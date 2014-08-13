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

            List result = []
            if (validator.validateFlix(flix)) {
                activity.error "error validating $flix : $flix.errors"
                response.status(400)
                render json(status: 400, flix: flix, errors: flix.errors)
            } else {
                flixService.flixFlow(flix).subscribe({
                    result << it?.toString()
                    activity.info "$it"
                }, { e ->
                    flix.errors << e.message
                    activity.error "error in $flix", e
                }, {
                    if (flix.errors) {
                        response.status(500)
                        render json(status: 500, flix: flix, errors: flix.errors)
                    } else {
                        response.status(200)
                        render json(status: 200, flix: flix, result: result)
                    }
                })
            }

        }
    }

}
