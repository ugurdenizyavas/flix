package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.encoding.MaterialNameEncoder
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixUtils
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
class CategoryService {

    final XmlSlurper xmlSlurper = new XmlSlurper()

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.flix.octopusCategoryServiceUrl}')
    String octopusCategoryServiceUrl

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    @Qualifier("internalHttpClient")
    Oct3HttpClient httpClient

    rx.Observable<String> retrieveCategoryFeed(RepoDelta delta) {
        String categoryFeed
        rx.Observable.just("starting").flatMap({
            def categoryReadUrl = octopusCategoryServiceUrl.replace(":publication", delta.publication).replace(":locale", delta.locale)
            log.info "category service url for {} is {}", delta, categoryReadUrl
            httpClient.doGet(categoryReadUrl)
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("getting octopus category feed", delta.errors)
        }).flatMap({ Oct3HttpResponse response ->
            categoryFeed = IOUtils.toString(response.bodyAsStream, EncodingUtil.CHARSET)
            def categoryUrn = FlixUtils.getCategoryUrn(delta.publication, delta.locale)
            def categorySaveUrl = repositoryFileServiceUrl.replace(":urn", categoryUrn.toString())
            log.info "category save url for {} is {}", delta, categorySaveUrl

            httpClient.doPost(categorySaveUrl, IOUtils.toInputStream(categoryFeed, EncodingUtil.CHARSET))
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("saving octopus category feed", delta.errors)
        }).map({
            categoryFeed
        })
    }


    rx.Observable<List> filterForCategory(List productUrls, String categoryFeed) {
        observe(execControl.blocking {
            log.info "starting category filtering"
            def categoryXml = xmlSlurper.parseText(categoryFeed)

            List productsInCategoryTree = categoryXml.depthFirst().findAll({ it.name() == 'product' }).collect({
                it.text()?.toUpperCase(MaterialNameEncoder.LOCALE)
            })
            def filtered = productUrls.findAll { urnStr ->
                def sku = new URNImpl(urnStr).values?.last()
                sku = MaterialNameEncoder.decode(sku)
                productsInCategoryTree.contains(sku)
            }

            log.info "finished category filtering: {} left, from {}", filtered.size(), productUrls?.size()
            filtered
        })
    }

}
