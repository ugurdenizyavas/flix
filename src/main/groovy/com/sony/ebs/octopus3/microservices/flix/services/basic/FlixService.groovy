package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheetServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.sub.CategoryService
import com.sony.ebs.octopus3.microservices.flix.services.dates.DeltaDatesProvider
import com.sony.ebs.octopus3.microservices.flix.services.sub.EanCodeService
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class FlixService {

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

    @Value('${octopus3.flix.flixSheetServiceUrl}')
    String flixSheetServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Autowired
    CategoryService categoryService

    @Autowired
    EanCodeService eanCodeService

    @Autowired
    DeltaDatesProvider deltaDatesProvider

    private rx.Observable<FlixSheetServiceResult> singleSheet(Flix flix, String jsonUrn, String eanCode) {

        def importUrl = flixSheetServiceUrl.replace(":urn", jsonUrn) + "?eanCode=$eanCode"
        if (flix?.processId?.id) {
            importUrl += "&processId=${flix?.processId?.id}"
        }

        rx.Observable.just("starting").flatMap({
            httpClient.doGet(importUrl)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                boolean success = NingHttpClient.isSuccess(response)
                def sheetServiceResult = new FlixSheetServiceResult(jsonUrn: jsonUrn, success: success,
                        statusCode: response.statusCode, eanCode: eanCode)
                if (!success) {
                    def json = jsonSlurper.parse(response.responseBodyAsStream, "UTF-8")
                    sheetServiceResult.errors = json.errors
                } else {
                    sheetServiceResult.with {
                        def xmlUrn = new FlixSheet(urnStr: jsonUrn).xmlUrn.toString()
                        xmlFileUrl = repositoryFileServiceUrl.replace(":urn", xmlUrn)
                        xmlFileAttributesUrl = repositoryFileAttributesServiceUrl.replace(":urn", xmlUrn)
                    }
                }
                sheetServiceResult
            }))
        }).onErrorReturn({
            log.error "error for $jsonUrn", it
            def error = it.message ?: it.cause?.message
            new FlixSheetServiceResult(jsonUrn: jsonUrn, success: false, errors: [error], eanCode: eanCode)
        })
    }

    rx.Observable flixFlow(Flix flix) {

        List categoryFilteredUrns
        rx.Observable.just("starting").flatMap({
            deltaDatesProvider.createDateParams(flix)
        }).flatMap({
            def deltaUrl = repositoryDeltaServiceUrl.replace(":urn", flix.deltaUrn.toString()) + it
            log.info "deltaUrl for $flix is $deltaUrl"
            httpClient.doGet(deltaUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "retrieving delta from repo service", flix.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                log.info "parsing delta json"
                def json = jsonSlurper.parse(response.responseBodyAsStream, "UTF-8")
                json?.results
            }))
        }).flatMap({
            flix.deltaUrns = it
            log.info "${flix.deltaUrns?.size()} products found in delta"

            log.info "deleting current flix xmls"
            def deleteUrl = repositoryFileServiceUrl.replace(":urn", flix.baseUrn.toString())
            httpClient.doDelete(deleteUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "deleting current flix xmls", flix.errors)
        }).flatMap({
            deltaDatesProvider.updateLastModified(flix)
        }).flatMap({
            categoryService.retrieveCategoryFeed(flix)
        }).flatMap({ String categoryFeed ->
            categoryService.filterForCategory(flix.deltaUrns, categoryFeed)
        }).flatMap({
            categoryFilteredUrns = it
            flix.categoryFilteredOutUrns = flix.deltaUrns - categoryFilteredUrns
            eanCodeService.filterForEanCodes(categoryFilteredUrns, flix.errors)
        }).flatMap({ Map eanCodeMap ->
            List eanCodeFilteredUrns = eanCodeMap.keySet() as List
            flix.eanCodeFilteredOutUrns = categoryFilteredUrns - eanCodeFilteredUrns
            List list = eanCodeMap?.collect { singleSheet(flix, it.key, it.value) }
            rx.Observable.merge(list, 30)
        })
    }

}

