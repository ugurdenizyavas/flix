package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@Service
class CategoryService {

    @Value('${octopus3.flix.categoryUrl}')
    String categoryServiceUrl

    @Value('${octopus3.flix.repositoryFileUrl}')
    String repositoryFileUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    rx.Observable<String> doCategoryFeed(Flix flix) {
        def categoryReadUrl = categoryServiceUrl.replace(":publication", flix.publication).replace(":locale", flix.locale)
        log.info "category service url for $flix is $categoryReadUrl"
        httpClient.doGet(categoryReadUrl).flatMap({ categoryResult ->
            def categoryUrnStr = flix.categoryUrn.toString()
            def categorySaveUrl = repositoryFileUrl.replace(":urn", categoryUrnStr)

            log.info "category save url for $flix is $categorySaveUrl"
            httpClient.doPost(categorySaveUrl, categoryResult).flatMap({
                rx.Observable.from("success for $categoryUrnStr")
            })
        }).onErrorReturn({ e ->
            log.error "error getting category for $flix", e
            null
        })
    }
}
