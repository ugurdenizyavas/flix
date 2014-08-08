package com.sony.ebs.octopus3.microservices.flix.services.dates

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
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
class RepoBasedDeltaDatesProviderTest {

    final static String FILE_ATTR_FEED = '''
{
    "status": 200,
    "result": {
        "lastModifiedTime": "2014-08-08T08:18:27.000+02:00",
        "lastAccessTime": "2014-08-07T07:45:57.000+02:00",
        "creationTime": "2014-08-08T08:18:27.000+02:00",
        "regularFile": false,
        "directory": true,
        "size": 60416
    }
}
'''

    RepoBasedDeltaDatesProvider deltaDatesProvider
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
        deltaDatesProvider = new RepoBasedDeltaDatesProvider(
                execControl: execController.control,
                repositoryFileAttributesServiceUrl: "/repository/fileattributes/:urn",
                repositoryFileServiceUrl: "/repository/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    def runUpdateLastModified() {
        deltaDatesProvider.httpClient = mockNingHttpClient.proxyInstance()

        def flix = new Flix(publication: "SCORE", locale: "fr_BE")

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
            doPost(1) { String url, String data ->
                assert url == "/repository/file/urn:flixmedia:last_modified:score:fr_be"
                assert data == "update"
                rx.Observable.just(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runUpdateLastModified() == "done"
    }

    @Test
    void "update last modified outOfFlow"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, String data ->
                rx.Observable.just(new MockNingResponse(_statusCode: 500))
            }
        }
        assert runUpdateLastModified() == "outOfFlow"
    }

    @Test
    void "update last modified error"() {
        mockNingHttpClient.demand.with {
            doPost(1) { String url, String data ->
                throw new Exception("error updating last modified time")
            }
        }
        assert runUpdateLastModified() == "error"
    }

    def runCreateDateParams(sdate, edate) {
        deltaDatesProvider.httpClient = mockNingHttpClient.proxyInstance()

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
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/repository/fileattributes/urn:flixmedia:last_modified:score:fr_be"
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: FILE_ATTR_FEED))
            }
        }
        assert runCreateDateParams(null, null) == "?sdate=2014-08-08T08%3A18%3A27.000%2B02%3A00"
    }

    @Test
    void "create date params sdate not found and edate"() {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/repository/fileattributes/urn:flixmedia:last_modified:score:fr_be"
                rx.Observable.just(new MockNingResponse(_statusCode: 404))
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
