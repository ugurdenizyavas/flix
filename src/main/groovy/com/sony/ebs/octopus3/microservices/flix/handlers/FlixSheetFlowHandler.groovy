package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixSheetService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity", category = "activity")
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
            activity.debug "starting $flixSheet"

            List result = []
            List errors = validator.validateFlixSheet(flixSheet)
            if (errors) {
                activity.error "error validating $flixSheet : $errors"
                response.status(400)
                render json(status: 400, errors: errors, flixSheet: flixSheet)
            } else {
                flixSheetService.sheetFlow(flixSheet).finallyDo({
                    if (flixSheet.errors) {
                        activity.debug "finished $flixSheet with errors: $flixSheet.errors"
                        response.status(500)
                        render json(status: 500, errors: flixSheet.errors, flixSheet: flixSheet)
                    } else {
                        activity.debug "finished $flixSheet with success"
                        response.status(200)
                        render json(status: 200, flixSheet: flixSheet, result: result)
                    }
                }).subscribe({
                    def flowResult = it?.toString()
                    result << flowResult
                    activity.debug "$flixSheet emited: $flowResult"
                }, { e ->
                    flixSheet.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $flixSheet", e
                })
            }

        }
    }

}
