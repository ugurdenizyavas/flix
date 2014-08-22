package com.sony.ebs.octopus3.microservices.flix.model

import com.sony.ebs.octopus3.commons.urn.URNCreationException
import org.junit.Test

class FlixTest {

    @Test
    void "base urn"() {
        new Flix(publication: "SCORE", locale: "fr_FR").baseUrn?.toString() == "urn:flixmedia:score:fr_fr"
    }

    @Test(expected = URNCreationException)
    void "base urn no locale"() {
        new Flix(publication: "SCORE").baseUrn
    }

    @Test(expected = URNCreationException)
    void "base urn no publication"() {
        new Flix(locale: "fr_FR").baseUrn
    }

    @Test
    void "delta urn"() {
        new Flix(publication: "SCORE", locale: "fr_FR").deltaUrn?.toString() == "urn:global_sku:score:fr_fr"
    }

    @Test
    void "last modified urn"() {
        new Flix(publication: "SCORE", locale: "fr_FR").lastModifiedUrn?.toString() == "urn:last_modified:score:fr_fr"
    }
    @Test
    void "category urn"() {
        new Flix(publication: "SCORE", locale: "fr_FR").categoryUrn?.toString() == "urn:flixmedia:score:fr_fr:category.xml"
    }

    @Test
    void "test getThirdPartyUrn"() {
        assert new Flix(publication: "GLOBAL", locale: "fr_BE").getThirdPartyUrn("aaa")?.toString() == "urn:thirdparty:flixmedia:aaa"
    }

    @Test
    void "test getArchiveUrn"() {
        assert new Flix(publication: "GLOBAL", locale: "fr_BE").getArchiveUrn("aaa")?.toString() == "urn:archive:flix_sku:aaa"
    }
}
