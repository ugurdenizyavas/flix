package com.sony.ebs.octopus3.microservices.flix.validators

import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import org.junit.Before
import org.junit.Test

class RequestValidatorTest {

    RequestValidator validator
    Flix flix

    @Before
    void before() {
        validator = new RequestValidator()
        flix = new Flix(publication: "SCORE", locale: "en_GB", sdate: "2014-07-09T00:00:00.000Z", edate: "2014-07-09T00:00:00.000Z")
    }

    @Test
    void "all valid"() {
        assert !validator.validateFlix(flix)
    }

    @Test
    void "invalid publication "() {
        flix.publication = "??"
        assert validator.validateFlix(flix) == ["publication parameter is invalid"]
    }

    @Test
    void "invalid locale "() {
        flix.locale = "_tr"
        assert validator.validateFlix(flix) == ["locale parameter is invalid"]
    }

    @Test
    void "invalid sdate "() {
        flix.sdate = "s1"
        assert validator.validateFlix(flix) == ["sdate parameter is invalid"]
    }

    @Test
    void "invalid edate "() {
        flix.edate = "s1"
        assert validator.validateFlix(flix) == ["edate parameter is invalid"]
    }

    @Test
    void " more than one invalid params"() {
        flix.locale = "_tr"
        flix.sdate = "s1"
        def errors = validator.validateFlix(flix)
        assert errors.size() == 2
        assert errors.contains("locale parameter is invalid")
        assert errors.contains("sdate parameter is invalid")
    }

    @Test
    void "sheet all valid"() {
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb")
        assert !validator.validateFlixSheet(flixSheet)
    }

    @Test
    void "invalid urn"() {
        def flixSheet = new FlixSheet(processId: "123", urnStr: "xxx")
        assert validator.validateFlixSheet(flixSheet) == ["urn parameter is invalid"]
    }

    @Test
    void "no processId"() {
        def flixSheet = new FlixSheet(urnStr: "urn:flix:score:en_gb")
        assert !validator.validateFlixSheet(flixSheet)
    }

    @Test
    void "invalid processId"() {
        def flixSheet = new FlixSheet(processId: "aa", urnStr: "urn:flix:score:en_gb")
        assert validator.validateFlixSheet(flixSheet) == ["processId parameter is invalid"]
    }
}
