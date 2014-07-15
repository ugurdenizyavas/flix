package com.sony.ebs.octopus3.microservices.flix.handlers

import groovy.util.logging.Slf4j
import ratpack.error.ClientErrorHandler
import ratpack.error.ServerErrorHandler
import ratpack.handling.Context

@Slf4j
class ErrorHandler implements ServerErrorHandler, ClientErrorHandler {

    @Override
    void error(Context context, int statusCode) throws Exception {
        context.response.status(statusCode).send("Client error $statusCode")
    }

    @Override
    void error(Context context, Exception exception) throws Exception {
        log.error("error exception", exception)
        context.response.status(500) //.send("server error 500")
    }
}
