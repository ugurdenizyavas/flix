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
            FlixSheet flixSheet = new FlixSheet(urnStr: pathTokens.urn,
                    processId: request.queryParams.processId,
                    eanCode: request.queryParams.eanCode)
            activity.info "starting $flixSheet"

            List result = []
            List errors = validator.validateFlixSheet(flixSheet)
            if (errors) {
                activity.error "error validating $flixSheet : $errors"
                response.status(400)
                render json(status: 400, flixSheet: flixSheet, errors: errors)
            } else {
                flixSheetService.sheetFlow(flixSheet).finallyDo({
                    if (flixSheet.errors) {
                        response.status(500)
                        render json(status: 500, flixSheet: flixSheet, errors: flixSheet.errors)
                    } else {
                        response.status(200)
                        render json(status: 200, flixSheet: flixSheet, result: result)
                    }
                }).subscribe({
                    result << it?.toString()
                    activity.info "$flixSheet finished: $it"
                }, { e ->
                    flixSheet.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $flixSheet", e
                })
            }

        }
    }

}
