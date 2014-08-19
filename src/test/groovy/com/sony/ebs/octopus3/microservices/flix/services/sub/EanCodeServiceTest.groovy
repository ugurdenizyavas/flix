package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class EanCodeServiceTest {

    EanCodeService eanCodeService
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
        eanCodeService = new EanCodeService(
                execControl: execController.control,
                octopusEanCodeServiceUrl: "/product/identifiers/ean_code")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    def runFilterForEanCodes(List productUrls, List errors) {
        eanCodeService.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable(5)
        boolean valueSet = false
        execController.start {
            eanCodeService.filterForEanCodes(productUrls, errors).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            }, {
                if (!valueSet) result.set("outOfFlow")
            })
        }
        result.get()
    }

    @Test
    void "error in getting ean codes"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "/product/identifiers/ean_code"
                rx.Observable.just(new MockNingResponse(_statusCode: 404))
            }
        }
        eanCodeService.httpClient = mockNingHttpClient.proxyInstance()

        def errors = []
        assert runFilterForEanCodes([], errors) == "outOfFlow"
        assert errors == ["HTTP 404 error getting ean code feed"]
    }

    @Test
    void "exception in getting ean codes"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "/product/identifiers/ean_code"
                throw new Exception("error in get")
            }
        }
        eanCodeService.httpClient = mockNingHttpClient.proxyInstance()

        def errors = []
        assert runFilterForEanCodes([], errors) == "error"
        assert errors == []
    }


    @Test
    void "filter for ean codes"() {
        String feed = """
<identifiers type="ean_code">
    <identifier materialName="A"><![CDATA[1]]></identifier>
    <identifier materialName="b"><![CDATA[2]]></identifier>
    <identifier materialName="E"><![CDATA[3]]></identifier>
</identifiers>
"""
        def productUrns = [
                "urn:gs:score:en_gb:a",
                "urn:gs:score:en_gb:b",
                "urn:gs:score:en_gb:c",
                "urn:gs:score:en_gb:d",
                "urn:gs:score:en_gb:e"
        ]

        mockNingHttpClient.demand.with {
            doGet(1) {
                assert it == "/product/identifiers/ean_code"
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: feed))
            }
        }

        def errors = []
        Map eanCodeMap = runFilterForEanCodes(productUrns, errors)

        assert errors == []

        assert eanCodeMap.size() == 3
        assert eanCodeMap."urn:gs:score:en_gb:a" == "1"
        assert eanCodeMap."urn:gs:score:en_gb:b" == "2"
        assert eanCodeMap."urn:gs:score:en_gb:e" == "3"
    }

}
