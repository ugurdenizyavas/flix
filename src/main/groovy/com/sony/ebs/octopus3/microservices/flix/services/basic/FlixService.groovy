package com.sony.ebs.octopus3.microservices.flix.services.basic

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
        def importUrl = "$sheetUrl/$sheetUrn?processId=$flix.processId.id"

        rx.Observable.from("starting").flatMap({
            httpClient.doGet(importUrl)
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
        }).flatMap({ String deltaFeed ->
            observe(execControl.blocking({
                log.info "parsing delta json"
                new JsonSlurper().parseText(deltaFeed)
            }))
        }).flatMap({
            jsonResult = it
            def deleteUrl = repositoryFileUrl.replace(":urn", flix.baseUrn.toString())
            httpClient.doDelete(deleteUrl)
        }).flatMap({
            observe(execControl.blocking {
                dateParamsProvider.updateLastModified(flix)
            })
        }).flatMap({
            List list = jsonResult?.results?.collect { String sheetUrn ->
                singleSheet(flix, sheetUrn)
            }
            list << categoryService.retrieveCategoryFeed(flix)
            rx.Observable.merge(list)
        })
    }

}

