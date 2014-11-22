package com.sony.ebs.octopus3.microservices.flix.service

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import com.sony.ebs.octopus3.commons.ratpack.product.filtering.CategoryService
import com.sony.ebs.octopus3.microservices.flix.model.Flix
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
class DeltaServiceTest {

    final static String DELTA_FEED = '''
        {
            "results" : [
                "urn:test_sku:score:en_gb:a",
                "urn:test_sku:score:en_gb:b",
                "urn:test_sku:score:en_gb:c",
                "urn:test_sku:score:en_gb:d",
                "urn:test_sku:score:en_gb:e",
                "urn:test_sku:score:en_gb:f",
                "urn:test_sku:score:en_gb:g",
                "urn:test_sku:score:en_gb:h"
            ]
        }
'''
    final static String CATEGORY_FEED = "<categories/>"

    DeltaService deltaService
    StubFor mockCategoryService, mockDeltaUrlHelper
    MockFor mockHttpClient

    Flix flix
    RepoDelta delta

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
        deltaService = new DeltaService(execControl: execController.control,
                productServiceUrl: "/flix/product/publication/:publication/locale/:locale/sku/:sku",
                repositoryDeltaServiceUrl: "/delta/:urn",
                repositoryFileServiceUrl: "/file/:urn",
                repositoryFileAttributesServiceUrl: "/fileAttributes/:urn")
        mockHttpClient = new MockFor(Oct3HttpClient)
        mockCategoryService = new StubFor(CategoryService)
        mockDeltaUrlHelper = new StubFor(DeltaUrlHelper)

        delta = new RepoDelta(type: RepoValue.flixMedia, processId: new ProcessIdImpl("123"), publication: "SCORE", locale: "en_GB", sdate: "d1", edate: "d2")
        flix = new Flix()

    }

    def runFlow() {
        deltaService.httpClient = mockHttpClient.proxyInstance()
        deltaService.categoryService = mockCategoryService.proxyInstance()
        deltaService.deltaUrlHelper = mockDeltaUrlHelper.proxyInstance()

        def result = new BlockingVariable<List<String>>(5)
        execController.start {
            deltaService.processDelta(delta, flix).toList().subscribe({
                result.set(it)
            }, {
                log.error "error", it
                result.set(["error"])
            })
        }
        result.get()
    }

    def createProductResponse(p) {
        """
         {
            "result" : {
                "outputUrn" : "urn:flix:score:en_gb:${p}.xml",
                "outputUrl" : "/file/urn:flix:score:en_gb:${p}.xml"
            }
         }'
        """
    }

    @Test
    void "success"() {
        mockHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/delta/urn:global_sku:score:en_gb?dates"
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: DELTA_FEED.bytes))
            }
            doDelete(1) { String url ->
                assert url == "/file/urn:flixmedia:score:en_gb"
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200))
            }
            doGet(4) { String url ->
                assert url.startsWith("/flix/product/publication/SCORE/locale/en_GB/sku/")
                def key = url[49]
                if (key == 'f') {
                    rx.Observable.just(new Oct3HttpResponse(statusCode: 500, bodyAsBytes: '{ "errors" : ["err1", "err2"]}'.bytes))
                } else if (key == 'g') {
                    throw new Exception("error in g")
                } else {
                    rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: createProductResponse(key).bytes))
                }
            }
        }
        mockCategoryService.demand.with {
            retrieveCategoryFeed(1) { d ->
                assert d == delta
                rx.Observable.just(CATEGORY_FEED)
            }
            filterForCategory(1) { List productUrls, String categoryFeed ->
                rx.Observable.just(
                        [
                                "urn:test_sku:score:en_gb:e": "psvita",
                                "urn:test_sku:score:en_gb:f": "ps4",
                                "urn:test_sku:score:en_gb:g": "psvita",
                                "urn:test_sku:score:en_gb:h": "psvita"
                        ]
                )
            }
        }
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                assert sdate == delta.sdate
                assert lastModifiedUrn == delta.lastModifiedUrn
                rx.Observable.just("updated date")
            }
            createRepoDeltaUrl(1) { initialUrl, sdate, edate ->
                assert initialUrl == "/delta/urn:global_sku:score:en_gb"
                assert sdate == "updated date"
                assert edate == delta.edate
                rx.Observable.just("/delta/urn:global_sku:score:en_gb?dates")
            }
            updateLastModified(1) { urn, errors ->
                assert urn == delta.lastModifiedUrn
                rx.Observable.just("done")
            }
        }
        List<ProductResult> result = runFlow().sort()
        assert result.size() == 4
        assert result[0] == new ProductResult(
                success: true, statusCode: 200,
                inputUrn: "urn:test_sku:score:en_gb:e",
                inputUrl: "/file/urn:test_sku:score:en_gb:e",
                outputUrn: "urn:flix:score:en_gb:e.xml",
                outputUrl: "/file/urn:flix:score:en_gb:e.xml",
        )
        assert result[1] == new ProductResult(
                success: false, statusCode: 500,
                inputUrn: "urn:test_sku:score:en_gb:f",
                inputUrl: "/file/urn:test_sku:score:en_gb:f",
                errors: ["err1", "err2"]
        )
        assert result[2] == new ProductResult(
                success: false, statusCode: 0,
                inputUrn: "urn:test_sku:score:en_gb:g",
                inputUrl: "/file/urn:test_sku:score:en_gb:g",
                errors: ["error in g"]
        )
        assert result[3] == new ProductResult(
                success: true, statusCode: 200,
                inputUrn: "urn:test_sku:score:en_gb:h",
                inputUrl: "/file/urn:test_sku:score:en_gb:h",
                outputUrn: "urn:flix:score:en_gb:h.xml",
                outputUrl: "/file/urn:flix:score:en_gb:h.xml",
        )
    }

    @Test
    void "error getting delta"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just("updated date")
            }
            createRepoDeltaUrl(1) { initialUrl, sdate, edate ->
                rx.Observable.just("//delta?dates")
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 404))
            }
        }
        assert runFlow() == []
        assert delta.errors == ["HTTP 404 error retrieving global sku delta"]
    }

    @Test
    void "error deleting existing feeds"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just("updated date")
            }
            createRepoDeltaUrl(1) { initialUrl, sdate, edate ->
                rx.Observable.just("//delta?dates")
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: DELTA_FEED.bytes))
            }
            doDelete(1) { String url ->
                rx.Observable.just(new Oct3HttpResponse(statusCode: 500))
            }
        }
        assert runFlow() == []
        assert delta.errors == ["HTTP 500 error deleting current flix xmls"]
    }


    @Test
    void "error updating last modified time"() {
        mockDeltaUrlHelper.demand.with {
            createStartDate(1) { sdate, lastModifiedUrn ->
                rx.Observable.just("updated date")
            }
            createRepoDeltaUrl(1) { initialUrl, sdate, edate ->
                rx.Observable.just("//delta?dates")
            }
            updateLastModified(1) { urn, errors ->
                throw new Exception("error updating last modified time")
            }
        }
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: DELTA_FEED.bytes))
            }
            doDelete(1) { String url ->
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200))
            }
        }
        assert runFlow() == ["error"]
    }

}
