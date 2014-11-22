package com.sony.ebs.octopus3.microservices.flix.service

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
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

    ProductResult enhanceProductResult(RepoProduct product, ProductResult productResult) {
        productResult.with {
            inputUrn = product.getUrnForType(RepoValue.global_sku).toString()
            inputUrl = repositoryFileServiceUrl.replace(":urn", inputUrn)
            outputUrn = FlixUtils.getXmlUrn(product.urn.toString()).toString()
            outputUrl = repositoryFileServiceUrl.replace(":urn", outputUrn)
            it
        }
    }

    rx.Observable<String> processProduct(RepoProduct product, ProductResult productResult) {
        String xmlString
        rx.Observable.just("starting").flatMap({
            observe(execControl.blocking({
                enhanceProductResult(product, productResult)
            }))
        }).flatMap({
            eanCodeEnhancer.enhance([materialName: MaterialNameEncoder.decode(product.sku)])
        }).filter({ Map map ->
            if (map.eanCode) {
                productResult.eanCode = map.eanCode
            } else {
                productResult.errors << "ean code not found"
            }
            productResult.eanCode as boolean
        }).flatMap({
            observe(execControl.blocking({
                log.info "getting json from repo for {}", product.sku
                FlixUtils.addProcessId(product.processId, productResult.inputUrl)
            }))
        }).flatMap({
            httpClient.doGet(it)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("getting sheet from repo", productResult.errors)
        }).flatMap({ Oct3HttpResponse response ->
            observe(execControl.blocking {
                createSheetJson(response.bodyAsStream, productResult.eanCode)
            })
        }).flatMap({ json ->
            observe(execControl.blocking {
                flixXmlBuilder.buildXml(json)
            })
        }).flatMap({
            xmlString = it
            observe(execControl.blocking({
                log.info "saving xml to repo for {}", product.sku
                FlixUtils.addProcessId(product.processId, productResult.outputUrl)
            }))
        }).flatMap({
            httpClient.doPost(it, IOUtils.toInputStream(xmlString, EncodingUtil.CHARSET))
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("saving flix xml to repo", productResult.errors)
        }).map({
            "success"
        })
    }

}

