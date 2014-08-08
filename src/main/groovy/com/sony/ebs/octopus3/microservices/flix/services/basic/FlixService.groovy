package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
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

    private rx.Observable<String> singleSheet(Flix flix, String sheetUrn) {

        def importUrl = flixSheetServiceUrl.replace(":urn", sheetUrn) + "?processId=${flix?.processId?.id}"

        rx.Observable.just("starting").flatMap({
            httpClient.doGet(importUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).map({
            "success for $sheetUrn"
        }).onErrorReturn({
            log.error "error for $importUrl", it
            "error for $sheetUrn"
        })
    }

    rx.Observable<String> flixFlow(Flix flix) {

        List deltaProductUrns
        rx.Observable.just("starting").flatMap({
            deltaDatesProvider.createDateParams(flix)
        }).flatMap({
            def deltaUrl = repositoryDeltaServiceUrl.replace(":urn", flix.deltaUrn.toString()) + it
            log.info "deltaUrl for $flix is $deltaUrl"
            httpClient.doGet(deltaUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                log.info "parsing delta json"
                new JsonSlurper().parseText(response.responseBody)
            }))
        }).flatMap({
            deltaProductUrns = it?.results
            log.info "${deltaProductUrns?.size()} products found in delta"

            log.info "deleting current flix xmls"
            def deleteUrl = repositoryFileServiceUrl.replace(":urn", flix.baseUrn.toString())
            httpClient.doDelete(deleteUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).flatMap({
            deltaDatesProvider.updateLastModified(flix)
        }).flatMap({
            categoryService.retrieveCategoryFeed(flix)
        }).flatMap({ String categoryFeed ->
            categoryService.filterForCategory(deltaProductUrns, categoryFeed)
        }).flatMap({ List filteredProductUrns ->
            log.info "${filteredProductUrns?.size()} calls will be made to flix sheet"
            List list = filteredProductUrns?.collect { singleSheet(flix, it) }
            rx.Observable.merge(list, 30)
        })
    }

}

