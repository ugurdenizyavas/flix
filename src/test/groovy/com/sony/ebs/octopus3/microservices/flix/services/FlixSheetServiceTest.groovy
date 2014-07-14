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
    StubFor mockNingHttpClient, mockEanCodeProvider

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixSheetService = new FlixSheetService(execControl: execController.control, repositoryFileUrl: "/repository/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
        mockEanCodeProvider = new StubFor(EanCodeProvider)
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
                assert url == "/repository/file/urn:flix-xml:score:en_gb:a"
                rx.Observable.from("done")
            }
        }

        mockEanCodeProvider.demand.with {
            getEanCode(1) {
                rx.Observable.from("ea1")
            }
        }

        flixSheetService.httpClient = mockNingHttpClient.proxyInstance()
        flixSheetService.eanCodeProvider = mockEanCodeProvider.proxyInstance()

        def finished = new Object()
        execController.start {
            flixSheetService.importSheet(flixSheet).subscribe { String result ->
                synchronized (finished) {
                    assert result == "done"
                    log.info "finished test"
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
    }

}
