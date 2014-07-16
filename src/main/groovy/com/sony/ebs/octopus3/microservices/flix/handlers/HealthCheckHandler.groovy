package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.services.MonitoringService
import org.springframework.beans.factory.annotation.Autowired
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

class HealthCheckHandler extends GroovyHandler {

    @Autowired
    MonitoringService monitoringService

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            def params = [:]

            params.enabled = request.queryParams.enabled

            if (params.enabled) {
                def action = params.enabled.toBoolean()
                if (action) {
                    monitoringService.up()
                    response.status(200)
                    render json(status: 200, message: "App is up for the eyes of LB!")
                } else {
                    monitoringService.down()
                    response.status(200)
                    render json(status: 200, message: "App is down for the eyes of LB!")
                }
            } else {
                if (monitoringService.checkStatus()) {
                    response.status(200)
                    render json(status: 200, message: "Ticking!")
                } else {
                    response.status(404)
                    render json(status: 404, message: "App is down!")
                }
            }
        }
    }

}
