package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoProduct
import com.sony.ebs.octopus3.commons.ratpack.product.enhancer.EanCodeEnhancer
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class ProductService {

    final JsonSlurper jsonSlurper = new JsonSlurper()

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    @Qualifier("internalHttpClient")
    Oct3HttpClient httpClient

    @Autowired
    EanCodeEnhancer eanCodeEnhancer

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Autowired
    FlixXmlBuilder flixXmlBuilder

    def createSheetJson(InputStream inputStream, String eanCode) {
        log.debug "starting parsing json"
        def json = jsonSlurper.parse(inputStream, EncodingUtil.CHARSET_STR)
        json.eanCode = eanCode
        log.debug "finished parsing json"
        json
    }

    def createRepoUrl(RepoProduct product, boolean xml) {
        observe(execControl.blocking({
            def urn
            if (xml) {
                urn = FlixUtils.getXmlUrn(product.urn.toString())
            } else {
                urn = new URNImpl(DeltaType.global_sku.toString(), [product.publication, product.locale, product.sku])
            }

            def initialUrl = repositoryFileServiceUrl.replace(":urn", urn.toString())

            def uriBuilder = new URIBuilder(initialUrl)
            if (product.processId) {
                uriBuilder.addParameter("processId", product.processId)
            }
            uriBuilder.toString()
        }))
    }

    rx.Observable<String> processProduct(RepoProduct product) {
        String xmlString
        rx.Observable.just("starting").flatMap({
            if (!product.eanCode) {
                eanCodeEnhancer.enhance([materialName: MaterialNameEncoder.decode(product.sku)])
            } else {
                rx.Observable.just([:])
            }
        }).filter({ Map map ->
            if (!product.eanCode) {
                if (map.eanCode) {
                    product.eanCode = map.eanCode
                } else {
                    product.errors << "ean code not found"
                }
            }
            product.eanCode as boolean
        }).flatMap({
            log.info "getting json from repo for {}", product.sku
            createRepoUrl(product, false)
        }).flatMap({
            httpClient.doGet(it)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("getting sheet from repo", product.errors)
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking {
                createSheetJson(response.bodyAsStream, product.eanCode)
            })
        }).flatMap({ json ->
            observe(execControl.blocking {
                flixXmlBuilder.buildXml(json)
            })
        }).flatMap({
            xmlString = it
            log.info "saving xml to repo for {}", product.sku
            createRepoUrl(product, true)
        }).flatMap({
            httpClient.doPost(it, IOUtils.toInputStream(xmlString, EncodingUtil.CHARSET))
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("saving flix xml to repo", product.errors)
        }).map({
            "success"
        })
    }

}

