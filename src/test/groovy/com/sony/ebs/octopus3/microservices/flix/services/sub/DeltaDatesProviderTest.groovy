package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.file.FileAttribute
import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.flix.model.Flix
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
class DeltaDatesProviderTest {

    DeltaDatesProvider deltaDatesProvider
    StubFor mockNingHttpClient, mockFileAttributesProvider

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
        deltaDatesProvider = new DeltaDatesProvider(
                execControl: execController.control,
                repositoryFileServiceUrl: "/repository/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
        mockFileAttributesProvider = new StubFor(FileAttributesProvider)
    }

    def runUpdateLastModified(Flix flix) {
        deltaDatesProvider.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            deltaDatesProvider.updateLastModified(flix).subscribe({
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
    void "update last modified"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, InputStream is ->
                assert url == "/repository/file/urn:flixmedia:last_modified:score:fr_be"
                assert is.text == "update"
                rx.Observable.just(new MockNingResponse(_statusCode: 200))
            }
        }
        def flix = new Flix(publication: "SCORE", locale: "fr_BE")
        assert runUpdateLastModified(flix) == "done"
    }

    @Test
    void "update last modified outOfFlow"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, InputStream is ->
                rx.Observable.just(new MockNingResponse(_statusCode: 500))
            }
        }
        def flix = new Flix(publication: "SCORE", locale: "fr_BE")
        assert runUpdateLastModified(flix) == "outOfFlow"
        assert flix.errors == ["HTTP 500 error updating last modified date"]
    }

    @Test
    void "update last modified error"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, InputStream is ->
                throw new Exception("error updating last modified time")
            }
        }
        def flix = new Flix(publication: "SCORE", locale: "fr_BE")
        assert runUpdateLastModified(flix) == "error"
    }

    def runCreateDateParams(sdate, edate) {
        deltaDatesProvider.fileAttributesProvider = mockFileAttributesProvider.proxyInstance()

        def flix = new Flix(publication: "SCORE", locale: "fr_BE", sdate: sdate, edate: edate)

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            deltaDatesProvider.createDateParams(flix).subscribe({
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
    void "create date params"() {
        mockFileAttributesProvider.demand.with {
            getLastModifiedTime(1) { URN urn ->
                assert urn.toString() == "urn:flixmedia:last_modified:score:fr_be"
                rx.Observable.just(new FileAttribute(found: true, value: "s1"))
            }
        }
        assert runCreateDateParams(null, null) == "?sdate=s1"
    }

    @Test
    void "create date params sdate not found and edate"() {
        mockFileAttributesProvider.demand.with {
            getLastModifiedTime(1) { URN urn ->
                assert urn.toString() == "urn:flixmedia:last_modified:score:fr_be"
                rx.Observable.just(new FileAttribute(found: false))
            }
        }
        assert runCreateDateParams(null, "s2") == "?edate=s2"
    }

    @Test
    void "create date params with sdate"() {
        assert runCreateDateParams("s1", null) == "?sdate=s1"
    }


    @Test
    void "create date params with sdate and edate"() {
        assert runCreateDateParams("s1", "s2") == "?sdate=s1&edate=s2"
    }

}
