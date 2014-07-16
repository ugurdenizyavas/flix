package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.model.FlixPackage
import com.sony.ebs.octopus3.microservices.flix.services.FlixPackageService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j
@Component
class FlixPackageFlowHandler extends GroovyHandler {

    @Autowired
    FlixPackageService flixPackageService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            log.info "starting package"
            FlixPackage flixPackage = new FlixPackage(publication: pathTokens.publication, locale: pathTokens.locale)

            List errors = validator.validateFlixPackage(flixPackage)
            if (errors) {
                log.error "error for $flixPackage : $errors"
                response.status(400)
                render json(status: 400, errors: errors, flixPackage: flixPackage)
            } else {
                flixPackageService.packageFlow(flixPackage).subscribe({ result ->
                    log.info "$result"
                })
                log.info "$flixPackage started"
                response.status(202)
                render json(status: 202, message: "flixPackage started", flixPackage: flixPackage)
            }

        }
    }

}
