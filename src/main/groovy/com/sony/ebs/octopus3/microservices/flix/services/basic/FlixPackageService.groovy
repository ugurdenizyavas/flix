package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixPackage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Slf4j
@Service
class FlixPackageService {

    @Value('${octopus3.flix.repositoryOpsUrl}')
    String repositoryOpsUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    String createOpsRecipe(FlixPackage flixPackage) {
        def packageUrnStr = flixPackage.baseUrn.toString()

        def builder = new groovy.json.JsonBuilder()
        builder.ops {
            zip {
                source packageUrnStr
            }
            copy {
                source "${packageUrnStr}.zip"
                destination flixPackage.destinationUrn.toString()
            }
            delete {
                source "${packageUrnStr}.zip"
            }
        }
        def result = builder.toString()
        log.info "recipe for $flixPackage is $result"
        result
    }

    rx.Observable<String> packageFlow(FlixPackage flixPackage) {
        log.info "creating package"
        def recipe = createOpsRecipe(flixPackage)
        httpClient.doPost(repositoryOpsUrl, recipe).flatMap({
            rx.Observable.from("success for $flixPackage")
        }).onErrorReturn({
            log.error "error in $flixPackage", it
            "error in $flixPackage"
        })
    }

}
