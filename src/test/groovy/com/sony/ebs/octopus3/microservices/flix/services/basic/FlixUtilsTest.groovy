package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNCreationException
import com.sony.ebs.octopus3.commons.urn.URNImpl
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

    @Test(expected = URNCreationException)
    void "getThirdPartyUrn null value"() {
        FlixUtils.getThirdPartyUrn(null)
    }

    @Test
    void "getThirdPartyUrn"() {
        assert FlixUtils.getThirdPartyUrn("aaa")?.toString() == "urn:thirdparty:flixmedia:aaa"
    }

    @Test(expected = URNCreationException)
    void "getArchiveUrn null value"() {
        FlixUtils.getArchiveUrn(null)
    }

    @Test
    void "getArchiveUrn"() {
        assert FlixUtils.getArchiveUrn("aaa")?.toString() == "urn:archive:flix_sku:aaa"
    }


}
