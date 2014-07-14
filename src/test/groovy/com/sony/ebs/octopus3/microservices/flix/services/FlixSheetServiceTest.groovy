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
    StubFor mockNingHttpClient

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixSheetService = new FlixSheetService(execControl: execController.control, repositoryFileUrl: "/repository/file")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "import sheet"() {
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb")

        mockNingHttpClient.demand.with {
            getLocal(1) { String url ->
                rx.Observable.from('"a":"1", "b": { "c" : ["2","3"]}')
            }
            postLocal(1) { String url, String data ->
                rx.Observable.from("done")
            }
        }

        flixSheetService.httpClient = mockNingHttpClient.proxyInstance()

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
