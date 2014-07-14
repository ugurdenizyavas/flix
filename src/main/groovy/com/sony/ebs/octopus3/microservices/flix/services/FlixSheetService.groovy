package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.microservices.flix.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import groovy.xml.MarkupBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
class FlixSheetService {

    @Value('${octopus3.flix.repositoryFileUrl}')
    String repositoryFileUrl

    @Autowired
    NingHttpClient httpClient

    @Autowired
    @Lazy
    ExecControl execControl

    rx.Observable<String> importSheet(FlixSheet flixSheet) {
        log.info "reading json"
        def readUrl = "$repositoryFileUrl/$flixSheet.urnStr"
        httpClient.getLocal(readUrl)
                .flatMap({ String readResult ->
            observe(execControl.blocking {
                log.info "creating xml from json"
                def json = new JsonSlurper().parseText(readResult)
                def writer = new StringWriter()
                def xml = new MarkupBuilder(writer)
                xml.content {
                    c json
                }
                writer.toString()
            })
        }).flatMap({ String xmlResult ->
            log.info "saving xml"
            def saveUrl = "$repositoryFileUrl/${flixSheet.xmlUrn.toString()}"
            httpClient.postLocal(saveUrl, xmlResult)
        })
    }

}

