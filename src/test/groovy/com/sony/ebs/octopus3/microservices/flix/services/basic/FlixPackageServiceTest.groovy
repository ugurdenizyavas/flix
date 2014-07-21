package com.sony.ebs.octopus3.microservices.flix.services.basic

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

    @Test
    void "test ops recipe"() {
        def recipe = flixPackageService.createOpsRecipe(new FlixPackage(publication: "SCORE", locale: "fr_BE"))

        def actual = new JsonSlurper().parseText(recipe)

        assert actual.ops.zip.source == "urn:flixmedia:score:fr_be"

        assert actual.ops.copy.source == "urn:flixmedia:score:fr_be.zip"
        assert actual.ops.copy.destination ==~ /urn:thirdparty:flix_fr_be_[0-9]{8}_[0-9]{6}\.zip/
    }

}
