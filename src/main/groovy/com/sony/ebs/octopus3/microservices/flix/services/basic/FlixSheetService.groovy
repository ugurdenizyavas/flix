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
            log.debug "eancode is $eanCode"
            log.info "reading json"
            def readUrl = repositoryFileUrl.replace(":urn", flixSheet.urnStr)
            httpClient.doGet(readUrl)
        }).map({
            log.info "parsing json"
            def json = new JsonSlurper().parseText(it)
            json.eanCode = eanCode ?: ""
            json
        }).map({
            log.debug "json is $it"
            log.info "building xml"
            flixXmlBuilder.buildXml(it)
        }).flatMap({
            log.debug "xml is $it"
            log.info "saving xml"
            def saveUrl = repositoryFileUrl.replace(":urn", flixSheet.sheetUrn.toString())
            httpClient.doPost(saveUrl, it)
        }).map({
            log.debug "save xml result is $it"
            "success for $flixSheet"
        })
    }

}

