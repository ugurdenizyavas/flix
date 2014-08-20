package com.sony.ebs.octopus3.microservices.flix.handlers

class HandlerUtil {

    static String getErrorMessage(Throwable t) {
        (t.message ?: t.cause?.message)
    }
}
