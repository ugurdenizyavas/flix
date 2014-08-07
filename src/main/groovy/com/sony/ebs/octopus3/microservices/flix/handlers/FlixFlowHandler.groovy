package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixService
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
class FlixFlowHandler extends GroovyHandler {

    @Autowired
    FlixService flixService

    @Autowired
    RequestValidator validator


    @Override
    protected void handle(GroovyContext context) {
        context.with {
            Flix flix = new Flix(processId: new ProcessIdImpl(), publication: pathTokens.publication,
                    locale: pathTokens.locale, sdate: request.queryParams.sdate, edate: request.queryParams.edate)
            activity.info "starting $flix"

            List errors = validator.validateFlix(flix)
            if (errors) {
                activity.error "error validating $flix : $errors"
                response.status(400)
                render json(status: 400, errors: errors, flix: flix)
            } else {
                flixService.flixFlow(flix).subscribe({ result ->
                    activity.info "$result"
                }, { e ->
                    activity.error "error in $flix", e
                })
                activity.info "$flix started"
                response.status(202)
                render json(status: 202, message: "flix started", flix: flix)
            }

        }
    }

}
