package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.flix.http.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixPackage
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
    StubFor mockNingHttpClient, mockCategoryService

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixService = new FlixService(execControl: execController.control, sheetUrl: "/flix/sheet", repositoryDeltaUrl: "/delta/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
        mockCategoryService = new StubFor(CategoryService)
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
                if (url.startsWith("/delta")) result = '{ "results" : ["urn:flix:a", "urn:flix:b", "urn:flix:c"]}'
                if (url.startsWith("/flix/sheet")) result = "$url"
                log.info "getLocal url $url"
                rx.Observable.from(result)
            }
        }
        mockCategoryService.demand.with {
            doCategoryFeed(1) { f ->
                assert f == flix
                log.info "doCategoryFeed"
                rx.Observable.from("success for urn:category:score:en_gb")
            }
        }

        flixService.httpClient = mockNingHttpClient.proxyInstance()
        flixService.categoryService = mockCategoryService.proxyInstance()

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
        assert result as Set == ["success for urn:category:score:en_gb", "success for urn:flix:a", "success for urn:flix:b", "success for urn:flix:c"] as Set
    }

    @Test
    void "package flow"() {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                rx.Observable.from("xxx")
            }
        }
        flixService.httpClient = mockNingHttpClient.proxyInstance()

        FlixPackage flixPackage = new FlixPackage(publication: "SCORE", locale: "fr_FR")

        def finished = new Object()
        def result
        execController.start {
            flixService.packageFlow(flixPackage).subscribe { String res ->
                synchronized (finished) {
                    result = res
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
        assert result == "success for FlixPackage(publication:SCORE, locale:fr_FR)"
    }

}
