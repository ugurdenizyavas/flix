package com.sony.ebs.octopus3.microservices.flix.model

import org.junit.Test

class FlixPackageTest {

    @Test
    void "test destinationUrn type"() {
        assert new FlixPackage(publication: "GLOBAL", locale: "fr_BE").destinationUrn.type == FlixUrnValue.thirdparty.toString()
    }

    @Test
    void "test destinationUrn value"() {
        def values = new FlixPackage(publication: "GLOBAL", locale: "fr_BE").destinationUrn.values
        assert values.size() == 1
        assert values[0] ==~ /flix_fr_be_[0-9]{8}_[0-9]{6}\.zip/
    }

    @Test
    void "test baseUrn"() {
        assert new FlixPackage(publication: "GLOBAL", locale: "fr_BE").baseUrn.toString() == "urn:flixmedia:global:fr_be"
    }

}
