package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import com.sony.ebs.octopus3.microservices.flix.services.sub.EanCodeProvider
import com.sony.ebs.octopus3.microservices.flix.services.sub.FlixXmlBuilder
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
class FlixSheetService {

    @Value('${octopus3.flix.repositoryFileUrl}')
    String repositoryFileUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Autowired
    EanCodeProvider eanCodeProvider

    @Autowired
    FlixXmlBuilder flixXmlBuilder

    rx.Observable<String> sheetFlow(FlixSheet flixSheet) {
        def eanCode
        rx.Observable.from("starting").flatMap({
            eanCodeProvider.getEanCode(flixSheet.urn)
        }).flatMap({
            eanCode = it
            log.info "reading json"
            def readUrl = repositoryFileUrl.replace(":urn", flixSheet.urnStr)
            httpClient.doGet(readUrl)
        }).flatMap({ String feed ->
            observe(execControl.blocking {
                log.info "parsing json"
                def json = new JsonSlurper().parseText(feed)
                json.eanCode = eanCode ?: ""
                json
            })
        }).flatMap({ json ->
            observe(execControl.blocking {
                log.debug "json is $json"
                log.info "building xml"
                flixXmlBuilder.buildXml(json)
            })
        }).flatMap({ String xml ->
            log.debug "xml is $xml"
            log.info "saving xml"
            def saveUrl = repositoryFileUrl.replace(":urn", flixSheet.sheetUrn.toString())
            httpClient.doPost(saveUrl, xml)
        }).map({
            log.debug "save xml result is $it"
            "success for $flixSheet"
        })
    }

}

