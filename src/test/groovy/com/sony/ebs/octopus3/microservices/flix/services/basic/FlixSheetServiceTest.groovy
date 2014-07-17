package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import com.sony.ebs.octopus3.microservices.flix.services.sub.EanCodeProvider
import com.sony.ebs.octopus3.microservices.flix.services.sub.FlixXmlBuilder
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder

@Slf4j
class FlixSheetServiceTest {

    FlixSheetService flixSheetService
    ExecController execController
    StubFor mockNingHttpClient, mockEanCodeProvider, mockFlixXmlBuilder

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixSheetService = new FlixSheetService(execControl: execController.control, repositoryFileUrl: "/repository/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
        mockEanCodeProvider = new StubFor(EanCodeProvider)
        mockFlixXmlBuilder = new StubFor(FlixXmlBuilder)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "import sheet"() {
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb:a")

        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/repository/file/urn:flix:score:en_gb:a"
                rx.Observable.from('{"a":"1", "b": { "c" : ["2","3"]}}')
            }
            doPost(1) { String url, String data ->
                assert url == "/repository/file/urn:flixmedia:flixsheet:score:en_gb:a"
                assert data == "some xml"
                rx.Observable.from("done")
            }
        }

        mockEanCodeProvider.demand.with {
            getEanCode(1) {
                rx.Observable.from("ea1")
            }
        }

        mockFlixXmlBuilder.demand.with {
            buildXml(1) { json ->
                assert json.eanCode == "ea1"
                assert json.a == "1"
                assert json.b.c == ["2","3"]
                "some xml"
            }
        }

        flixSheetService.httpClient = mockNingHttpClient.proxyInstance()
        flixSheetService.eanCodeProvider = mockEanCodeProvider.proxyInstance()
        flixSheetService.flixXmlBuilder = mockFlixXmlBuilder.proxyInstance()

        def finished = new Object()
        def result
        execController.start {
            flixSheetService.importSheet(flixSheet).subscribe { String res ->
                synchronized (finished) {
                    result = res
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
        assert result == "success for FlixSheet(processId:123, urnStr:urn:flix:score:en_gb:a)"
    }

}
