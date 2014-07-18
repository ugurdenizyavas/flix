package com.sony.ebs.octopus3.microservices.flix.services.sub

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

    void runFlow(String expected, String xml) {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/ean/a"
                if (xml == 'exception')
                    throw new Exception("error in doGet")
                rx.Observable.from(xml)
            }
        }

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
        runFlow("4905524328974", '<eancodes><eancode material="DSC-500" code="4905524328974"/></eancodes>')
    }

    @Test
    void "error case"() {
        runFlow(null, 'invalid xml')
    }

    //@Test
    void "get from octopus issue case"() {
        runFlow(null, 'exception')
    }

}
