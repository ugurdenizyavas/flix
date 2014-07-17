package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixPackage
import groovy.json.JsonSlurper
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test

@Slf4j
class FlixPackageServiceTest {

    FlixPackageService flixPackageService
    StubFor mockNingHttpClient

    @Before
    void before() {
        flixPackageService = new FlixPackageService(repositoryOpsUrl: "/ops")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    @Test
    void "package flow"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, String data ->
                assert url == "/ops"
                rx.Observable.from("xxx")
            }
        }
        flixPackageService.httpClient = mockNingHttpClient.proxyInstance()

        FlixPackage flixPackage = new FlixPackage(publication: "SCORE", locale: "fr_FR")
        def result = flixPackageService.packageFlow(flixPackage).toBlocking().single()
        assert result == "success for FlixPackage(publication:SCORE, locale:fr_FR)"
    }


    def assertJson = { String t1, String t2 ->
        def jsonSlurper = new JsonSlurper()
        def expected = jsonSlurper.parseText(t1)
        def actual = jsonSlurper.parseText(t2)
        assert expected == actual
    }

    @Test
    void "test ops recipe"() {
        def actual = flixPackageService.createOpsRecipe(new FlixPackage(publication: "SCORE", locale: "fr_FR"))
        assertJson(RECIPE, actual)
    }

    def RECIPE = """
{
    "ops": {
        "copy": {
            "source": "urn:flixmedia:score:fr_fr.zip",
            "destination": "urn:archive:flixmedia:score:fr_fr.zip"
        },
        "zip": {
            "source": "urn:flixmedia:score:fr_fr.zip"
        }
    }
}
"""
}
