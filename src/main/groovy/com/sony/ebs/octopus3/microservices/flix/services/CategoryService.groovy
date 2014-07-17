package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
@Slf4j
@org.springframework.context.annotation.Lazy
class CategoryService {

    @Value('${octopus3.flix.categoryUrl}')
    String categoryServiceUrl

    @Value('${octopus3.flix.repositoryFileUrl}')
    String repositoryFileUrl

    @Autowired
    @Qualifier("proxyHttpClient")
    NingHttpClient proxyHttpClient

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient localHttpClient

    rx.Observable<String> doCategoryFeed(Flix flix) {
        def categoryReadUrl = categoryServiceUrl.replace(":publication", flix.publication).replace(":locale", flix.locale)
        log.info "category service url for $flix is $categoryReadUrl"
        proxyHttpClient.doGet(categoryReadUrl).flatMap({ categoryResult ->
            def categoryUrnStr = flix.categoryUrn.toString()
            def categorySaveUrl = repositoryFileUrl.replace(":urn", categoryUrnStr)

            log.info "category save url for $flix is $categorySaveUrl"
            localHttpClient.doPost(categorySaveUrl, categoryResult).flatMap({
                rx.Observable.from("success for $categoryUrnStr")
            })
        })
    }
}
