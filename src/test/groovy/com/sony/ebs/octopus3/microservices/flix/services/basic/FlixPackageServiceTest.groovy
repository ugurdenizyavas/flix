package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixPackage
import groovy.json.JsonSlurper
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class FlixPackageServiceTest {

    FlixPackageService flixPackageService
    StubFor mockNingHttpClient

    static ExecController execController

    @BeforeClass
    static void beforeClass() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
    }

    @AfterClass
    static void afterClass() {
        if (execController) execController.close()
    }

    @Before
    void before() {
        flixPackageService = new FlixPackageService(repositoryOpsUrl: "/ops")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    void runFlow(String expected) {
        FlixPackage flixPackage = new FlixPackage(publication: "SCORE", locale: "fr_FR")
        flixPackageService.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        execController.start {
            flixPackageService.packageFlow(flixPackage)
                    .doOnError({
                result.set("error")
            }).subscribe({
                result.set(it)
            })
        }
        assert result.get() == expected
    }

    @Test
    void "package flow"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, String data ->
                assert url == "/ops"
                rx.Observable.from("xxx")
            }
        }
        runFlow("success for FlixPackage(publication:SCORE, locale:fr_FR)")
    }

    @Test
    void "package flow error"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, String data ->
                throw new Exception("error post")
            }
        }
        runFlow("error")
    }

    @Test
    void "test ops recipe"() {
        def recipe = flixPackageService.createOpsRecipe(new FlixPackage(publication: "SCORE", locale: "fr_BE"))

        def actual = new JsonSlurper().parseText(recipe)

        assert actual.ops.zip.source == "urn:flixmedia:score:fr_be"

        assert actual.ops.copy.source == "urn:flixmedia:score:fr_be.zip"
        assert actual.ops.copy.destination ==~ /urn:thirdparty:flixmedia:flix_fr_be_[0-9]{8}_[0-9]{6}\.zip/
        assert actual.ops.delete.source == "urn:flixmedia:score:fr_be.zip"
    }

}
