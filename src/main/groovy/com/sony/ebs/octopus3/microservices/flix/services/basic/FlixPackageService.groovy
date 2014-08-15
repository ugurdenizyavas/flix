package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixPackage
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
class FlixPackageService {

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.flix.repositoryOpsServiceUrl}')
    String repositoryOpsServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    String createOpsRecipe(FlixPackage flixPackage) {
        def packageUrnStr = flixPackage.baseUrn.toString()

        def getZip = {
            it.zip {
                source packageUrnStr
            }
        }
        def getCopy = {
            it.copy {
                source "${packageUrnStr}.zip"
                destination flixPackage.destinationUrn.toString()
            }
        }
        def getDelete = {
            it.delete {
                source "${packageUrnStr}.zip"
            }
        }

        def builder = new groovy.json.JsonBuilder()
        builder {
            ops getZip(builder), getCopy(builder), getDelete(builder)
        }

        def result = builder.toString()
        log.info "recipe for $flixPackage is $result "
        result
    }

    rx.Observable<String> packageFlow(FlixPackage flixPackage) {
        log.info "creating package"
        rx.Observable.just("starting").flatMap({
            observe(execControl.blocking {
                createOpsRecipe(flixPackage)
            })
        }).flatMap({ String recipe ->
            httpClient.doPost(repositoryOpsServiceUrl, IOUtils.toInputStream(recipe, "UTF-8"))
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "calling repo ops service to package flix")
        }).map({
            "success for $flixPackage"
        })
    }

}
