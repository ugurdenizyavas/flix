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
class EanCodeService {

    final XmlSlurper xmlSlurper = new XmlSlurper()

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.flix.octopusEanCodeServiceUrl}')
    String octopusEanCodeServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    rx.Observable<String> filterForEanCodes(List productUrls, URN baseUrn, List errors) {
        def createUrn = {
            new URNImpl(baseUrn.type, baseUrn.values + it)
        }
        rx.Observable.just("starting").flatMap({
            httpClient.doGet(octopusEanCodeServiceUrl)
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "getting ean code feed", errors)
        }).flatMap({ Response response ->
            observe(execControl.blocking({
                def xml = xmlSlurper.parse(response.responseBodyAsStream)
                Map eanCodeMap = [:]
                xml.identifier?.each { identifier ->
                    def urnStr = createUrn(identifier.@materialName?.toString())?.toString()
                    if (productUrls.contains(urnStr)) {
                        eanCodeMap[urnStr] = identifier.text()
                    }
                }
                log.info "finished eanCode filtering: ${eanCodeMap?.size()} left, from ${productUrls?.size()}"
                "done"
                eanCodeMap
            }))
        })
    }

}
