package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
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
class PackageServiceTest {

    PackageService packageService
    StubFor mockNingHttpClient
    Flix flix
    RepoDelta delta

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
        packageService = new PackageService(
                repositoryOpsServiceUrl: "/repo/ops",
                repositoryFileServiceUrl: "/repo/file/:urn"
                , execControl: execController.control)
        mockNingHttpClient = new StubFor(NingHttpClient)

        delta = new RepoDelta(publication: "SCORE", locale: "fr_FR")
        flix = new Flix()
    }

    def runFlow() {
        packageService.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable(5)
        boolean valueSet = false
        execController.start {
            packageService.packageFlow(delta, flix).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            }, {
                if (!valueSet) result.set("outOfFlow")
            })
        }
        result.get()
    }

    @Test
    void "success"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, InputStream is ->
                assert url == "/repo/ops"
                rx.Observable.just(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow() == "success"
        assert flix.outputPackageUrl ==~ /\/repo\/file\/urn:thirdparty:flixmedia:flix_fr_fr_[0-9]{8}_[0-9]{6}\.zip/
        assert flix.archivePackageUrl ==~ /\/repo\/file\/urn:archive:flix_sku:flix_fr_fr_[0-9]{8}_[0-9]{6}\.zip/
    }

    @Test
    void "error calling ops service"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, InputStream is ->
                rx.Observable.just(new MockNingResponse(_statusCode: 500))
            }
        }
        assert runFlow() == "outOfFlow"
        assert delta.errors == ["HTTP 500 error calling repo ops service"]
    }

    @Test
    void "exception calling ops service"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, InputStream is ->
                throw new Exception("calling ops service")
            }
        }
        assert runFlow() == "error"
    }

    @Test
    void "test ops recipe"() {
        def thirdParty = "urn:thirdparty:flixmedia:flix_fr_be.zip"
        def archive = "urn:archive:flix_sku:flix_fr_be.zip"
        def recipe = packageService.createOpsRecipe(new URNImpl("urn:flixmedia:score:fr_be"), thirdParty, archive)

        def actual = new JsonSlurper().parseText(recipe)

        assert actual.ops[0].zip.source == "urn:flixmedia:score:fr_be"

        assert actual.ops[1].copy.source == "urn:flixmedia:score:fr_be.zip"
        assert actual.ops[1].copy.destination == thirdParty

        assert actual.ops[2].copy.source == "urn:flixmedia:score:fr_be.zip"
        assert actual.ops[2].copy.destination == archive

        assert actual.ops[3].delete.source == "urn:flixmedia:score:fr_be.zip"
    }

}
