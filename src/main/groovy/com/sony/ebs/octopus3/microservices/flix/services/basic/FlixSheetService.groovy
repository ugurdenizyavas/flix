package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.enhancer.EanCodeEnhancer
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
    @Qualifier("eanCodeEnhancer")
    EanCodeEnhancer eanCodeEnhancer

    @Autowired
    FlixXmlBuilder flixXmlBuilder

    rx.Observable<String> sheetFlow(FlixSheet flixSheet) {
        Map product = [sku: flixSheet.urn.values.last()]
        rx.Observable.just("starting").flatMap({
            eanCodeEnhancer.enhance(product)
        }).filter({
            if (!product.eanCode) {
                flixSheet.errors << "ean code does not exist"
            }
            product.eanCode as boolean
        }).flatMap({
            log.info "getting global sku"
            def readUrl = repositoryFileServiceUrl.replace(":urn", flixSheet.urnStr)
            httpClient.doGet(readUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting sheet from repo", flixSheet.errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking {
                log.info "parsing json"
                def json = jsonSlurper.parse(response.responseBodyAsStream, "UTF-8")
                json.eanCode = product.eanCode
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
            def saveUrl = repositoryFileServiceUrl.replace(":urn", flixSheet.xmlUrn.toString())
            httpClient.doPost(saveUrl, IOUtils.toInputStream(xml, "UTF-8"))
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving flix xml to repo", flixSheet.errors)
        }).map({
            log.debug "save xml result is $it"
            "success"
        })
    }

}

