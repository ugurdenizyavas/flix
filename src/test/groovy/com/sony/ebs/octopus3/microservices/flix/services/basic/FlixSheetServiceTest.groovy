package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.enhancer.EanCodeEnhancer
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import com.sony.ebs.octopus3.microservices.flix.services.sub.FlixXmlBuilder
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
class FlixSheetServiceTest {

    FlixSheetService flixSheetService
    StubFor mockNingHttpClient, mockEanCodeEnhancer, mockFlixXmlBuilder

    static ExecController execController

    static String VALID_JSON = '{"a":"1", "b": { "c" : ["2","3"]}}'

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
        flixSheetService = new FlixSheetService(execControl: execController.control, repositoryFileServiceUrl: "/repository/file/:urn")

        mockNingHttpClient = new StubFor(NingHttpClient)
        mockEanCodeEnhancer = new StubFor(EanCodeEnhancer)
        mockFlixXmlBuilder = new StubFor(FlixXmlBuilder)
    }

    def runFlow(FlixSheet flixSheet) {
        flixSheetService.httpClient = mockNingHttpClient.proxyInstance()
        flixSheetService.eanCodeEnhancer = mockEanCodeEnhancer.proxyInstance()
        flixSheetService.flixXmlBuilder = mockFlixXmlBuilder.proxyInstance()

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            flixSheetService.sheetFlow(flixSheet).subscribe({
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
            doGet(1) { String url ->
                assert url == "/repository/file/urn:flix:score:en_gb:a"
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: VALID_JSON))
            }
            doPost(1) { String url, InputStream is ->
                assert url == "/repository/file/urn:flixmedia:score:en_gb:a.xml"
                assert is.text == "some xml"
                rx.Observable.just(new MockNingResponse(_statusCode: 200))
            }
        }

        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.just(obj)
            }
        }

        mockFlixXmlBuilder.demand.with {
            buildXml(1) { json ->
                assert json.eanCode == "ea1"
                assert json.a == "1"
                assert json.b.c == ["2", "3"]
                "some xml"
            }
        }
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb:a")
        assert runFlow(flixSheet) == "success"
    }

    @Test
    void "ean code error"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                rx.Observable.just(obj)
            }
        }
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb:a")
        assert runFlow(flixSheet) == "outOfFlow"
        assert flixSheet.errors == ["ean code does not exist"]
    }

    @Test
    void "sheet not found"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.just(obj)
            }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 404))
            }
        }
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb:a")
        assert runFlow(flixSheet) == "outOfFlow"
        assert flixSheet.errors == ["HTTP 404 error getting sheet from repo"]
    }

    @Test
    void "invalid sheet"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.just(obj)
            }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: 'invalid json'))
            }
        }
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb:a")
        assert runFlow(flixSheet) == "error"
    }

    @Test
    void "error building xml"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.just(obj)
            }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: VALID_JSON))
            }
        }
        mockFlixXmlBuilder.demand.with {
            buildXml(1) {
                throw new Exception("error building xml")
            }
        }
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb:a")
        assert runFlow(flixSheet) == "error"
    }

    @Test
    void "error saving xml"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.just(obj)
            }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: VALID_JSON))
            }
            doPost(1) { String url, InputStream is ->
                rx.Observable.just(new MockNingResponse(_statusCode: 500))
            }
        }
        mockFlixXmlBuilder.demand.with {
            buildXml(1) { json ->
                "some xml"
            }
        }
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb:a")
        assert runFlow(flixSheet) == "outOfFlow"
        assert flixSheet.errors == ["HTTP 500 error saving flix xml to repo"]
    }
}
