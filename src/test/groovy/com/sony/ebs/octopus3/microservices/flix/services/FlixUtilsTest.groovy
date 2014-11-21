package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.urn.URNCreationException
import com.sony.ebs.octopus3.microservices.flix.services.FlixUtils
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test

@Slf4j
class FlixUtilsTest {

    @Before
    void before() {
    }

    @Test(expected = URNCreationException)
    void "getXmlUrn invalid sheetUrn"() {
        FlixUtils.getXmlUrn("urn:a")
    }

    @Test
    void "getXmlUrn valid sheetUrn"() {
        assert FlixUtils.getXmlUrn("urn:a:b")?.toString() == "urn:flixmedia:b.xml"
    }

    @Test
    void "getXmlUrn valid longer sheetUrn"() {
        assert FlixUtils.getXmlUrn("urn:a:b:c:d")?.toString() == "urn:flixmedia:b:c:d.xml"
    }

    @Test
    void "getCategoryUrn"() {
        assert FlixUtils.getCategoryUrn("SCORE", "en_GB")?.toString() == "urn:flixmedia:score:en_gb:category.xml"
    }

    @Test(expected = URNCreationException)
    void "getCategoryUrn null values"() {
        FlixUtils.getCategoryUrn(null, null)
    }

    @Test(expected = URNCreationException)
    void "getCategoryUrn null publication"() {
        FlixUtils.getCategoryUrn(null, "en_GB")
    }

    @Test(expected = URNCreationException)
    void "getCategoryUrn null locale"() {
        FlixUtils.getCategoryUrn("SCORE", null)
    }

    @Test
    void "getThirdPartyUrn"() {
        assert FlixUtils.getThirdPartyUrn()?.toString() == "urn:thirdparty:flixmedia"
    }

    @Test
    void "getArchiveUrn"() {
        assert FlixUtils.getArchiveUrn()?.toString() == "urn:archive:flix_sku"
    }


}
