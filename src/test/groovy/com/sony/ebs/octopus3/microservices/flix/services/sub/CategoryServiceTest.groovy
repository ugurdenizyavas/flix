package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class CategoryServiceTest {

    CategoryService categoryService
    ExecController execController
    StubFor mockNingHttpClient

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        categoryService = new CategoryService(categoryServiceUrl: "/product/publications/:publication/locales/:locale/hierarchies/category",
                repositoryFileUrl: "/repository/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "get category feed"() {
        def flix = new Flix(publication: "SCORE", locale: "en_GB")
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/product/publications/SCORE/locales/en_GB/hierarchies/category"
                rx.Observable.from("xxx")
            }
            doPost(1) { String url, String data ->
                assert url == "/repository/file/urn:flixmedia:score:en_gb:category"
                assert data == "xxx"
                rx.Observable.from("done")
            }
        }

        categoryService.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        execController.start {
            categoryService.doCategoryFeed(flix).subscribe({
                result.set(it)
            })
        }
        assert result.get() == "success for urn:flixmedia:score:en_gb:category"

    }

    @Test
    void "on error"() {
        def flix = new Flix(publication: "SCORE", locale: "en_GB")
        mockNingHttpClient.demand.with {
            doGet(1) { rx.Observable.from("xxx") }
            //doGet(1) { throw new RuntimeException("error in post") }
            doPost(1) { url, data -> throw new RuntimeException("error in post") }
        }

        categoryService.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        execController.start {
            categoryService.doCategoryFeed(flix).subscribe({
                result.set(it)
            })
        }
        assert result.get() == null
    }
}
