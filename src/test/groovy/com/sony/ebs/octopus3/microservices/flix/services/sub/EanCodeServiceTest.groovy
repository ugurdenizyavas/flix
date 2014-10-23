package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
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
    StubFor mockHttpClient

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
        mockHttpClient = new StubFor(Oct3HttpClient)
    }

    def runFilterForEanCodes(List productUrls, List errors) {
        eanCodeService.httpClient = mockHttpClient.proxyInstance()

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
        mockHttpClient.demand.with {
            doGet(1) {
                assert it == "/product/identifiers/ean_code"
                rx.Observable.just(new Oct3HttpResponse(statusCode: 404))
            }
        }
        eanCodeService.httpClient = mockHttpClient.proxyInstance()

        def errors = []
        assert runFilterForEanCodes([], errors) == "outOfFlow"
        assert errors == ["HTTP 404 error getting ean code feed"]
    }

    @Test
    void "exception in getting ean codes"() {
        mockHttpClient.demand.with {
            doGet(1) {
                assert it == "/product/identifiers/ean_code"
                throw new Exception("error in get")
            }
        }
        eanCodeService.httpClient = mockHttpClient.proxyInstance()

        def errors = []
        assert runFilterForEanCodes([], errors) == "error"
        assert errors == []
    }


    @Test
    void "filter for ean codes"() {
        String feed = """
<identifiers type="ean_code">
    <identifier materialName="SS-AC3//C CE7"><![CDATA[1]]></identifier>
    <identifier materialName="A"><![CDATA[2]]></identifier>
    <identifier materialName="b"><![CDATA[3]]></identifier>
    <identifier materialName="E"><![CDATA[4]]></identifier>
    <identifier materialName="SS-AC3+/C CE7"><![CDATA[5]]></identifier>
</identifiers>
"""
        def productUrns = [
                "urn:gs:score:en_gb:a",
                "urn:gs:score:en_gb:b",
                "urn:gs:score:en_gb:c",
                "urn:gs:score:en_gb:d",
                "urn:gs:score:en_gb:e",
                "urn:gs:score:en_gb:ss-ac3_2f_2fc+ce7",
                "urn:gs:score:en_gb:ss-ac3_2b_2fc+ce7"
        ]

        mockHttpClient.demand.with {
            doGet(1) {
                assert it == "/product/identifiers/ean_code"
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: feed.bytes))
            }
        }

        def errors = []
        Map eanCodeMap = runFilterForEanCodes(productUrns, errors)

        assert errors == []

        assert eanCodeMap.size() == 5
        assert eanCodeMap."urn:gs:score:en_gb:ss-ac3_2f_2fc+ce7" == "1"
        assert eanCodeMap."urn:gs:score:en_gb:ss-ac3_2b_2fc+ce7" == "5"
        assert eanCodeMap."urn:gs:score:en_gb:a" == "2"
        assert eanCodeMap."urn:gs:score:en_gb:b" == "3"
        assert eanCodeMap."urn:gs:score:en_gb:e" == "4"
    }

}
