package com.sony.ebs.octopus3.microservices.flix

import com.github.dreamhead.moco.HttpServer
import com.github.dreamhead.moco.Moco
import com.github.dreamhead.moco.Runner
import com.jayway.restassured.response.Response
import cucumber.api.groovy.EN
import cucumber.api.groovy.Hooks
import groovy.json.JsonSlurper
import ratpack.groovy.test.LocalScriptApplicationUnderTest
import ratpack.groovy.test.TestHttpClient
import ratpack.groovy.test.TestHttpClients

import static com.github.dreamhead.moco.Moco.by
import static com.github.dreamhead.moco.Moco.uri
import static com.github.dreamhead.moco.Moco.and
import static com.github.dreamhead.moco.Moco.eq
import static com.github.dreamhead.moco.Moco.query
import static com.github.dreamhead.moco.Moco.with
import static com.github.dreamhead.moco.Moco.status

this.metaClass.mixin(Hooks)
this.metaClass.mixin(EN)

System.setProperty 'environment', 'cucumber'
System.setProperty 'ratpack.port', '12300'

class LocalRatpackWorld {
    LocalScriptApplicationUnderTest aut = new LocalScriptApplicationUnderTest()
    @Delegate
    TestHttpClient client = TestHttpClients.testHttpClient(aut)

    HttpServer server = Moco.httpserver(12306)
    Runner runner = Runner.runner(server)
}

def parseJson = { Response response ->
    def text = response.body.asString()
    new JsonSlurper().parseText(text)
}

World {
    new LocalRatpackWorld()
}

Before() {
    runner.start()
}

After() {
    runner.stop()
    aut.stop()
}

def validateError = { Response response, message ->
    assert response.statusCode == 400
    def json = parseJson(response)
    assert json?.status == 400
    assert json?.errors == [message]
}

/*
* ******************** FLIX DELTA SERVICE ***********************************************************
* */

def createIdentifiersFeed = { materialName ->
    """
            <products>
                <product>
                    <identifier type='display_name'><![CDATA[DSC-RX10]]></identifier>
                    <identifier type='catalogue_name'><![CDATA[DSC-RX10]]></identifier>
                    <identifier type='business_group'><![CDATA[DIM]]></identifier>
                    <identifier type='spider_business_group'><![CDATA[DIME]]></identifier>
                    <identifier type='sap_hierarchy'><![CDATA[SCPCDIMDSCCYBRCYBS]]></identifier>
                    <identifier type='eight_digit'><![CDATA[80814350]]></identifier>
                    <identifier type='material_name'><![CDATA[DSCRX10.CE3]]></identifier>
                    <identifier type='ean_code'><![CDATA[$materialName]]></identifier>
                </product>
            </products>
    """
}

def createProductServiceResult(String sku, eanCode, errors) {
    """
        {
            "errors" : ${errors.collect({ "\"$it\"" })} ,
            "result" : {
                "eanCode" : "$eanCode",
                "inputUrn" : "urn:global_sku:score:en_gb:${sku.toLowerCase()}",
                "inputUrl" : "http://localhost:12306/repository/file/urn:global_sku:score:en_gb:${sku.toLowerCase()}",
                "outputUrn" : "urn:flixmedia:score:en_gb:${sku.toLowerCase()}.xml",
                "outputUrl" : "http://localhost:12306/repository/file/urn:flixmedia:score:en_gb:${sku.toLowerCase()}.xml"
            }
        }
    """
}
def createProductServiceResult(String sku, errors) {
    """
        {
            "errors" : ${errors.collect({ "\"$it\"" })} ,
            "result" : {
                "inputUrn" : "urn:global_sku:score:en_gb:${sku.toLowerCase()}",
                "inputUrl" : "http://localhost:12306/repository/file/urn:global_sku:score:en_gb:${sku.toLowerCase()}"
            }
        }
    """
}

Given(~"Flix delta for publication (.*) locale (.*) with (.*)") { String publication, String locale, String expected ->
    def pubAndLocale = publication.toLowerCase() + ":" + locale.toLowerCase()

    String DELTA_FEED = """
{
    "results" : [
        "urn:test_sku:$pubAndLocale:a",
        "urn:test_sku:$pubAndLocale:b",
        "urn:test_sku:$pubAndLocale:c",
        "urn:test_sku:$pubAndLocale:d",
        "urn:test_sku:$pubAndLocale:e",
        "urn:test_sku:$pubAndLocale:f",
        "urn:test_sku:$pubAndLocale:g",
        "urn:test_sku:$pubAndLocale:h",
        "urn:test_sku:$pubAndLocale:ss-ac3_2f_2fc+ce7",
        "urn:test_sku:$pubAndLocale:ss-ac3_2b_2fc+ce7"
    ]
}
"""

    String CATEGORY_FEED = """
<ProductHierarchy name="category" publication="SCORE" locale="en_GB">
    <node>
        <name><![CDATA[SCORE]]></name>
        <displayName><![CDATA[SCORE]]></displayName>
        <nodes>
            <node>
                <name><![CDATA[TVH TV and Home Cinema]]></name>
                <nodes>
                    <node>
                        <name><![CDATA[tv]]></name>
                        <products>
                            <product><![CDATA[c]]></product>
                            <product><![CDATA[D]]></product>
                            <product><![CDATA[E]]></product>
                            <product><![CDATA[f]]></product>
                            <product><![CDATA[SS-AC3//C CE7]]></product>
                        </products>
                    </node>
                    <node>
                        <name><![CDATA[projectors]]></name>
                        <products>
                            <product><![CDATA[G]]></product>
                            <product><![CDATA[h]]></product>
                            <product><![CDATA[SS-AC3+/C CE7]]></product>
                        </products>
                    </node>
                </nodes>
            </node>
        </nodes>
    </node>
</ProductHierarchy>
"""

    if (expected == "delta error") {
        server.get(by(uri("/repository/delta/urn:global_sku:$pubAndLocale"))).response(status(500))
    } else {
        server.get(by(uri("/repository/delta/urn:global_sku:$pubAndLocale"))).response(with(DELTA_FEED), status(200))
    }
    if (expected == "delete current error") {
        server.delete(by(uri("/repository/file/urn:flixmedia:$pubAndLocale"))).response(status(500))
    } else {
        server.delete(by(uri("/repository/file/urn:flixmedia:$pubAndLocale"))).response(status(200))
    }
    if (expected == "update last modified date error") {
        server.post(by(uri("/repository/file/urn:flixmedia:last_modified:$pubAndLocale"))).response(status(500))
    } else {
        server.post(by(uri("/repository/file/urn:flixmedia:last_modified:$pubAndLocale"))).response(status(200))
    }
    if (expected == "get category error") {
        server.get(by(uri("/product/publications/$publication/locales/$locale/hierarchies/category"))).response(status(500))
    } else {
        server.get(by(uri("/product/publications/$publication/locales/$locale/hierarchies/category"))).response(with(CATEGORY_FEED), status(200))
    }
    if (expected == "save category error") {
        server.post(by(uri("/repository/file/urn:flixmedia:$pubAndLocale:category.xml"))).response(status(500))
    } else {
        server.post(by(uri("/repository/file/urn:flixmedia:$pubAndLocale:category.xml"))).response(status(200))
    }
    if (expected == "ops error") {
        server.post(by(uri("/repository/ops"))).response(status(500))
    } else {
        server.post(by(uri("/repository/ops"))).response(status(200))
    }


    server.get(and(by(uri("/flix/product/publication/$publication/locale/$locale/sku/c")), eq(query("category"), "tv"))).response(with(createProductServiceResult("c", [])), status(500))
    server.get(and(by(uri("/flix/product/publication/$publication/locale/$locale/sku/d")), eq(query("category"), "tv"))).response(with(createProductServiceResult("d", [])), status(500))

    server.get(and(by(uri("/flix/product/publication/$publication/locale/$locale/sku/e")), eq(query("category"), "tv"))).response(with(createProductServiceResult("e", "1", [])), status(200))
    server.get(and(by(uri("/flix/product/publication/$publication/locale/$locale/sku/f")), eq(query("category"), "tv"))).response(with(createProductServiceResult("f", "1", ["err1", "err2"])), status(500))
    server.get(and(by(uri("/flix/product/publication/$publication/locale/$locale/sku/g")), eq(query("category"), "projectors"))).response(with(createProductServiceResult("g", "1", [])), status(200))
    server.get(and(by(uri("/flix/product/publication/$publication/locale/$locale/sku/h")), eq(query("category"), "projectors"))).response(with(createProductServiceResult("h", "1", ["err2", "err3"])), status(500))

    server.get(and(by(uri("/flix/product/publication/$publication/locale/$locale/sku/ss-ac3_2f_2fc+ce7")), eq(query("category"), "tv"))).response(with(createProductServiceResult("ss-ac3_2f_2fc+ce7", "1", [])), status(200))
    server.get(and(by(uri("/flix/product/publication/$publication/locale/$locale/sku/ss-ac3_2b_2fc+ce7")), eq(query("category"), "projectors"))).response(with(createProductServiceResult("ss-ac3_2b_2fc+ce7", "1", [])), status(200))
}

When(~"I request flix delta service for publication (.*) locale (.*)") { publication, locale ->
    get("flix/delta/publication/$publication/locale/$locale?upload=true&sdate=2014-07-09T00:00:00.000Z&edate=2014-07-12T00:00:00.000Z")
}

When(~"I request flix delta service no upload for publication (.*) locale (.*)") { publication, locale ->
    get("flix/delta/publication/$publication/locale/$locale?sdate=2014-07-09T00:00:00.000Z&edate=2014-07-12T00:00:00.000Z")
}

def validateDeltaSuccess = { response, publication, locale, upload ->
    def pubAndLocale = publication.toLowerCase() + ":" + locale.toLowerCase()

    def getUrn = { "urn:test_sku:$pubAndLocale:$it".toString() }
    def getXmlUrl = { "http://localhost:12306/repository/file/urn:flixmedia:$pubAndLocale:${it}.xml".toString() }

    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.delta.publication == publication
    assert json.delta.locale == locale
    assert json.delta.sdate == "2014-07-09T00:00:00.000Z"
    assert json.delta.edate == "2014-07-12T00:00:00.000Z"
    assert json.delta.processId

    if (upload) {
        assert json.result.other."package created" ==~ /http:\/\/localhost:12306\/repository\/file\/urn:thirdparty:flixmedia:flix_[a-z]{2}_[a-z]{2}_[0-9]{8}_[0-9]{6}\.zip/
    } else {
        assert !json.result.other."package created"
    }
    assert json.result.other."package archived" ==~ /http:\/\/localhost:12306\/repository\/file\/urn:archive:flix_sku:flix_[a-z]{2}_[a-z]{2}_[0-9]{8}_[0-9]{6}\.zip/

    assert json.result.stats."number of delta products" == 10
    assert json.result.stats."number of products filtered out by category" == 2
    assert json.result.stats."number of products filtered out by ean code" == 2
    assert json.result.stats."number of successful" == 4
    assert json.result.stats."number of unsuccessful" == 2
    assert json.result.stats."sum" == 10

    assert json.result.urns.deltaUrns?.sort() == [
            getUrn("a"), getUrn("b"), getUrn("c"), getUrn("d"), getUrn("e"), getUrn("f"),
            getUrn("g"), getUrn("h"), getUrn("ss-ac3_2b_2fc+ce7"), getUrn("ss-ac3_2f_2fc+ce7")
    ]
    assert json.result.urns.categoryFilteredOutUrns?.sort() == [getUrn("a"), getUrn("b")]
    assert json.result.urns.eanCodeFilteredOutUrns?.sort() == [getUrn("c"), getUrn("d")]

    assert json.result.other.outputUrls?.sort() == [
            getXmlUrl("e"),
            getXmlUrl("g"),
            getXmlUrl("ss-ac3_2b_2fc+ce7"),
            getXmlUrl("ss-ac3_2f_2fc+ce7")
    ]

    assert json.result.productErrors.size() == 3
    assert json.result.productErrors.err1 == [getUrn("f")]
    assert json.result.productErrors.err2?.sort() == [getUrn("f"), getUrn("h")]
    assert json.result.productErrors.err3 == [getUrn("h")]
}

Then(~"Flix delta service for publication (.*) locale (.*) should be done") { publication, locale ->
    validateDeltaSuccess(response, publication, locale, true)
}
Then(~"Flix delta service no upload for publication (.*) locale (.*) should be done") { publication, locale ->
    validateDeltaSuccess(response, publication, locale, false)
}

Then(~"Flix delta service should give (.*) error") { String error ->
    assert response.statusCode == 500
    def json = parseJson(response)
    assert json?.status == 500
    assert json.delta.publication
    assert json.delta.locale
    assert json?.errors == [error]
}

When(~"I request flix delta service with invalid (.*) parameter") { paramName ->
    if (paramName == "publication") {
        get("flix/delta/publication/,,/locale/en_GB")
    } else if (paramName == "locale") {
        get("flix/delta/publication/SCORE/locale/tr_")
    } else if (paramName == "sdate") {
        get("flix/delta/publication/SCORE/locale/en_GB?sdate=s1")
    } else if (paramName == "edate") {
        get("flix/delta/publication/SCORE/locale/en_GB?edate=s2")
    }
}

Then(~"Flix delta service should reject with (.*) parameter error") { paramName ->
    validateError(response, "$paramName parameter is invalid")
}

/*
* ******************** FLIX PRODUCT SERVICE *************************************************************
* */

Given(~"Octopus ean code (.*) for product (.*)") { String eanCode, String sku ->
    def url = "/product/identifiers/material_name/${sku.toUpperCase()}"
    server.get(by(uri(url))).response(with(createIdentifiersFeed(eanCode)), status(200))
}

Given(~"Category (.*) for product (.*)") { category,  sku ->
    String categoryFeed = """
<ProductHierarchy name="category" publication="SCORE" locale="en_GB">
    <node>
        <name><![CDATA[SCORE]]></name>
        <displayName><![CDATA[SCORE]]></displayName>
        <nodes>
            <node>
                <name><![CDATA[$category]]></name>
                <products>
                    <product><![CDATA[$sku]]></product>
                </products>
            </node>
        </nodes>
    </node>
</ProductHierarchy>

    """
    def url = "/product/publications/SCORE/locales/en_GB/hierarchies/category"
    server.get(by(uri(url))).response(with(categoryFeed), status(200))

}

Given(~"Repo product for product (.*)") { String sku ->
    def jsonUri = "/repository/file/urn:global_sku:score:en_gb:${sku.toLowerCase()}"
    server.get(by(uri(jsonUri))).response(with('{"a":"1"}'), status(200))
}

Given(~"Repo product with process id (.*) for product (.*)") { String processId, String sku->
    def jsonUri = "/repository/file/urn:global_sku:score:en_gb:${sku.toLowerCase()}"
    server.get(and(by(uri(jsonUri)), eq(query("processId"), processId))).response(with('{"a":"1"}'), status(200))
}

Given(~"Repo product not found for product (.*)") { String sku ->
    def jsonUri = "/repository/file/urn:global_sku:score:en_gb:${sku.toLowerCase()}"
    server.get(by(uri(jsonUri))).response(with('{"a":"1"}'), status(404))
}

Given(~"Xml save error for product (.*)") { String sku ->
    def xmlUri = "/repository/file/urn:flixmedia:score:en_gb:${sku.toLowerCase()}.xml"
    server.post(by(uri(xmlUri))).response(status(500))
}

Given(~"Xml save success for product (.*)") { String sku ->
    def xmlUri = "/repository/file/urn:flixmedia:score:en_gb:${sku.toLowerCase()}.xml"
    server.post(by(uri(xmlUri))).response(status(200))
}

Given(~"Xml save success with process id (.*) for product (.*)") { String processId, String sku->
    def xmlUri = "/repository/file/urn:flixmedia:score:en_gb:${sku.toLowerCase()}.xml"
    server.post(and(by(uri(xmlUri)), eq(query("processId"), processId))).response(status(200))
}

When(~"I request flix product service for product (.*)") { sku ->
    get("flix/product/publication/SCORE/locale/en_GB/sku/$sku")
}

When(~"I request flix product service with category (.*) for product (.*)") { category, sku ->
    get("flix/product/publication/SCORE/locale/en_GB/sku/$sku?category=$category")
}

When(~"I request flix product service with process id (.*) for product (.*)") { processId, sku ->
    get("flix/product/publication/SCORE/locale/en_GB/sku/$sku?processId=$processId")
}

def validateProductResponse(json, sku) {
    assert json.product.publication == "SCORE"
    assert json.product.locale == "en_GB"
    assert json.product.sku == sku

    assert json.timeStats

    assert json.result.inputUrn == "urn:global_sku:score:en_gb:${sku}"
    assert json.result.inputUrl == "http://localhost:12306/repository/file/urn:global_sku:score:en_gb:${sku}"
}

Then(~"Flix product service for product (.*) category (.*) ean code (.*) should be done") { sku, category, eanCode ->
    assert response.statusCode == 200
    def json = parseJson(response)

    validateProductResponse(json, sku)

    assert json.status == 200
    assert !json.errors
    assert json.result.outputUrn == "urn:flixmedia:score:en_gb:${sku}.xml"
    assert json.result.outputUrl == "http://localhost:12306/repository/file/urn:flixmedia:score:en_gb:${sku}.xml"
    assert json.result.eanCode == eanCode
    assert json.result.category == category
}

Then(~"Flix product service with process id (.*) for product (.*) category (.*) ean code (.*) should be done") { processId, sku, category, eanCode ->
    assert response.statusCode == 200
    def json = parseJson(response)

    validateProductResponse(json, sku)

    assert json.status == 200
    assert !json.errors
    assert json.result.outputUrn == "urn:flixmedia:score:en_gb:${sku}.xml"
    assert json.result.outputUrl == "http://localhost:12306/repository/file/urn:flixmedia:score:en_gb:${sku}.xml"
    assert json.result.eanCode == eanCode
    assert json.result.category == category
    assert json.product.processId == processId
}

Then(~"Flix product service for product (.*) should fail with (.*)") { sku, error ->
    assert response.statusCode == 500
    def json = parseJson(response)

    validateProductResponse(json, sku)

    assert json.status == 500
    assert json.errors == [error]
    assert !json.result.outputUrn
    assert !json.result.outputUrl
}



