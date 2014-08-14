package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheetServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.sub.CategoryService
import com.sony.ebs.octopus3.microservices.flix.services.dates.DeltaDatesProvider
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
class FlixServiceTest {

    final
    static String DELTA_FEED = '''
        {
            "results" : [
                "urn:global_sku:score:en_gb:a",
                "urn:global_sku:score:en_gb:b",
                "urn:global_sku:score:en_gb:c",
                "urn:global_sku:score:en_gb:d"
            ]
        }
'''
    final static String CATEGORY_FEED = "<categories/>"

    static Map SHEET_FEED = [a: '{ "result" : ["success"]}', b: '{ "errors" : ["err1", "err2"]}', d: '{ "result" : ["success"]}']

    FlixService flixService
    StubFor mockCategoryService, mockDeltaDatesProvider
    MockFor mockNingHttpClient

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
        flixService = new FlixService(execControl: execController.control, flixSheetServiceUrl: "/flix/sheet/:urn",
                repositoryDeltaServiceUrl: "/delta/:urn", repositoryFileServiceUrl: "/file/:urn")
        mockNingHttpClient = new MockFor(NingHttpClient)
        mockCategoryService = new StubFor(CategoryService)
        mockDeltaDatesProvider = new StubFor(DeltaDatesProvider)
    }

    def runFlow(Flix flix) {
        flixService.httpClient = mockNingHttpClient.proxyInstance()
        flixService.categoryService = mockCategoryService.proxyInstance()
        flixService.deltaDatesProvider = mockDeltaDatesProvider.proxyInstance()

        def result = new BlockingVariable<List<String>>(5)
        execController.start {
            flixService.flixFlow(flix).toList().subscribe({
                result.set(it)
            }, {
                log.error "error", it
                result.set(["error"])
            })
        }
        result.get()
    }

    @Test
    void "success"() {
        def flix = new Flix(processId: new ProcessIdImpl("123"), publication: "SCORE", locale: "en_GB", sdate: "d1", edate: "d2")
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/delta/urn:global_sku:score:en_gb?dates"
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doDelete(1) { String url ->
                assert url == "/file/urn:flixmedia:score:en_gb"
                rx.Observable.just(new MockNingResponse(_statusCode: 200))
            }
            doGet(4) { String url ->
                assert url.startsWith("/flix/sheet/urn:global_sku:score:en_gb")
                def key = url[39]
                if (key == 'c') {
                    throw new Exception("error in c")
                }
                rx.Observable.just(new MockNingResponse(_statusCode: (key == 'b' ? 500 : 200), _responseBody: SHEET_FEED[key]))
            }
        }
        mockCategoryService.demand.with {
            retrieveCategoryFeed(1) { f ->
                assert f == flix
                rx.Observable.just(CATEGORY_FEED)
            }
            filterForCategory(1) { Flix f, categoryFeed ->
                assert f == flix
                rx.Observable.just(f.deltaUrns)
            }
        }
        mockDeltaDatesProvider.demand.with {
            createDateParams(1) { f ->
                assert f == flix
                rx.Observable.just("?dates")
            }
            updateLastModified(1) { f ->
                assert f == flix
                rx.Observable.just("done")
            }
        }
        def result = runFlow(flix).sort()
        assert result.size() == 4
        assert result[0] == new FlixSheetServiceResult(urn: "urn:global_sku:score:en_gb:a", success: true, statusCode: 200)
        assert result[1] == new FlixSheetServiceResult(urn: "urn:global_sku:score:en_gb:b", success: false, statusCode: 500, errors: ["err1", "err2"])
        assert result[2] == new FlixSheetServiceResult(urn: "urn:global_sku:score:en_gb:c", success: false, statusCode: 0, errors: ["error in c"])
        assert result[3] == new FlixSheetServiceResult(urn: "urn:global_sku:score:en_gb:d", success: true, statusCode: 200)
    }

    @Test
    void "filtered by category"() {
        def flix = new Flix(processId: new ProcessIdImpl("123"), publication: "SCORE", locale: "en_GB", sdate: "d1", edate: "d2")
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doDelete(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 200))
            }
            doGet(3) {
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: SHEET_FEED['a']))
            }
        }
        mockCategoryService.demand.with {
            retrieveCategoryFeed(1) {
                rx.Observable.just(CATEGORY_FEED)
            }
            filterForCategory(1) { Flix f, categoryFeed ->
                rx.Observable.just(f.deltaUrns - f.deltaUrns.last())
            }
        }
        mockDeltaDatesProvider.demand.with {
            createDateParams(1) {
                rx.Observable.just("?dates")
            }
            updateLastModified(1) {
                rx.Observable.just("done")
            }
        }
        def result = runFlow(flix).sort()
        assert result.size() == 3
        assert result[0] == new FlixSheetServiceResult(urn: "urn:global_sku:score:en_gb:a", success: true, statusCode: 200)
        assert result[1] == new FlixSheetServiceResult(urn: "urn:global_sku:score:en_gb:b", success: true, statusCode: 200)
        assert result[2] == new FlixSheetServiceResult(urn: "urn:global_sku:score:en_gb:c", success: true, statusCode: 200)
    }

    @Test
    void "error getting delta"() {
        mockDeltaDatesProvider.demand.with {
            createDateParams(1) { rx.Observable.just("?dates") }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 404))
            }
        }
        def flix = new Flix(publication: "SCORE", locale: "en_GB")
        assert runFlow(flix) == []
        assert flix.errors == ["HTTP 404 error retrieving delta from repo service"]
    }

    @Test
    void "error deleting existing feeds"() {
        mockDeltaDatesProvider.demand.with {
            createDateParams(1) { rx.Observable.just("?dates") }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doDelete(1) { String url ->
                rx.Observable.just(new MockNingResponse(_statusCode: 500))
            }
        }
        def flix = new Flix(publication: "SCORE", locale: "en_GB")
        assert runFlow(flix) == []
        assert flix.errors == ["HTTP 500 error deleting current flix xmls"]
    }


    @Test
    void "error updating last modified time"() {
        mockDeltaDatesProvider.demand.with {
            createDateParams(1) { rx.Observable.just("?dates") }
            updateLastModified(1) {
                throw new Exception("error updating last modified time")
            }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doDelete(1) { String url ->
                rx.Observable.just(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow(new Flix(publication: "SCORE", locale: "en_GB")) == ["error"]
    }

}
