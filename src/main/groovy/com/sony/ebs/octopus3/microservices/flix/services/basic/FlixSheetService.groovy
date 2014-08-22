package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import com.sony.ebs.octopus3.microservices.flix.services.sub.FlixXmlBuilder
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class FlixSheetService {

    final JsonSlurper jsonSlurper = new JsonSlurper()

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Autowired
    FlixXmlBuilder flixXmlBuilder

    def createSheetJson(InputStream inputStream, String eanCode) {
        log.debug "starting parsing json"
        def json = jsonSlurper.parse(inputStream, "UTF-8")
        json.eanCode = eanCode
        log.debug "finished parsing json"
        json
    }

    rx.Observable<String> sheetFlow(FlixSheet flixSheet) {
        rx.Observable.just("starting").flatMap({
            def readUrl = repositoryFileServiceUrl.replace(":urn", flixSheet.urnStr)
            httpClient.doGet(readUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting sheet from repo", flixSheet.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking {
                createSheetJson(response.responseBodyAsStream, flixSheet.eanCode)
            })
        }).flatMap({ json ->
            observe(execControl.blocking {
                flixXmlBuilder.buildXml(json)
            })
        }).flatMap({ String xml ->
            def saveUrl = repositoryFileServiceUrl.replace(":urn", flixSheet.xmlUrn.toString())
            httpClient.doPost(saveUrl, IOUtils.toInputStream(xml, "UTF-8"))
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving flix xml to repo", flixSheet.errors)
        }).map({
            "success"
        })
    }

}

