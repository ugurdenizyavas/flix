package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.services.sub.CategoryService
import com.sony.ebs.octopus3.microservices.flix.services.sub.DateParamsProvider
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder

@Slf4j
class FlixServiceTest {

    FlixService flixService
    ExecController execController
    StubFor mockNingHttpClient, mockCategoryService, mockDateParamsProvider

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixService = new FlixService(execControl: execController.control, sheetUrl: "/flix/sheet",
                repositoryDeltaUrl: "/delta/:urn", repositoryFileUrl: "/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
        mockCategoryService = new StubFor(CategoryService)
        mockDateParamsProvider = new StubFor(DateParamsProvider)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "flix flow"() {
        def flix = new Flix(processId: new ProcessIdImpl("123"), publication: "SCORE", locale: "en_GB", sdate: "d1", edate: "d2")

        mockNingHttpClient.demand.with {
            doGet(4) { String url ->
                String result = ""
                if (url.startsWith("/delta")) {
                    result = '{ "results" : ["urn:flix:a", "urn:flix:b", "urn:flix:c"]}'
                    assert url == "/delta/urn:global_sku:score:en_gb?dates"
                }
                if (url.startsWith("/flix/sheet")) result = "$url"
                log.info "getLocal url $url"
                rx.Observable.from(result)
            }
            doDelete(1) { String url ->
                assert url == "/file/urn:flixmedia:score:en_gb"
                rx.Observable.from("deleted")
            }
        }
        mockCategoryService.demand.with {
            doCategoryFeed(1) { f ->
                assert f == flix
                log.info "doCategoryFeed"
                rx.Observable.from("success for urn:category:score:en_gb")
            }
        }
        mockDateParamsProvider.demand.with {
            createDateParams(1) { f ->
                assert f == flix
                rx.Observable.from("?dates")
            }
            updateLastModified(1) { f ->
                assert f == flix
                rx.Observable.from("done")
            }
        }

        flixService.httpClient = mockNingHttpClient.proxyInstance()
        flixService.categoryService = mockCategoryService.proxyInstance()
        flixService.dateParamsProvider = mockDateParamsProvider.proxyInstance()

        def finished = new Object()
        def result = [].asSynchronized()
        execController.start {
            flixService.flixFlow(flix).subscribe { String res ->
                synchronized (finished) {
                    result << res
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
        assert result.size() == 4
        assert result.contains("success for urn:category:score:en_gb")
        assert result.contains("success for urn:flix:a")
        assert result.contains("success for urn:flix:b")
        assert result.contains("success for urn:flix:c")
    }

}
