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

Given(~"Flix delta for publication (.*) locale (.*) with (.*)") { String publication, String locale, String expected ->
    def pubAndLocale = publication.toLowerCase() + ":" + locale.toLowerCase()

    String DELTA_FEED = """
{
    "results" : [
        "urn:global_sku:$pubAndLocale:a",
        "urn:global_sku:$pubAndLocale:b",
        "urn:global_sku:$pubAndLocale:c",
        "urn:global_sku:$pubAndLocale:d",
        "urn:global_sku:$pubAndLocale:e",
        "urn:global_sku:$pubAndLocale:f",
        "urn:global_sku:$pubAndLocale:g",
        "urn:global_sku:$pubAndLocale:h"
    ]
}
"""

    String EAN_CODE_FEED = """
<identifiers type="ean_code">
    <identifier materialName="E"><![CDATA[1]]></identifier>
    <identifier materialName="f"><![CDATA[2]]></identifier>
    <identifier materialName="G"><![CDATA[3]]></identifier>
    <identifier materialName="h"><![CDATA[4]]></identifier>
</identifiers>
"""

    String CATEGORY_FEED = """
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
                            <product><![CDATA[c]]></product>
                            <product><![CDATA[D]]></product>
                            <product><![CDATA[E]]></product>
                            <product><![CDATA[f]]></product>
                        </products>
                    </node>
                    <node>
                        <name><![CDATA[HCS Home Cinema Projectors]]></name>
                        <displayName><![CDATA[Projectors]]></displayName>
                        <products>
                            <product><![CDATA[G]]></product>
                            <product><![CDATA[h]]></product>
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
    if (expected == "get ean code error") {
        server.get(by(uri("/product/identifiers/ean_code"))).response(status(500))
    } else {
        server.get(by(uri("/product/identifiers/ean_code"))).response(with(EAN_CODE_FEED), status(200))
    }
    if (expected == "ops error") {
        server.post(by(uri("/repository/ops"))).response(status(500))
    } else {
        server.post(by(uri("/repository/ops"))).response(status(200))
    }

    server.get(by(uri("/flix/sheet/urn:global_sku:$pubAndLocale:e"))).response('{}')
    server.get(by(uri("/flix/sheet/urn:global_sku:$pubAndLocale:f"))).response(with('{"errors" : ["err1", "err2"]}'), status(500))
    server.get(by(uri("/flix/sheet/urn:global_sku:$pubAndLocale:g"))).response('{}')
    server.get(by(uri("/flix/sheet/urn:global_sku:$pubAndLocale:h"))).response(with('{"errors" : ["err2", "err3"]}'), status(500))
}

When(~"I request flix delta service for publication (.*) locale (.*)") { publication, locale ->
    get("flix/delta/publication/$publication/locale/$locale?sdate=2014-07-09T00:00:00.000Z&edate=2014-07-12T00:00:00.000Z")
}

Then(~"Flix delta service for publication (.*) locale (.*) should be done") { publication, locale ->
    def pubAndLocale = publication.toLowerCase() + ":" + locale.toLowerCase()

    def getUrn = { "urn:global_sku:$pubAndLocale:$it".toString() }
    def getXmlUrl = { "http://localhost:12306/repository/file/urn:flixmedia:$pubAndLocale:${it}.xml".toString() }

    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.flix.publication == publication
    assert json.flix.locale == locale
    assert json.flix.sdate == "2014-07-09T00:00:00.000Z"
    assert json.flix.edate == "2014-07-12T00:00:00.000Z"
    assert json.flix.processId
    assert json.flix.categoryFilteredOutUrns?.sort() == [getUrn("a"), getUrn("b")]
    assert json.flix.eanCodeFilteredOutUrns?.sort() == [getUrn("c"), getUrn("d")]
    assert json.flix.deltaUrns?.sort() == [getUrn("a"), getUrn("b"), getUrn("c"), getUrn("d"), getUrn("e"), getUrn("f"), getUrn("g"), getUrn("h")]

    assert json.result.stats."number of delta products" == 8
    assert json.result.stats."number of products filtered out by category" == 2
    assert json.result.stats."number of products filtered out by ean code" == 2
    assert json.result.stats."number of success" == 2
    assert json.result.stats."number of errors" == 2

    assert json.result.success?.sort() == [getXmlUrl("e"), getXmlUrl("g")]

    assert json.result.errors.size() == 3
    assert json.result.errors.err1 == [getUrn("f")]
    assert json.result.errors.err2?.sort() == [getUrn("f"), getUrn("h")]
    assert json.result.errors.err3 == [getUrn("h")]
}

Then(~"Flix delta service should give (.*) error") { String error ->
    assert response.statusCode == 500
    def json = parseJson(response)
    assert json?.status == 500
    assert json.flix.publication
    assert json.flix.locale
    assert json?.errors == [error]
    assert !json?.result
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
* ******************** FLIX SHEET SERVICE *************************************************************
* */

Given(~"Flix json for sheet (.*)") { String sheet ->
    server.get(by(uri("/repository/file/urn:global_sku:score:en_GB:$sheet"))).response(with('{"a":"1"}'), status(200))
    server.post(by(uri("/repository/file/urn:flixmedia:score:en_gb:${sheet}.xml"))).response(status(200))
}

When(~"I request flix sheet service for process (.*) sheet (.*)") { process, sheet ->
    get("flix/sheet/urn:global_sku:score:en_GB:$sheet?processId=$process&eanCode=123")
}

Then(~"Flix sheet service for process (.*) sheet (.*) should be done") { process, sheet ->
    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.result == ["success"]
    assert json.flixSheet.processId == process
    assert json.flixSheet.urnStr == "urn:global_sku:score:en_GB:$sheet"
    assert json.flixSheet.eanCode == "123"
}

When(~"I request flix sheet service with invalid urn") { ->
    get("flix/sheet/urn:xxx?eanCode=123")
}

Then(~"Flix sheet service should reject with urn parameter error") { ->
    assert response.statusCode == 400
    def json = parseJson(response)
    assert json.status == 400
    assert json.errors == ["urn parameter is invalid"]
    assert json.flixSheet.urnStr == "urn:xxx"
    assert json.flixSheet.eanCode == "123"
}

When(~"I request flix sheet service with invalid ean code") { ->
    get("flix/sheet/urn:global_sku:score:en_GB:a")
}

Then(~"Flix sheet service should reject with ean code parameter error") { ->
    assert response.statusCode == 400
    def json = parseJson(response)
    assert json.status == 400
    assert json.errors == ["eanCode parameter is invalid"]
    assert json.flixSheet.urnStr == "urn:global_sku:score:en_GB:a"
    assert !json.flixSheet.eanCode
}

/*
* ******************** FLIX PACKAGE SERVICE *************************************************************
* */
Given(~"Repository ops service with success") { ->
    server.post(by(uri("/repository/ops"))).response(status(200))
}

Given(~"Repository ops service with error") { ->
    server.post(by(uri("/repository/ops"))).response(status(500))
}

When(~"I request flix package service for publication (.*) locale (.*)") { publication, locale ->
    get("flix/package/publication/$publication/locale/$locale")
}

Then(~"Flix package service for publication (.*) locale (.*) should be successful") { publication, locale ->
    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.result == ["success"]
    assert json.flixPackage.publication == publication
    assert json.flixPackage.locale == locale
}

Then(~"Flix package service for publication (.*) locale (.*) should get error") { publication, locale ->
    assert response.statusCode == 500
    def json = parseJson(response)
    assert json.status == 500
    assert !json.result
    assert json.flixPackage.publication == publication
    assert json.flixPackage.locale == locale
    assert json.errors == ["HTTP 500 error calling repo ops service"]
}
