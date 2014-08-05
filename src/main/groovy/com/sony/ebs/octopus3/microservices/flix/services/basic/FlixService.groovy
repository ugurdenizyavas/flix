package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.services.sub.CategoryService
import com.sony.ebs.octopus3.microservices.flix.services.sub.DateParamsProvider
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

    @Value('${octopus3.flix.repositoryDeltaUrl}')
    String repositoryDeltaUrl

    @Value('${octopus3.flix.repositoryFileUrl}')
    String repositoryFileUrl

    @Value('${octopus3.flix.sheetUrl}')
    String sheetUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Autowired
    CategoryService categoryService

    @Autowired
    DateParamsProvider dateParamsProvider

    private rx.Observable<String> singleSheet(Flix flix, String sheetUrn) {
        log.info "importing sheet"
        def importUrl = "$sheetUrl/$sheetUrn?processId=${flix?.processId?.id}"

        rx.Observable.from("starting").flatMap({
            httpClient.doGet(importUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).map({
            log.info "finished $importUrl"
            "success for $sheetUrn"
        })
    }

    rx.Observable<String> flixFlow(Flix flix) {

        Map jsonResult
        rx.Observable.from("starting").flatMap({
            observe(execControl.blocking {
                dateParamsProvider.createDateParams(flix)
            })
        }).flatMap({
            def deltaUrl = repositoryDeltaUrl.replace(":urn", flix.deltaUrn.toString()) + it
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
            jsonResult = it
            def deleteUrl = repositoryFileUrl.replace(":urn", flix.baseUrn.toString())
            httpClient.doDelete(deleteUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).flatMap({
            observe(execControl.blocking {
                dateParamsProvider.updateLastModified(flix)
            })
        }).flatMap({
            log.info "${jsonResult?.results?.size()} products in delta for $flix"
            List list = jsonResult?.results?.collect { String sheetUrn ->
                singleSheet(flix, sheetUrn)
            }
            list << categoryService.retrieveCategoryFeed(flix)
            rx.Observable.merge(list)
        })
    }

}

