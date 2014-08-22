package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
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
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    rx.Observable<String> retrieveCategoryFeed(Flix flix) {
        String categoryFeed
        rx.Observable.just("starting").flatMap({
            def categoryReadUrl = octopusCategoryServiceUrl.replace(":publication", flix.publication).replace(":locale", flix.locale)
            log.info "category service url for {} is {}", flix, categoryReadUrl
            httpClient.doGet(categoryReadUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting octopus category feed", flix.errors)
        }).flatMap({ Response response ->
            categoryFeed = IOUtils.toString(response.responseBodyAsStream, "UTF-8")
            def categorySaveUrl = repositoryFileServiceUrl.replace(":urn", flix.categoryUrn.toString())
            log.info "category save url for {} is {}", flix, categorySaveUrl

            httpClient.doPost(categorySaveUrl, IOUtils.toInputStream(categoryFeed, "UTF-8"))
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving octopus category feed", flix.errors)
        }).map({
            categoryFeed
        })
    }


    rx.Observable<List> filterForCategory(List productUrls, String categoryFeed) {
        observe(execControl.blocking {
            log.info "starting category filtering"
            def categoryXml = xmlSlurper.parseText(categoryFeed)

            List productsInCategoryTree = categoryXml.depthFirst().findAll({ it.name() == 'product' }).collect({
                it.text()?.toLowerCase()
            })
            def filtered = productUrls.findAll { urnStr ->
                def sku = new URNImpl(urnStr).values?.last()
                productsInCategoryTree.contains(sku)
            }

            log.info "finished category filtering: {} left, from {}", filtered.size(), productUrls?.size()
            filtered
        })
    }

}
