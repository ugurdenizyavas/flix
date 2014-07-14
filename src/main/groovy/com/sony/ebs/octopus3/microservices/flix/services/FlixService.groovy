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

    private rx.Observable<String> singleSheet(Flix flix, String urn) {
        def importUrl = "$sheetUrl/$urn?processId=$flix.processId.id"
        httpClient.getLocal(importUrl).flatMap({
            log.info "finished $importUrl"
            rx.Observable.from("success for $urn")
        }).onErrorReturn({
            log.error "error in $urn", it
            "error in $urn"
        })
    }

    rx.Observable<String> flixFlow(Flix flix) {
        observe(execControl.blocking {
            log.info "$flix started"
            "$flix started"
        }).flatMap({
            def urn = new URNImpl("flix", [flix.publication, flix.locale])
            def url = "$repositoryDeltaUrl/${urn.toString()}?sdate=$flix.sdate&edate=$flix.edate"
            httpClient.getLocal(url)
        }).flatMap({ deltaResult ->
            def json = new JsonSlurper().parseText(deltaResult)
            rx.Observable.zip(
                    json?.urns?.collect { String urn ->
                        singleSheet(flix, urn)
                    }
            ) { sheetResult ->
                log.info "import finished with result $sheetResult"
                "$sheetResult"
            }
        })
    }

}

