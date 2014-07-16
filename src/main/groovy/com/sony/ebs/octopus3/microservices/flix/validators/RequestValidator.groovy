package com.sony.ebs.octopus3.microservices.flix.validators

import com.sony.ebs.octopus3.commons.date.ISODateUtils
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixPackage
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
        validatePublication(errors, flix.publication)
        validateLocale(errors, flix.locale)
        validateDate(errors, flix.sdate, "sdate")
        validateDate(errors, flix.edate, "edate")
        errors
    }

    void validateDate(List errors, String date, String name) {
        try {
            ISODateUtils.toISODate(date)
        } catch (e) {
            errors << "$name parameter is invalid".toString()
        }
    }

    void validateLocale(List errors, String locale) {
        try {
            LocaleUtils.toLocale(locale)
        } catch (e) {
            errors << "locale parameter is invalid"
        }

    }

    void validatePublication(List errors, String publication) {
        if (!(publication ==~ /[a-zA-Z0-9\-]+/)) {
            errors << "publication parameter is invalid"
        }
    }

    List validateFlixPackage(FlixPackage flixPackage) {
        def errors = []
        validatePublication(errors, flixPackage.publication)
        validateLocale(errors, flixPackage.locale)
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
