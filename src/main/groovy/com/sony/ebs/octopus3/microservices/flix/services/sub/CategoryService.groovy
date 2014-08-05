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
@org.springframework.context.annotation.Lazy
class CategoryService {

    @Value('${octopus3.flix.categoryUrl}')
    String categoryServiceUrl

    @Value('${octopus3.flix.repositoryFileUrl}')
    String repositoryFileUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    rx.Observable<String> retrieveCategoryFeed(Flix flix) {
        def categoryUrnStr = flix.categoryUrn.toString()

        rx.Observable.from("starting").flatMap({
            def categoryReadUrl = categoryServiceUrl.replace(":publication", flix.publication).replace(":locale", flix.locale)
            log.info "category service url for $flix is $categoryReadUrl"
            httpClient.doGetAsString(categoryReadUrl)
        }).flatMap({ categoryXml ->
            def categorySaveUrl = repositoryFileUrl.replace(":urn", categoryUrnStr)
            log.info "category save url for $flix is $categorySaveUrl"

            httpClient.doPost(categorySaveUrl, categoryXml)
        }).map({
            "success for $categoryUrnStr"
        })
    }
}
