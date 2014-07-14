package com.sony.ebs.octopus3.microservices.flix.validators

import com.sony.ebs.octopus3.commons.date.ISODateUtils
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import groovy.util.logging.Slf4j
import org.apache.commons.lang.LocaleUtils
import org.springframework.stereotype.Component

@Slf4j
@Component
class RequestValidator {

    /**
     * Validates all flix params
     * @param flix
     * @return
     */
    List validateFlix(Flix flix) {
        def errors = []

        if (!(flix.publication ==~ /[a-zA-Z0-9\-]+/)) {
            errors << "publication parameter is invalid"
        }
        try {
            LocaleUtils.toLocale(flix.locale)
        } catch (e) {
            errors << "locale parameter is invalid"
        }
        try {
            ISODateUtils.toISODate(flix.sdate)
        } catch (e) {
            errors << "sdate parameter is invalid"
        }
        try {
            ISODateUtils.toISODate(flix.edate)
        } catch (e) {
            errors << "edate parameter is invalid"
        }

        errors
    }

    /**
     * Validates all flixSheet params
     * @param flixSheet
     * @return
     */
    List validateFlixSheet(FlixSheet flixSheet) {
        def errors = []

        if (!(flixSheet.processId ==~ /[0-9\-]+/)) {
            errors << "processId parameter is invalid"
        }
        try {
            new URNImpl(flixSheet.urnStr)
        } catch (e) {
            errors << "urn parameter is invalid"
        }

        errors
    }

}
