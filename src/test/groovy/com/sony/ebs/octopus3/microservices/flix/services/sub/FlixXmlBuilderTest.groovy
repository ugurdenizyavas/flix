package com.sony.ebs.octopus3.microservices.flix.services.sub

import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.custommonkey.xmlunit.Diff
import org.custommonkey.xmlunit.XMLUnit
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.springframework.core.io.DefaultResourceLoader

@Slf4j
class FlixXmlBuilderTest {

    final static String BASE_PATH = "classpath:com/sony/ebs/octopus3/microservices/flix/services/"

    DefaultResourceLoader defaultResourceLoader = new DefaultResourceLoader()

    FlixXmlBuilder flixXmlBuilder

    @Before
    void before() {
        flixXmlBuilder = new FlixXmlBuilder()
    }

    @After
    void after() {
    }

    def getFileText(name) {
        defaultResourceLoader.getResource(BASE_PATH + name)?.inputStream.text
    }

    def testBuildXml(name) {
        def json = new JsonSlurper().parseText(getFileText("${name}.json"))
        def actual = flixXmlBuilder.buildXml(json)
        def expected = getFileText("${name}.xml")

        XMLUnit.setIgnoreWhitespace(true)
        XMLUnit.setIgnoreDiffBetweenTextAndCDATA(true)
        def xmlDiff = new Diff(expected, actual)
        assert xmlDiff.similar()
    }

    @Test
    void "test hierarchy"() {
        testBuildXml("x1")
    }

    @Test
    void "test non alphanumeric elements"() {
        testBuildXml("x2")
    }

    @Test
    void "test real cadc json"() {
        testBuildXml("x3")
    }

}
