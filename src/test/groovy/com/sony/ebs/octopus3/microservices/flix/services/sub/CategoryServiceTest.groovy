package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.springframework.core.io.DefaultResourceLoader
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder
import spock.util.concurrent.BlockingVariable

@Slf4j
class CategoryServiceTest {

    DefaultResourceLoader defaultResourceLoader = new DefaultResourceLoader()
    final static String BASE_PATH = "classpath:com/sony/ebs/octopus3/microservices/flix/services/sub/"

    final static String CATEGORY_FEED = "<categories/>"

    CategoryService categoryService
    StubFor mockHttpClient

    static ExecController execController

    def getFileText(name) {
        IOUtils.toString(defaultResourceLoader.getResource(BASE_PATH + name)?.inputStream, EncodingUtil.CHARSET)
    }

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
        categoryService = new CategoryService(
                execControl: execController.control,
                octopusCategoryServiceUrl: "/product/publications/:publication/locales/:locale/hierarchies/category",
                repositoryFileServiceUrl: "/repository/file/:urn")
        mockHttpClient = new StubFor(Oct3HttpClient)
    }

    def runRetrieveCategoryFeed(RepoDelta delta) {
        categoryService.httpClient = mockHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            categoryService.retrieveCategoryFeed(delta).subscribe({
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
    void "get category feed"() {
        def categoryFeed = getFileText("category_ru.xml")

        mockHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/product/publications/SCORE/locales/en_GB/hierarchies/category"
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: categoryFeed.bytes))
            }
            doPost(1) { String url, InputStream is ->
                assert url == "/repository/file/urn:flixmedia:score:en_gb:category.xml"
                assert IOUtils.toString(is, EncodingUtil.CHARSET) == categoryFeed
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200))
            }
        }
        def delta = new RepoDelta(publication: "SCORE", locale: "en_GB")
        assert runRetrieveCategoryFeed(delta) == categoryFeed
    }

    @Test
    void "category not found"() {
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 500))
            }
        }
        categoryService.httpClient = mockHttpClient.proxyInstance()

        def delta = new RepoDelta(publication: "SCORE", locale: "en_GB")
        assert runRetrieveCategoryFeed(delta) == "outOfFlow"
        assert delta.errors == ["HTTP 500 error getting octopus category feed"]
    }

    @Test
    void "could not save"() {
        mockHttpClient.demand.with {
            doGet(1) {
                rx.Observable.just(new Oct3HttpResponse(statusCode: 200, bodyAsBytes: CATEGORY_FEED.bytes))
            }
            doPost(1) { String url, InputStream is ->
                rx.Observable.just(new Oct3HttpResponse(statusCode: 404))
            }
        }
        categoryService.httpClient = mockHttpClient.proxyInstance()

        def delta = new RepoDelta(publication: "SCORE", locale: "en_GB")
        assert runRetrieveCategoryFeed(delta) == "outOfFlow"
        assert delta.errors == ["HTTP 404 error saving octopus category feed"]
    }

    @Test
    void "exception in get"() {
        mockHttpClient.demand.with {
            doGet(1) {
                throw new Exception("error in get")
            }
        }
        categoryService.httpClient = mockHttpClient.proxyInstance()

        def delta = new RepoDelta(publication: "SCORE", locale: "en_GB")
        assert runRetrieveCategoryFeed(delta) == "error"
    }

    def runFilterForCategory(List productUrls, String categoryFeed) {
        def result = new BlockingVariable<List>(5)
        boolean valueSet = false
        execController.start {
            categoryService.filterForCategory(productUrls, categoryFeed).subscribe({
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
    void "filter for category"() {
        def xml = """
<ProductHierarchy name="category" publication="SCORE" locale="en_GB">
    <node>
        <name><![CDATA[SCORE]]></name>
        <displayName><![CDATA[SCORE]]></displayName>
        <nodes>
            <node>
                <name><![CDATA[TVH TV and Home Cinema]]></name>
                <displayName><![CDATA[TV & home cinema]]></displayName>
                <nodes>
                    <node>
                        <name><![CDATA[HCS Home Cinema Projectors]]></name>
                        <displayName><![CDATA[Projectors]]></displayName>
                        <products>
                            <product><![CDATA[A1]]></product>
                            <product><![CDATA[A2]]></product>
                            <product><![CDATA[SS-AC3+/C CE7]]></product>
                        </products>
                    </node>
                    <node>
                        <name><![CDATA[HCS Home Cinema Projectors]]></name>
                        <displayName><![CDATA[Projectors]]></displayName>
                        <products>
                            <product><![CDATA[C1]]></product>
                            <product><![CDATA[C2]]></product>
                            <product><![CDATA[SS-AC3//C CE7]]></product>
                        </products>
                    </node>
                </nodes>
            </node>
        </nodes>
    </node>
</ProductHierarchy>
"""
        def productUrls = [
                "urn:test_sku:score:en_gb:a1",
                "urn:test_sku:score:en_gb:b",
                "urn:test_sku:score:en_gb:c2",
                "urn:test_sku:score:en_gb:d",
                "urn:test_sku:score:en_gb:ss-ac3_2f_2fc+ce7",
                "urn:test_sku:score:en_gb:ss-ac3_2b_2fc+ce7"
        ]

        List filtered = runFilterForCategory(productUrls, xml)
        assert filtered.sort() == [
                "urn:test_sku:score:en_gb:a1",
                "urn:test_sku:score:en_gb:c2",
                "urn:test_sku:score:en_gb:ss-ac3_2b_2fc+ce7",
                "urn:test_sku:score:en_gb:ss-ac3_2f_2fc+ce7"
        ]
    }

}
