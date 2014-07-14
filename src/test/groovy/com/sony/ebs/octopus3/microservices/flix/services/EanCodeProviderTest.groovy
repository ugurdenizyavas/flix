package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.urn.URNImpl
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
class EanCodeProviderTest {

    EanCodeProvider eanCodeProvider
    ExecController execController
    StubFor mockNingHttpClient

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        eanCodeProvider = new EanCodeProvider(execControl: execController.control, serviceUrl: "/ean")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "flix flow"() {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/ean/a"
                def xml = """
                <eancodes>
                    <eancode material="DSC-500" code="4905524328974"/>
                </eancodes>
                """
                rx.Observable.from(xml)
            }
        }

        eanCodeProvider.httpClient = mockNingHttpClient.proxyInstance()

        def finished = new Object()
        execController.start {
            eanCodeProvider.getEanCode(new URNImpl("urn:flix:a")).subscribe { String result ->
                synchronized (finished) {
                    assert result == "4905524328974"
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
