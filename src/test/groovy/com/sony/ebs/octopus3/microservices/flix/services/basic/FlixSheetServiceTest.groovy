package com.sony.ebs.octopus3.microservices.flix.services.basic

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
        flixSheetService = new FlixSheetService(execControl: execController.control, repositoryFileUrl: "/repository/file/:urn")

        mockNingHttpClient = new StubFor(NingHttpClient)
        mockEanCodeEnhancer = new StubFor(EanCodeEnhancer)
        mockFlixXmlBuilder = new StubFor(FlixXmlBuilder)
    }

    void runFlow(String expected, boolean complete = false) {

        flixSheetService.httpClient = mockNingHttpClient.proxyInstance()
        flixSheetService.eanCodeEnhancer = mockEanCodeEnhancer.proxyInstance()
        flixSheetService.flixXmlBuilder = mockFlixXmlBuilder.proxyInstance()

        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb:a")

        def result = new BlockingVariable<String>(5)
        execController.start {
            flixSheetService.sheetFlow(flixSheet).subscribe({
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            }, {
                if (complete) {
                    result.set(expected)
                }
            })
        }
        assert result.get() == expected
    }

    @Test
    void "success"() {
        mockNingHttpClient.demand.with {
            doGetAsString(1) { String url ->
                assert url == "/repository/file/urn:flix:score:en_gb:a"
                rx.Observable.from('{"a":"1", "b": { "c" : ["2","3"]}}')
            }
            doPost(1) { String url, String data ->
                assert url == "/repository/file/urn:flixmedia:score:en_gb:a"
                assert data == "some xml"
                rx.Observable.from("done")
            }
        }

        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.from(obj)
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
        runFlow("success for FlixSheet(processId:123, urnStr:urn:flix:score:en_gb:a)")
    }

    @Test
    void "ean code error"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                rx.Observable.from(obj)
            }
        }
        runFlow("completed", true)
    }

    @Test
    void "read json error"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.from(obj)
            }
        }
        mockNingHttpClient.demand.with {
            doGetAsString(1) {
                throw new Exception()
            }
        }
        runFlow("error")
    }

    @Test
    void "parse json error"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.from(obj)
            }
        }
        mockNingHttpClient.demand.with {
            doGetAsString(1) {
                rx.Observable.from('invalid json')
            }
        }
        runFlow("error")
    }

    @Test
    void "building xml"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.from(obj)
            }
        }
        mockNingHttpClient.demand.with {
            doGetAsString(1) {
                rx.Observable.from('{"a":"1", "b": { "c" : ["2","3"]}}')
            }
        }
        mockFlixXmlBuilder.demand.with {
            buildXml(1) {
                throw new Exception()
            }
        }
        runFlow("error")
    }

    @Test
    void "saving xml"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) { obj ->
                obj.eanCode = "ea1"
                rx.Observable.from(obj)
            }
        }
        mockNingHttpClient.demand.with {
            doGetAsString(1) {
                rx.Observable.from('{"a":"1", "b": { "c" : ["2","3"]}}')
            }
            doPost(1) { String url, String data ->
                throw new Exception()
            }
        }
        mockFlixXmlBuilder.demand.with {
            buildXml(1) { json ->
                "some xml"
            }
        }
        runFlow("error")
    }
}
