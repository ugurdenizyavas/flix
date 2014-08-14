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

@Slf4j(value = "activity")
@Component
@org.springframework.context.annotation.Lazy
class FlixSheetFlowHandler extends GroovyHandler {

    @Autowired
    FlixSheetService flixSheetService

    @Autowired
    RequestValidator validator


    @Override
    protected void handle(GroovyContext context) {
        context.with {
            FlixSheet flixSheet = new FlixSheet(processId: request.queryParams.processId, urnStr: pathTokens.urn)
            activity.info "starting $flixSheet"

            List result = []
            if (validator.validateFlixSheet(flixSheet)) {
                activity.error "error validating $flixSheet : $flixSheet.errors"
                response.status(400)
                render json(status: 400, flixSheet: flixSheet, errors: flixSheet.errors)
            } else {
                flixSheetService.sheetFlow(flixSheet).subscribe({
                    result << it?.toString()
                    activity.info "$flixSheet finished: $it"
                }, { e ->
                    flixSheet.errors << e.message ?: e.cause?.message
                    activity.error "error in $flixSheet", e
                }, {
                    if (flixSheet.errors) {
                        response.status(500)
                        render json(status: 500, flixSheet: flixSheet, errors: flixSheet.errors)
                    } else {
                        response.status(200)
                        render json(status: 200, flixSheet: flixSheet, result: result)
                    }
                })
            }

        }
    }

}
