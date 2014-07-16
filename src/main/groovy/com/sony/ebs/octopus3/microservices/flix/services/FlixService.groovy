package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.microservices.flix.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
class FlixService {

    @Value('${octopus3.flix.repositoryDeltaUrl}')
    String repositoryDeltaUrl

    @Value('${octopus3.flix.sheetUrl}')
    String sheetUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Autowired
    CategoryService categoryService

    @Autowired
    @Lazy
    ExecControl execControl

    @Autowired
    DateParamsProvider dateParamsProvider

    private rx.Observable<String> singleSheet(Flix flix, String sheetUrn) {
        log.info "importing sheet"
        def importUrl = "$sheetUrl/$sheetUrn?processId=$flix.processId.id"
        httpClient.doGet(importUrl).flatMap({
            log.info "finished $importUrl"
            rx.Observable.from("success for $sheetUrn")
        }).onErrorReturn({
            log.error "error in $sheetUrn", it
            "error in $sheetUrn"
        })
    }

    rx.Observable<String> flixFlow(Flix flix) {
        observe(execControl.blocking {
            repositoryDeltaUrl.replace(":urn", flix.deltaUrn.toString()) + dateParamsProvider.createDateParams(flix)
        }).flatMap({ deltaUrl ->
            log.info "deltaUrl for $flix is $deltaUrl"
            httpClient.doGet(deltaUrl)
        }).flatMap({ deltaResult ->
            observe(execControl.blocking {
                dateParamsProvider.updateLastModified(flix)

                log.info "parsing delta json"
                new JsonSlurper().parseText(deltaResult)
            })
        }).flatMap({ jsonResult ->
            List list = jsonResult?.results?.collect { String sheetUrn ->
                singleSheet(flix, sheetUrn)
            }
            list << categoryService.doCategoryFeed(flix)
            rx.Observable.merge(list)
        })
    }

}

