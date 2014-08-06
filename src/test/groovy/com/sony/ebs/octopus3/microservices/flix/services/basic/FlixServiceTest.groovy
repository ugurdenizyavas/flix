package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.services.sub.CategoryService
import com.sony.ebs.octopus3.microservices.flix.services.sub.DateParamsProvider
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
    static String DELTA_FEED = '{ "results" : ["urn:global_sku:score:en_gb:a", "urn:global_sku:score:en_gb:b", "urn:global_sku:score:en_gb:c"]}'
    final static String CATEGORY_FEED = "<categories/>"

    FlixService flixService
    StubFor mockCategoryService, mockDateParamsProvider
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
        mockDateParamsProvider = new StubFor(DateParamsProvider)
    }

    def runFlow(flix) {
        flixService.httpClient = mockNingHttpClient.proxyInstance()
        flixService.categoryService = mockCategoryService.proxyInstance()
        flixService.dateParamsProvider = mockDateParamsProvider.proxyInstance()

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
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doDelete(1) { String url ->
                assert url == "/file/urn:flixmedia:score:en_gb"
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
            doGet(3) { String url ->
                assert url.startsWith("/flix/sheet/urn:global_sku:score:en_gb")
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: url))
            }
        }
        mockCategoryService.demand.with {
            retrieveCategoryFeed(1) { f ->
                log.info "doCategoryFeed"
                assert f == flix
                rx.Observable.from(CATEGORY_FEED)
            }
            filterForCategory(1) { List productUrns, categoryFeed ->
                log.info "filterForCategory"
                rx.Observable.just(productUrns - productUrns.last())
            }
        }
        mockDateParamsProvider.demand.with {
            createDateParams(1) { f ->
                assert f == flix
                "?dates"
            }
            updateLastModified(1) { f ->
                assert f == flix
                "done"
            }
        }
        assert runFlow(flix)?.sort() == ["success for urn:global_sku:score:en_gb:a", "success for urn:global_sku:score:en_gb:b"]
    }

    @Test
    void "error getting delta"() {
        mockDateParamsProvider.demand.with {
            createDateParams(1) { "?dates" }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        assert runFlow(new Flix(publication: "SCORE", locale: "en_GB")) == []
    }

    @Test
    void "error deleting existing feeds"() {
        mockDateParamsProvider.demand.with {
            createDateParams(1) { "?dates" }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doDelete(1) { String url ->
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        assert runFlow(new Flix(publication: "SCORE", locale: "en_GB")) == []
    }


    @Test
    void "error updating last modified time"() {
        mockDateParamsProvider.demand.with {
            createDateParams(1) { "?dates" }
            updateLastModified(1) {
                throw new Exception("error updating last modified time")
            }
        }
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: DELTA_FEED))
            }
            doDelete(1) { String url ->
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runFlow(new Flix(publication: "SCORE", locale: "en_GB")) == ["error"]
    }

}
