package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class CategoryServiceTest {

    CategoryService categoryService
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
        categoryService = new CategoryService(categoryServiceUrl: "/product/publications/:publication/locales/:locale/hierarchies/category",
                repositoryFileUrl: "/repository/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    def runFlow(String expected) {
        def flix = new Flix(publication: "SCORE", locale: "en_GB")
        categoryService.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        execController.start {
            categoryService.retrieveCategoryFeed(flix).subscribe({
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            })
        }
        assert result.get() == expected
    }

    @Test
    void "get category feed"() {
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
        runFlow("success for urn:flixmedia:score:en_gb:category")
    }

    @Test
    void "error in get"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                throw new Exception("error in get")
            }
        }
        categoryService.httpClient = mockNingHttpClient.proxyInstance()
        runFlow("error")
    }

    @Test
    void "error in post"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from("xxx")
            }
            doPost(1) { url, data ->
                throw new Exception("error in post")
            }
        }
        categoryService.httpClient = mockNingHttpClient.proxyInstance()
        runFlow("error")
    }
}
