package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.flix.services.FlixService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j
@Component
class FlixFlowHandler extends GroovyHandler {

    @Autowired
    FlixService flixService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            String publication = pathTokens.publication
            String locale = pathTokens.locale

            def sendError = { String message ->
                log.error message
                response.status(400)
                render json(status: 400, message: message, publication: publication, locale: locale)
            }
            ProcessId processId = new ProcessIdImpl()
            flixService.flixFlow(processId, publication, locale).subscribe({ result ->
                log.info "flix media generation finished for publication $publication, locale $locale, reuslt: $result"
            })
            log.info "flix media generation started for publication $publication, locale $locale"
            response.status(202)
            render json(status: 202, processId: processId.id, message: "flix media generation started", publication: publication, locale: locale)
        }
    }

}
