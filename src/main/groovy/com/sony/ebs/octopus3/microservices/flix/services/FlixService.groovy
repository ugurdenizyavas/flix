package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
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
    NingHttpClient httpClient

    @Autowired
    @Lazy
    ExecControl execControl

    private rx.Observable<String> singleSheet(Flix flix, String sheetUrn) {
        log.info "importing sheet"
        def importUrl = "$sheetUrl/$sheetUrn?processId=$flix.processId.id"
        httpClient.getLocal(importUrl).flatMap({
            log.info "finished $importUrl"
            rx.Observable.from("success for $sheetUrn")
        }).onErrorReturn({
            log.error "error in $sheetUrn", it
            "error in $sheetUrn"
        })
    }

    rx.Observable<String> flixFlow(Flix flix) {
        log.info "reading delta"
        def deltaUrn = new URNImpl("flix", [flix.publication, flix.locale])
        def deltaUrl = "$repositoryDeltaUrl/${deltaUrn.toString()}?sdate=$flix.sdate&edate=$flix.edate"
        httpClient.getLocal(deltaUrl)
                .flatMap({ deltaResult ->
            observe(execControl.blocking {
                log.info "parsing delta json"
                new JsonSlurper().parseText(deltaResult)
            })
        }).flatMap({ jsonResult ->
            rx.Observable.zip(
                    jsonResult?.urns?.collect { String sheetUrn ->
                        singleSheet(flix, sheetUrn)
                    }
            ) { sheetResult ->
                log.info "finished sheet calls with $sheetResult"
                "$sheetResult"
            }
        })
    }

}

