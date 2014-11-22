package com.sony.ebs.octopus3.microservices.flix.service

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import com.sony.ebs.octopus3.commons.ratpack.product.filtering.CategoryService
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
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
    DeltaUrlHelper deltaUrlHelper

    rx.Observable<ProductResult> createProductServiceResult(Oct3HttpResponse response, String inputUrn) {
        observe(execControl.blocking({
            def productResult = new ProductResult(
                    inputUrn: inputUrn,
                    inputUrl: repositoryFileServiceUrl.replace(":urn", inputUrn),
                    success: response.success,
                    statusCode: response.statusCode
            )
            def json = jsonSlurper.parse(response.bodyAsStream, EncodingUtil.CHARSET_STR)

            productResult.errors = json.errors
            productResult.eanCode = json.result?.eanCode
            if (response.success) {
                productResult.outputUrn = json.result?.outputUrn
                productResult.outputUrl = json.result?.outputUrl
            }
            productResult
        }))
    }

    private def createProductServiceUrl(RepoDelta delta, String inputUrn) {
        observe(execControl.blocking({
            def sku = new URNImpl(inputUrn).values.last()

            def initialUrl = productServiceUrl.replace(":publication", delta.publication).replace(":locale", delta.locale).replace(":sku", sku)
            def urlBuilder = new URIBuilder(initialUrl)
            if (delta.processId?.id) {
                urlBuilder.addParameter("processId", delta.processId?.id)
            }
            urlBuilder.toString()
        }))
    }

    rx.Observable<ProductResult> doProduct(RepoDelta delta, String inputUrn) {
        rx.Observable.just("starting").flatMap({
            createProductServiceUrl(delta, inputUrn)
        }).flatMap({
            httpClient.doGet(it)
        }).flatMap({ Oct3HttpResponse response ->
            createProductServiceResult(response, inputUrn)
        }).onErrorReturn({
            log.error "error for $inputUrn", it
            def error = HandlerUtil.getErrorMessage(it)
            new ProductResult(
                    success: false,
                    inputUrn: inputUrn,
                    inputUrl: repositoryFileServiceUrl.replace(":urn", inputUrn),
                    errors: [error]
            )
        })
    }

    rx.Observable<ProductResult> processDelta(RepoDelta delta, Flix flix) {

        List categoryFilteredUrns
        def lastModifiedUrn = delta.lastModifiedUrn
        rx.Observable.just("starting").flatMap({
            deltaUrlHelper.createStartDate(delta.sdate, lastModifiedUrn)
        }).flatMap({
            delta.finalStartDate = it
            def deltaUrn = delta.getUrnForType(RepoValue.global_sku)
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
            List list = categoryFilteredUrns?.collect { doProduct(delta, it) }
            rx.Observable.merge(list, 30)
        })
    }

}

