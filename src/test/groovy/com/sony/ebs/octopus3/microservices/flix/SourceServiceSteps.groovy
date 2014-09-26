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
import static com.github.dreamhead.moco.Moco.with
import static com.github.dreamhead.moco.Moco.with
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
        "urn:global_sku:$pubAndLocale:h",
        "urn:global_sku:$pubAndLocale:ss-ac3_2f_2fc+ce7",
        "urn:global_sku:$pubAndLocale:ss-ac3_2b_2fc+ce7"
    ]
}
"""

    String EAN_CODE_FEED = """
<identifiers type="ean_code">
    <identifier materialName="E"><![CDATA[1]]></identifier>
    <identifier materialName="f"><![CDATA[2]]></identifier>
    <identifier materialName="G"><![CDATA[3]]></identifier>
    <identifier materialName="h"><![CDATA[4]]></identifier>
    <identifier materialName="SS-AC3+/C CE7"><![CDATA[5]]></identifier>
    <identifier materialName="SS-AC3//C CE7"><![CDATA[6]]></identifier>
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
                            <product><![CDATA[SS-AC3//C CE7]]></product>
                        </products>
                    </node>
                    <node>
                        <name><![CDATA[HCS Home Cinema Projectors]]></name>
                        <displayName><![CDATA[Projectors]]></displayName>
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

    server.get(by(uri("/flix/product/publication/$publication/locale/$locale/sku/e"))).response(with('{}'), status(200))
    server.get(by(uri("/flix/product/publication/$publication/locale/$locale/sku/f"))).response(with('{"errors" : ["err1", "err2"]}'), status(500))
    server.get(by(uri("/flix/product/publication/$publication/locale/$locale/sku/g"))).response(with('{}'), status(200))
    server.get(by(uri("/flix/product/publication/$publication/locale/$locale/sku/h"))).response(with('{"errors" : ["err2", "err3"]}'), status(500))
    server.get(by(uri("/flix/product/publication/$publication/locale/$locale/sku/ss-ac3_2f_2fc+ce7"))).response(with('{}'), status(200))
    server.get(by(uri("/flix/product/publication/$publication/locale/$locale/sku/ss-ac3_2b_2fc+ce7"))).response(with('{}'), status(200))
}

When(~"I request flix delta service for publication (.*) locale (.*)") { publication, locale ->
    get("flix/delta/publication/$publication/locale/$locale?sdate=2014-07-09T00:00:00.000Z&edate=2014-07-12T00:00:00.000Z")
}

Then(~"Flix delta service for publication (.*) locale (.*) should be done") { publication, locale ->
    def pubAndLocale = publication.toLowerCase() + ":" + locale.toLowerCase()

    def getUrn = { "publication/$publication/locale/$locale/sku/$it".toString() }
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
    assert json.flix.deltaUrns?.sort() == [
            getUrn("a"), getUrn("b"), getUrn("c"), getUrn("d"), getUrn("e"), getUrn("f"),
            getUrn("g"), getUrn("h"), getUrn("ss-ac3_2b_2fc+ce7"), getUrn("ss-ac3_2f_2fc+ce7")
    ]

    assert json.result."package created" ==~ /http:\/\/localhost:12306\/repository\/file\/urn:thirdparty:flixmedia:flix_[a-z]{2}_[a-z]{2}_[0-9]{8}_[0-9]{6}\.zip/
    assert json.result."package archived" ==~ /http:\/\/localhost:12306\/repository\/file\/urn:archive:flix_sku:flix_[a-z]{2}_[a-z]{2}_[0-9]{8}_[0-9]{6}\.zip/
    assert json.result.stats."number of delta products" == 10
    assert json.result.stats."number of products filtered out by category" == 2
    assert json.result.stats."number of products filtered out by ean code" == 2
    assert json.result.stats."number of success" == 4
    assert json.result.stats."number of errors" == 2

    assert json.result.success?.sort() == [
            getXmlUrl("e"),
            getXmlUrl("g"),
            getXmlUrl("ss-ac3_2b_2fc+ce7"),
            getXmlUrl("ss-ac3_2f_2fc+ce7")
    ]

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
    def sheetLC = sheet.toLowerCase()
    server.get(by(uri("/repository/file/urn:global_sku:score:en_GB:${sheetLC}?processId=123"))).response(with('{"a":"1"}'), status(200))
    server.post(by(uri("/repository/file/urn:flixmedia:score:en_gb:${sheetLC}.xml?processId=123"))).response(status(200))
}

Given(~"Octopus ean code (.*) for sheet (.*)") { String eanCode, String sheet ->
    def sheetUC = sheet.toUpperCase()
    server.get(by(uri("/product/identifiers/material_name/$sheetUC"))).response(with(createIdentifiersFeed(eanCode)), status(200))
}

When(~"I request flix sheet service for process (.*) sheet (.*) no ean code") { process, sheet ->

    get("flix/product/publication/SCORE/locale/en_GB/sku/$sheet?processId=$process")
}

When(~"I request flix sheet service for process (.*) sheet (.*) ean code (.*)") { process, sheet, eanCode ->
    get("flix/product/publication/SCORE/locale/en_GB/sku/$sheet?processId=$process&eanCode=$eanCode")
}

Then(~"Flix sheet service for process (.*) sheet (.*) ean code (.*) should be done") { process, sheet, eanCode ->
    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.result == ["success"]
    assert json.product.processId == process
    assert json.product.eanCode == eanCode
}

