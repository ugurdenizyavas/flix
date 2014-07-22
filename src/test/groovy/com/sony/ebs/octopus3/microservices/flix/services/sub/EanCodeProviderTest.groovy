package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

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

    void runGetEanCode(String expected) {
        eanCodeProvider.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        execController.start {
            eanCodeProvider.getEanCode(new URNImpl("urn:flix:a"))
                    .doOnError({
                result.set("error")
            }).subscribe({
                result.set(it)
            })
        }
        assert result.get() == expected
    }

    @Test
    void "success case"() {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/ean/a"
                rx.Observable.from('<eancodes><eancode material="DSC-500" code="4905524328974"/></eancodes>')
            }
        }
        runGetEanCode("4905524328974")
    }

    @Test
    void "error case"() {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                rx.Observable.from("invalid xml")
            }
        }
        runGetEanCode("error")
    }

}
