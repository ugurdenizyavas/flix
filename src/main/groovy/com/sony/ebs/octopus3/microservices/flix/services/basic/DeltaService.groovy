package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import com.sony.ebs.octopus3.commons.ratpack.product.filtering.CategoryService
import com.sony.ebs.octopus3.commons.ratpack.product.filtering.EanCodeService
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.ProductServiceResult
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class DeltaService {

    final JsonSlurper jsonSlurper = new JsonSlurper()

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.flix.repositoryDeltaServiceUrl}')
    String repositoryDeltaServiceUrl

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Value('${octopus3.flix.repositoryFileAttributesServiceUrl}')
    String repositoryFileAttributesServiceUrl

    @Value('${octopus3.flix.productServiceUrl}')
    String productServiceUrl

    @Autowired
    @Qualifier("internalHttpClient")
    Oct3HttpClient httpClient

    @Autowired
    CategoryService categoryService

    @Autowired
    EanCodeService eanCodeService

    @Autowired
    DeltaUrlHelper deltaUrlHelper

    private def createProductServiceResult(Oct3HttpResponse response, String jsonUrn, String eanCode) {
        observe(execControl.blocking({
            def sheetServiceResult = new ProductServiceResult(jsonUrn: jsonUrn, success: response.success,
                    statusCode: response.statusCode, eanCode: eanCode)
            if (!response.success) {
                def json = jsonSlurper.parse(response.bodyAsStream, EncodingUtil.CHARSET_STR)
                sheetServiceResult.errors = json.errors
            } else {
                sheetServiceResult.with {
                    def xmlUrnStr = FlixUtils.getXmlUrn(jsonUrn)?.toString()
                    xmlFileUrl = repositoryFileServiceUrl.replace(":urn", xmlUrnStr)
                    xmlFileAttributesUrl = repositoryFileAttributesServiceUrl.replace(":urn", xmlUrnStr)
                }
            }
            sheetServiceResult
        }))
    }

    private def createProductServiceUrl(RepoDelta delta, String jsonUrn, String eanCode) {
        observe(execControl.blocking({
            def sku = new URNImpl(jsonUrn).values.last()

            def initialUrl = productServiceUrl.replace(":publication", delta.publication).replace(":locale", delta.locale).replace(":sku", sku)
            def urlBuilder = new URIBuilder(initialUrl)
            if (delta.processId?.id) {
                urlBuilder.addParameter("processId", delta.processId?.id)
            }
            if (eanCode) {
                urlBuilder.addParameter("eanCode", eanCode)
            }
            urlBuilder.toString()
        }))
    }

    private rx.Observable<ProductServiceResult> doProduct(RepoDelta delta, String jsonUrn, String eanCode) {
        rx.Observable.just("starting").flatMap({
            createProductServiceUrl(delta, jsonUrn, eanCode)
        }).flatMap({
            httpClient.doGet(it)
        }).flatMap({ Oct3HttpResponse response ->
            createProductServiceResult(response, jsonUrn, eanCode)
        }).onErrorReturn({
            log.error "error for $jsonUrn", it
            def error = HandlerUtil.getErrorMessage(it)
            new ProductServiceResult(jsonUrn: jsonUrn, success: false, errors: [error], eanCode: eanCode)
        })
    }

    rx.Observable<ProductServiceResult> processDelta(RepoDelta delta, Flix flix) {

        List categoryFilteredUrns
        def lastModifiedUrn = delta.lastModifiedUrn
        rx.Observable.just("starting").flatMap({
            deltaUrlHelper.createStartDate(delta.sdate, lastModifiedUrn)
        }).flatMap({
            delta.finalStartDate = it
            def deltaUrn = delta.getUrnForType(DeltaType.global_sku)
            def initialUrl = repositoryDeltaServiceUrl.replace(":urn", deltaUrn.toString())
            deltaUrlHelper.createRepoDeltaUrl(initialUrl, delta.finalStartDate, delta.edate)
        }).flatMap({
            delta.finalDeltaUrl = it
            log.debug "delta url is {} for {}", delta.finalDeltaUrl, delta
            httpClient.doGet(delta.finalDeltaUrl)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("retrieving global sku delta", delta.errors)
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking({
                log.info "parsing delta json"
                def json = jsonSlurper.parse(response.bodyAsStream, EncodingUtil.CHARSET_STR)
                json?.results
            }))
        }).flatMap({
            delta.deltaUrns = it
            log.info "{} products found in delta", delta.deltaUrns?.size()

            log.info "deleting current flix xmls"
            def deleteUrl = repositoryFileServiceUrl.replace(":urn", delta.baseUrn.toString())
            httpClient.doDelete(deleteUrl)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("deleting current flix xmls", delta.errors)
        }).flatMap({
            deltaUrlHelper.updateLastModified(lastModifiedUrn, delta.errors)
        }).flatMap({
            categoryService.retrieveCategoryFeed(delta)
        }).flatMap({ String categoryFeed ->
            categoryService.filterForCategory(delta.deltaUrns, categoryFeed)
        }).flatMap({ Map categoryMap ->
            categoryFilteredUrns = categoryMap.keySet() as List
            flix.categoryFilteredOutUrns = delta.deltaUrns - categoryFilteredUrns
            eanCodeService.filterForEanCodes(categoryFilteredUrns, delta.errors)
        }).flatMap({ Map eanCodeMap ->
            List eanCodeFilteredUrns = eanCodeMap.keySet() as List
            flix.eanCodeFilteredOutUrns = categoryFilteredUrns - eanCodeFilteredUrns
            List list = eanCodeMap?.collect { doProduct(delta, it.key, it.value) }
            rx.Observable.merge(list, 30)
        })
    }

}

