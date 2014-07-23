package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class EanCodeProviderTest {

    EanCodeProvider eanCodeProvider
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
        eanCodeProvider = new EanCodeProvider(execControl: execController.control, serviceUrl: "/ean/:product")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    void runFlow(String expected) {
        eanCodeProvider.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        execController.start {
            eanCodeProvider.getEanCode(new URNImpl("urn:flix:a")).subscribe({
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            })
        }
        assert result.get() == expected
    }

    @Test
    void "success case"() {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/ean/A"
                rx.Observable.from('<eancodes><eancode material="DSC-500" code="4905524328974"/></eancodes>')
            }
        }
        runFlow("4905524328974")
    }

    @Test
    void "error case"() {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                rx.Observable.from("invalid xml")
            }
        }
        runFlow("error")
    }

}
