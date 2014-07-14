package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.flix.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder

@Slf4j
class FlixServiceTest {

    FlixService flixService
    ExecController execController
    StubFor mockNingHttpClient

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixService = new FlixService(execControl: execController.control, sheetUrl: "/flix/sheet", repositoryDeltaUrl: "/delta")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "flix flow"() {
        def flix = new Flix(processId: new ProcessIdImpl("123"), publication: "SCORE", locale: "en_GB", sdate: "d1", edate: "d2")

        mockNingHttpClient.demand.with {
            getLocal(4) { String url ->
                String result = ""
                if (url.startsWith("/delta")) result = '{ "urns" : ["urn:flix:a", "urn:flix:b", "urn:flix:c"]}'
                if (url.startsWith("/flix/sheet")) result = "$url"
                log.info "getLocal url $url"
                rx.Observable.from(result)
            }
        }

        flixService.httpClient = mockNingHttpClient.proxyInstance()

        def finished = new Object()
        execController.start {
            flixService.flixFlow(flix).subscribe { String result ->
                synchronized (finished) {
                    log.info "result $result"
                    assert result == "[success for urn:flix:a, success for urn:flix:b, success for urn:flix:c]"
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
    }

}
