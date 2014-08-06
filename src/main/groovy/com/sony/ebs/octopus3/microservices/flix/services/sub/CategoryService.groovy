package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.util.logging.Slf4j
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
        def categoryUrnStr = flix.categoryUrn.toString()

        String categoryFeed
        rx.Observable.from("starting").flatMap({
            def categoryReadUrl = octopusCategoryServiceUrl.replace(":publication", flix.publication).replace(":locale", flix.locale)
            log.info "category service url for $flix is $categoryReadUrl"
            httpClient.doGet(categoryReadUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).flatMap({ Response response ->
            categoryFeed = response.responseBody
            def categorySaveUrl = repositoryFileServiceUrl.replace(":urn", categoryUrnStr)
            log.info "category save url for $flix is $categorySaveUrl"

            httpClient.doPost(categorySaveUrl, categoryFeed)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).map({
            categoryFeed
        })
    }


    rx.Observable<List> filterForCategory(List productUrns, String categoryFeed) {
        observe(execControl.blocking {
            def categoryXml = new XmlSlurper().parseText(categoryFeed)
            def filteredProductUrns = productUrns.findAll { urnStr ->
                def sku = new URNImpl(urnStr).values?.last()
                categoryXml.depthFirst().findAll { it.name() == 'product' && sku.equalsIgnoreCase(it.text()) }
            }
            log.info "category filtering: ${filteredProductUrns.size()} left, from ${productUrns.size()}"
            filteredProductUrns
        })
    }

}
