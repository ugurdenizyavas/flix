package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
class EanCodeProvider {

    @Value('${octopus3.flix.eanCodeUrl}')
    String serviceUrl

    @Autowired
    @Qualifier("proxyHttpClient")
    NingHttpClient httpClient

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    rx.Observable<String> getEanCode(URN urn) {
        def url = serviceUrl.replace(":product", urn.values.last())
        log.info "ean code service url for $urn is $url"
        httpClient.doGet(url).flatMap({ result ->
            observe(execControl.blocking {
                log.info "parsing eanCode xml"
                def xml = new XmlSlurper().parseText(result)
                xml.eancode?.@code?.toString()
            })
        }).onErrorReturn({ e ->
            log.error "error getting ean code for $urn", e
            null
        })
    }
}
