package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.microservices.flix.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
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
    StubFor mockNingHttpClient, mockEancodeProvider

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixSheetService = new FlixSheetService(execControl: execController.control, repositoryFileUrl: "/repository/file")
        mockNingHttpClient = new StubFor(NingHttpClient)
        mockEancodeProvider = new StubFor(EancodeProvider)
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
                rx.Observable.from('{"a":"1", "b": { "c" : ["2","3"]}}')
            }
            doPost(1) { String url, String data ->
                rx.Observable.from("done")
            }
        }

        mockEancodeProvider.demand.with {
            getEanCode(1) {
                rx.Observable.from("ea1")
            }
        }

        flixSheetService.httpClient = mockNingHttpClient.proxyInstance()
        flixSheetService.eancodeProvider = mockEancodeProvider.proxyInstance()

        def finished = new Object()
        execController.start {
            flixSheetService.importSheet(flixSheet).subscribe { String result ->
                synchronized (finished) {
                    assert result == "done"
                    log.info "assertions finished"
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
    }

}
