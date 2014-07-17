package com.sony.ebs.octopus3.microservices.flix.services

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

    def parseJson(readResult) {
        observe(execControl.blocking {
            log.info "parsing json"
            new JsonSlurper().parseText(readResult)
        })
    }

    def buildXml(jsonResult) {
        observe(execControl.blocking {
            log.info "building xml"
            flixXmlBuilder.buildXml(jsonResult)
        })
    }

    rx.Observable<String> importSheet(FlixSheet flixSheet) {
        log.info "reading json"
        def readUrl = repositoryFileUrl.replace(":urn", flixSheet.urnStr)
        httpClient.doGet(readUrl)
                .flatMap({ String readResult ->
            rx.Observable.zip(
                    parseJson(readResult),
                    eanCodeProvider.getEanCode(flixSheet.urn)
            ) { json, eanCode ->
                log.info "merging results"
                json.eanCode = eanCode
                json
            }
        }).flatMap({ jsonResult ->
            buildXml(jsonResult)
        }).flatMap({ String xmlResult ->
            log.info "saving xml"
            def saveUrl = repositoryFileUrl.replace(":urn", flixSheet.sheetUrn.toString())
            httpClient.doPost(saveUrl, xmlResult)
        }).flatMap({ String saveResult ->
            log.debug "save xml result: $saveResult"
            rx.Observable.from("success for $flixSheet")
        })
    }

}

