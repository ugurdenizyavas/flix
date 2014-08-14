package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheetServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.sub.CategoryService
import com.sony.ebs.octopus3.microservices.flix.services.dates.DeltaDatesProvider
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

    @Value('${octopus3.flix.flixSheetServiceUrl}')
    String flixSheetServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Autowired
    CategoryService categoryService

    @Autowired
    DeltaDatesProvider deltaDatesProvider

    private rx.Observable<FlixSheetServiceResult> singleSheet(Flix flix, String sheetUrn) {

        def importUrl = flixSheetServiceUrl.replace(":urn", sheetUrn) + "?processId=${flix?.processId?.id}"

        rx.Observable.just("starting").flatMap({
            httpClient.doGet(importUrl)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                boolean success = NingHttpClient.isSuccess(response)
                def sheetServiceResult = new FlixSheetServiceResult(urn: sheetUrn, success: success, statusCode: response.statusCode)
                if (!success) {
                    def json = jsonSlurper.parse(response.responseBodyAsStream)
                    sheetServiceResult.errors = json.errors
                }
                sheetServiceResult
            }))
        }).onErrorReturn({
            log.error "error for $sheetUrn", it
            def error = it.message ?: it.cause?.message
            new FlixSheetServiceResult(urn: sheetUrn, success: false, errors: [error])
        })
    }

    rx.Observable flixFlow(Flix flix) {

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
                jsonSlurper.parse(response.responseBodyAsStream)
            }))
        }).flatMap({
            flix.deltaUrns = it?.results
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
            categoryService.filterForCategory(flix, categoryFeed)
        }).flatMap({ List filteredProductUrns ->
            log.info "${filteredProductUrns?.size()} calls will be made to flix sheet"
            List list = filteredProductUrns?.collect { singleSheet(flix, it) }
            rx.Observable.merge(list, 30)
        })
    }

}

