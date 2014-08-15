package com.sony.ebs.octopus3.microservices.flix.model

import com.sony.ebs.octopus3.commons.urn.URNCreationException
import org.junit.Test

class FlixSheetTest {

    @Test(expected = URNCreationException)
    void "no urn"() {
        new FlixSheet().urn
    }

    @Test(expected = URNCreationException)
    void "invalid urn"() {
        new FlixSheet(urnStr: "urn:a").urn
    }

    @Test
    void "valid urn"() {
        assert new FlixSheet(urnStr: "urn:a:b").urn.toString() == "urn:a:b"
    }

    @Test(expected = URNCreationException)
    void "invalid sheetUrn"() {
        new FlixSheet(urnStr: "urn:a").sheetUrn
    }

    @Test
    void "valid sheetUrn"() {
        assert new FlixSheet(urnStr: "urn:a:b").sheetUrn?.toString() == "urn:flixmedia:b.xml"
    }

    @Test
    void "valid longer sheetUrn"() {
        assert new FlixSheet(urnStr: "urn:a:b:c:d").sheetUrn?.toString() == "urn:flixmedia:b:c:d.xml"
    }
}