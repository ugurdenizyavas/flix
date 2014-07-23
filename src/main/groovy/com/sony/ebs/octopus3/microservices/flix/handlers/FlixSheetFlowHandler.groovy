package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixSheetService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j
@Component
class FlixSheetFlowHandler extends GroovyHandler {

    @Autowired
    FlixSheetService flixSheetService

    @Autowired
    RequestValidator validator


    @Override
    protected void handle(GroovyContext context) {
        context.with {
            log.info "starting flixSheet"
            FlixSheet flixSheet = new FlixSheet(processId: request.queryParams.processId, urnStr: pathTokens.urn)

            List errors = validator.validateFlixSheet(flixSheet)
            if (errors) {
                log.error "error validating $flixSheet : $errors"
                response.status(400)
                render json(status: 400, errors: errors, flixSheet: flixSheet)
            } else {
                flixSheetService.sheetFlow(flixSheet).subscribe({ result ->
                    log.info "$result"
                }, { e ->
                    log.error "error in $flixSheet", e
                })
                log.info "$flixSheet started"
                response.status(202)
                render json(status: 202, message: "flixSheet started", flixSheet: flixSheet)
            }

        }
    }

}
