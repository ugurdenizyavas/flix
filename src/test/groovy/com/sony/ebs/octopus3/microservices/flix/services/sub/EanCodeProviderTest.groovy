package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.services.sub.EanCodeProvider
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
        eanCodeProvider = new EanCodeProvider(execControl: execController.control, serviceUrl: "/ean/:product")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    void "run flow"(String expected, String xml) {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/ean/a"
                rx.Observable.from(xml)
            }
        }

        eanCodeProvider.httpClient = mockNingHttpClient.proxyInstance()

        def finished = new Object()
        def result
        execController.start {
            eanCodeProvider.getEanCode(new URNImpl("urn:flix:a"))
                    .doOnError({
                synchronized (finished) {
                    result = "error"
                    finished.notifyAll()
                }
            }).subscribe { String res ->
                synchronized (finished) {
                    result = res
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
        assert result == expected
    }

    @Test
    void "success case"() {
        "run flow"("4905524328974", '<eancodes><eancode material="DSC-500" code="4905524328974"/></eancodes>')
    }

    @Test
    void "error case"() {
        "run flow"("error", 'invalid xml')
    }

}
