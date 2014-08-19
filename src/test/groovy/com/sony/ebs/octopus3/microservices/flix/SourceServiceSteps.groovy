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

Given(~"Flix delta for publication (.*) locale (.*)") { String publication, String locale ->
    def publicationLC = publication.toLowerCase()
    def localeLC = locale.toLowerCase()

    String DELTA_FEED = """
{
    "results" : [
        "urn:global_sku:$publicationLC:$localeLC:a",
        "urn:global_sku:$publicationLC:$localeLC:b",
        "urn:global_sku:$publicationLC:$localeLC:c",
        "urn:global_sku:$publicationLC:$localeLC:d",
        "urn:global_sku:$publicationLC:$localeLC:e",
        "urn:global_sku:$publicationLC:$localeLC:f",
        "urn:global_sku:$publicationLC:$localeLC:g",
        "urn:global_sku:$publicationLC:$localeLC:h"
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

    server.get(by(uri("/repository/delta/urn:global_sku:$publicationLC:$localeLC"))).response(DELTA_FEED)
    server.delete(by(uri("/repository/file/urn:flixmedia:$publicationLC:$localeLC"))).response("")
    server.post(by(uri("/repository/file/urn:flixmedia:last_modified:$publicationLC:$localeLC"))).response("")

    server.get(by(uri("/product/publications/$publication/locales/$locale/hierarchies/category"))).response(CATEGORY_FEED)
    server.post(by(uri("/repository/file/urn:flixmedia:$publicationLC:$localeLC:category.xml"))).response("")
    server.get(by(uri("/product/identifiers/ean_code"))).response(EAN_CODE_FEED)

    server.get(by(uri("/flix/sheet/urn:global_sku:$publicationLC:$localeLC:e"))).response('{}')
    server.get(by(uri("/flix/sheet/urn:global_sku:$publicationLC:$localeLC:f"))).response(with('{"errors" : ["err1", "err2"]}'), status(500))
    server.get(by(uri("/flix/sheet/urn:global_sku:$publicationLC:$localeLC:g"))).response('{}')
    server.get(by(uri("/flix/sheet/urn:global_sku:$publicationLC:$localeLC:h"))).response(with('{"errors" : ["err2", "err3"]}'), status(500))
}

When(~"I request flix delta service for publication (.*) locale (.*)") { publication, locale ->
    get("flix/delta/publication/$publication/locale/$locale?sdate=2014-07-09T00:00:00.000Z&edate=2014-07-12T00:00:00.000Z")
}

Then(~"Flix delta service for publication (.*) locale (.*) should be done") { publication, locale ->
    def publicationLC = publication.toLowerCase()
    def localeLC = locale.toLowerCase()

    def getUrn = { "urn:global_sku:$publicationLC:$localeLC:$it".toString() }
    def getXmlUrl = { "http://localhost:12306/repository/file/urn:flixmedia:$publicationLC:$localeLC:${it}.xml".toString() }

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

Then(~"Flix delta service should give (.*) parameter error") { paramName ->
    validateError(response, "$paramName parameter is invalid")
}

/*
* ******************** FLIX SHEET SERVICE *************************************************************
* */
Given(~"Flix json for sheet (.*)") { String sheet ->
    server.request(by(uri("/repository/file/urn:global_sku:score:en_GB:$sheet")))
            .response('{"a":"1"}')
    server.post(by(uri("/repository/file/urn:flixmedia:score:en_gb:${sheet}.xml")))
            .response('done')
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

Then(~"Flix sheet service should give invalid urn error") { ->
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

Then(~"Flix sheet service should give invalid ean code error") { ->
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
