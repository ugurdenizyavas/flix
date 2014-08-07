package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.commons.ratpack.http.ning.MockNingResponse
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
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
class CategoryServiceTest {

    final static String CATEGORY_FEED = "<categories/>"

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
        categoryService = new CategoryService(
                execControl: execController.control,
                octopusCategoryServiceUrl: "/product/publications/:publication/locales/:locale/hierarchies/category",
                repositoryFileServiceUrl: "/repository/file/:urn")
        mockNingHttpClient = new StubFor(NingHttpClient)
    }

    def runRetrieveCategoryFeed() {
        def flix = new Flix(publication: "SCORE", locale: "en_GB")
        categoryService.httpClient = mockNingHttpClient.proxyInstance()

        def result = new BlockingVariable<String>(5)
        boolean valueSet = false
        execController.start {
            categoryService.retrieveCategoryFeed(flix).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            }, {
                if (!valueSet)result.set("outOfFlow")
            })
        }
        result.get()
    }

    @Test
    void "get category feed"() {
        mockNingHttpClient.demand.with {
            doGet(1) { String url ->
                assert url == "/product/publications/SCORE/locales/en_GB/hierarchies/category"
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: CATEGORY_FEED))
            }
            doPost(1) { String url, String data ->
                assert url == "/repository/file/urn:flixmedia:score:en_gb:category"
                assert data == CATEGORY_FEED
                rx.Observable.from(new MockNingResponse(_statusCode: 200))
            }
        }
        assert runRetrieveCategoryFeed() == CATEGORY_FEED
    }

    @Test
    void "category not found"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        categoryService.httpClient = mockNingHttpClient.proxyInstance()
        assert runRetrieveCategoryFeed() == "outOfFlow"
    }

    @Test
    void "could not save"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                rx.Observable.from(new MockNingResponse(_statusCode: 200, _responseBody: CATEGORY_FEED))
            }
            doPost(1) { url, data ->
                rx.Observable.from(new MockNingResponse(_statusCode: 404))
            }
        }
        categoryService.httpClient = mockNingHttpClient.proxyInstance()
        assert runRetrieveCategoryFeed() == "outOfFlow"
    }

    @Test
    void "exception in get"() {
        mockNingHttpClient.demand.with {
            doGet(1) {
                throw new Exception("error in get")
            }
        }
        categoryService.httpClient = mockNingHttpClient.proxyInstance()
        assert runRetrieveCategoryFeed() == "error"
    }

    def runFilterForCategory(List products, String categoryXml) {
        def result = new BlockingVariable<List>(5)
        boolean valueSet = false
        execController.start {
            categoryService.filterForCategory(products, categoryXml).subscribe({
                valueSet = true
                result.set(it)
            }, {
                log.error "error", it
                result.set("error")
            }, {
                if (!valueSet)result.set("outOfFlow")
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
                        </products>
                    </node>
                    <node>
                        <name><![CDATA[HCS Home Cinema Projectors]]></name>
                        <displayName><![CDATA[Projectors]]></displayName>
                        <products>
                            <product><![CDATA[C1]]></product>
                            <product><![CDATA[C2]]></product>
                        </products>
                    </node>
                </nodes>
            </node>
        </nodes>
    </node>
</ProductHierarchy>
"""
        assert runFilterForCategory(["urn:flix:a1", "urn:flix:b", "urn:flix:c2"], xml) == ["urn:flix:a1", "urn:flix:c2"]
    }

}
