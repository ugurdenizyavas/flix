package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
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
        def categoryUrnStr = flix.categoryUrn.toString()

        String categoryFeed
        rx.Observable.just("starting").flatMap({
            def categoryReadUrl = octopusCategoryServiceUrl.replace(":publication", flix.publication).replace(":locale", flix.locale)
            log.info "category service url for $flix is $categoryReadUrl"
            httpClient.doGet(categoryReadUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting octopus category feed", flix.errors)
        }).flatMap({ Response response ->
            categoryFeed = IOUtils.toString(response.responseBodyAsStream, "UTF-8")
            def categorySaveUrl = repositoryFileServiceUrl.replace(":urn", categoryUrnStr)
            log.info "category save url for $flix is $categorySaveUrl"

            httpClient.doPost(categorySaveUrl, IOUtils.toInputStream(categoryFeed, "UTF-8"))
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "saving octopus category feed", flix.errors)
        }).map({
            categoryFeed
        })
    }


    rx.Observable<List> filterForCategory(Flix flix, String categoryFeed) {
        observe(execControl.blocking {
            log.info "starting category filtering"
            def categoryXml = xmlSlurper.parseText(categoryFeed)

            List productsInCategoryTree = categoryXml.depthFirst().findAll({ it.name() == 'product' }).collect({
                it.text()?.toLowerCase()
            })
            def filteredProductUrns = flix.deltaUrns.findAll { urnStr ->
                def sku = new URNImpl(urnStr).values?.last()
                productsInCategoryTree.contains(sku)
            }
            log.info "finished category filtering: ${filteredProductUrns.size()} left, from ${flix.deltaUrns?.size()}"
            flix.categoryFilteredOutUrns = flix.deltaUrns - filteredProductUrns
            filteredProductUrns
        })
    }

}
