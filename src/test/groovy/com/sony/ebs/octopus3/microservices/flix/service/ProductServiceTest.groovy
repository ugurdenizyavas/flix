package com.sony.ebs.octopus3.microservices.flix.service

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoProduct
import com.sony.ebs.octopus3.commons.ratpack.product.enhancer.EanCodeEnhancer
import groovy.mock.interceptor.MockFor
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
class ProductServiceTest {

    ProductService productService
    RepoProduct product
    ProductResult productResult
    StubFor mockFlixXmlBuilder, mockEanCodeEnhancer
    MockFor mockHttpClient

    static ExecController execController

    static String VALID_JSON = '{"a":"1", "b": { "c" : ["2","3"]}}'

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
        productService = new ProductService(execControl: execController.control, repositoryFileServiceUrl: "/repository/file/:urn")

        product = new RepoProduct(type: RepoValue.flixMedia, publication: "GLOBAL", locale: "fr_BE", sku: "a_2fb_2bc", processId: "123")

        productResult = new ProductResult()

        mockHttpClient = new MockFor(Oct3HttpClient)
        mockFlixXmlBuilder = new StubFor(FlixXmlBuilder)
        mockEanCodeEnhancer = new StubFor(EanCodeEnhancer)
    }

    def runFlow() {
        productService.httpClient = mockHttpClient.proxyInstance()
        productService.flixXmlBuilder = mockFlixXmlBuilder.proxyInstance()
        productService.eanCodeEnhancer = mockEanCodeEnhancer.proxyInstance()

        def result = new BlockingVariable(5)
        boolean valueSet = false
        execController.start {
            productService.processProduct(product, productResult).subscribe({
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

    def validateProductResultSuccess() {
        productResult.with {
            assert inputUrn == "urn:global_sku:global:fr_be:a_2fb_2bc"
            assert inputUrl == "/repository/file/urn:global_sku:global:fr_be:a_2fb_2bc"
            assert outputUrn == "urn:flixmedia:global:fr_be:a_2fb_2bc.xml"
            assert outputUrl == "/repository/file/urn:flixmedia:global:fr_be:a_2fb_2bc.xml"
        }
    }

    def validateProductResultError() {
        productResult.with {
            assert inputUrn == "urn:global_sku:global:fr_be:a_2fb_2bc"
            assert inputUrl == "/repository/file/urn:global_sku:global:fr_be:a_2fb_2bc"
            assert !outputUrn
            assert !outputUrl
        }
    }


    @Test
    void "success"() {
        mockHttpClient.demand.with {
            doGet(1) {
                assert it == "/repository/file/urn:global_sku:global:fr_be:a_2fb_2bc?processId=123"
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: VALID_JSON.bytes))
            }
            doPost(1) { String url, InputStream is ->
                assert url == "/repository/file/urn:flixmedia:global:fr_be:a_2fb_2bc.xml?processId=123"
                assert is.text == "some xml"
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200))
            }
        }
        mockEanCodeEnhancer.demand.with {
            enhance(1) {
                assert it.materialName == "A/B+C"
                it.eanCode = "ea2"
                rx.Observable.just(it)
            }
        }
        mockFlixXmlBuilder.demand.with {
            buildXml(1) { json ->
                assert json.eanCode == "ea2"
                assert json.a == "1"
                assert json.b.c == ["2", "3"]
                "some xml"
            }
        }
        assert runFlow() == "success"
        validateProductResultSuccess()
        assert productResult.eanCode == "ea2"
    }

    @Test
    void "error no ean code"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) {
                rx.Observable.just(it)
            }
        }
        assert runFlow() == "outOfFlow"
        validateProductResultError()
        assert productResult.errors == ["ean code not found"]
    }

    @Test
    void "sheet not found"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) {
                it.eanCode = "ea2"
                rx.Observable.just(it)
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 404))
            }
        }
        assert runFlow() == "outOfFlow"
        validateProductResultError()
        assert productResult.errors == ["HTTP 404 error getting sheet from repo"]
    }

    @Test
    void "invalid sheet"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) {
                it.eanCode = "ea2"
                rx.Observable.just(it)
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: 'invalid json'.bytes))
            }
        }
        assert runFlow() == "error"
        validateProductResultError()
    }

    @Test
    void "error building xml"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) {
                it.eanCode = "ea2"
                rx.Observable.just(it)
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: VALID_JSON.bytes))
            }
        }
        mockFlixXmlBuilder.demand.with {
            buildXml(1) {
                throw new Exception("error building xml")
            }
        }
        assert runFlow() == "error"
        validateProductResultError()
    }

    @Test
    void "error saving xml"() {
        mockEanCodeEnhancer.demand.with {
            enhance(1) {
                it.eanCode = "ea2"
                rx.Observable.just(it)
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: VALID_JSON.bytes))
            }
            doPost(1) { String url, InputStream is ->
                rx.Observable.just(new Oct3HttpResponse(statusCode: 500))
            }
        }
        mockFlixXmlBuilder.demand.with {
            buildXml(1) { json ->
                "some xml"
            }
        }
        assert runFlow() == "outOfFlow"
        validateProductResultError()
        assert productResult.errors == ["HTTP 500 error saving flix xml to repo"]
    }

}
