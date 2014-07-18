package com.sony.ebs.octopus3.microservices.flix.validators

import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import org.junit.Before
import org.junit.Test

class RequestValidatorTest {

    RequestValidator validator

    @Before
    void before() {
        validator = new RequestValidator()
    }

    @Test
    void "all valid"() {
        def flix = new Flix(publication: "GLOBAL", locale: "en_GB")
        assert !validator.validateFlix(flix)
    }

    @Test
    void "invalid publication "() {
        def flix = new Flix(publication: "??", locale: "en_GB")
        assert validator.validateFlix(flix) == ["publication parameter is invalid"]
    }

    @Test
    void "invalid locale "() {
        def flix = new Flix(publication: "GLOBAL", locale: "_tr")
        assert validator.validateFlix(flix) == ["locale parameter is invalid"]
    }

    @Test
    void "valid sdate "() {
        def flix = new Flix(publication: "GLOBAL", locale: "en_GB", sdate: "2014-07-09T00:00:00.000Z")
        assert !validator.validateFlix(flix)
    }

    @Test
    void "invalid sdate "() {
        def flix = new Flix(publication: "GLOBAL", locale: "en_GB", sdate: "s1")
        assert validator.validateFlix(flix) == ["sdate parameter is invalid"]
    }

    @Test
    void "valid edate "() {
        def flix = new Flix(publication: "GLOBAL", locale: "en_GB", edate: "2014-07-09T00:00:00.000Z")
        assert !validator.validateFlix(flix)
    }

    @Test
    void "invalid edate "() {
        def flix = new Flix(publication: "GLOBAL", locale: "en_GB", edate: "s1")
        assert validator.validateFlix(flix) == ["edate parameter is invalid"]
    }

    @Test
    void " more than one invalid params"() {
        def flix = new Flix(publication: "GLOBAL", locale: "_tr", sdate: "s1")
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
    void "valid processId"() {
        def flixSheet = new FlixSheet(processId: "a8ff962c-1410-49bc-a8fd-896309033171", urnStr: "urn:flix:a")
        assert !validator.validateFlixSheet(flixSheet)
    }

    @Test
    void "invalid processId"() {
        def flixSheet = new FlixSheet(processId: "a123?", urnStr: "urn:flix:score:en_gb")
        assert validator.validateFlixSheet(flixSheet) == ["processId parameter is invalid"]
    }
}
