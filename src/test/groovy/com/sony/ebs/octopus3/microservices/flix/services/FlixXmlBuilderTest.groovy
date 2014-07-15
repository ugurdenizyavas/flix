package com.sony.ebs.octopus3.microservices.flix.services

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Test

@Slf4j
class FlixXmlBuilderTest {

    FlixXmlBuilder flixXmlBuilder

    @Before
    void before() {
        flixXmlBuilder = new FlixXmlBuilder()
    }

    @After
    void after() {
    }

    @Test
    void "build xml"() {
        def str = '{"a":"1", "b": { "c" : ["2","3"]}}'
        def json = new JsonSlurper().parseText(str)

        def xml = new XmlParser().parseText(flixXmlBuilder.buildXml(json))

        assert xml.a.text() == "1"
        assert xml.b.c[0].text() == "2"
        assert xml.b.c[1].text() == "3"
    }

}
