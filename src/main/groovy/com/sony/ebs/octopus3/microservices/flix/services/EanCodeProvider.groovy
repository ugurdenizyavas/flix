package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.flix.http.NingHttpClient
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Component
@Slf4j
@org.springframework.context.annotation.Lazy
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
        def url = "$serviceUrl/${urn.values.last()}"
        httpClient.doGet(url).flatMap({ result ->
            observe(execControl.blocking {
                log.info "parsing eanCode xml"
                def xml = new XmlSlurper().parseText(result)
                xml.eancode?.@code?.toString()
            })
        })
    }
}
