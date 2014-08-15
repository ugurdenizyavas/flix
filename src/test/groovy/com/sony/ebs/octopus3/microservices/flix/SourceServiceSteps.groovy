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

/*
* ******************** FLIX MEDIA GENERATION SERVICE ***********************************************************
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
        "urn:global_sku:$publicationLC:$localeLC:d"
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
                <displayName><![CDATA[TV & home cinema]]></displayName>
                <nodes>
                    <node>
                        <name><![CDATA[HCS Home Cinema Projectors]]></name>
                        <displayName><![CDATA[Projectors]]></displayName>
                        <products>
                            <product><![CDATA[a]]></product>
                        </products>
                    </node>
                    <node>
                        <name><![CDATA[HCS Home Cinema Projectors]]></name>
                        <displayName><![CDATA[Projectors]]></displayName>
                        <products>
                            <product><![CDATA[c]]></product>
                            <product><![CDATA[d]]></product>
                        </products>
                    </node>
                </nodes>
            </node>
        </nodes>
    </node>
</ProductHierarchy>
"""

    String SHEET_FEED_WITH_ERRORS = '''
{
    "errors" : ["err1", "err2"]
}
'''

    server.get(by(uri("/repository/delta/urn:global_sku:$publicationLC:$localeLC"))).response(DELTA_FEED)
    server.delete(by(uri("/repository/file/urn:flixmedia:$publicationLC:$localeLC"))).response("")
    server.post(by(uri("/repository/file/urn:flixmedia:last_modified:$publicationLC:$localeLC"))).response("")

    server.get(by(uri("/product/publications/$publication/locales/$locale/hierarchies/category"))).response(CATEGORY_FEED)
    server.post(by(uri("/repository/file/urn:flixmedia:$publicationLC:$localeLC:category"))).response("")

    server.get(by(uri("/flix/sheet/urn:global_sku:$publicationLC:$localeLC:a"))).response('{}')
    server.get(by(uri("/flix/sheet/urn:global_sku:$publicationLC:$localeLC:c"))).response('{}')
    server.get(by(uri("/flix/sheet/urn:global_sku:$publicationLC:$localeLC:d"))).response(with(SHEET_FEED_WITH_ERRORS), status(500))
}

When(~"I request flix media generation for publication (.*) locale (.*)") { publication, locale ->
    get("flix/delta/publication/$publication/locale/$locale?sdate=2014-07-09T00:00:00.000Z&edate=2014-07-12T00:00:00.000Z")
}

Then(~"Flix media generation for publication (.*) locale (.*) should be done") { publication, locale ->
    def publicationLC = publication.toLowerCase()
    def localeLC = locale.toLowerCase()

    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.flix.publication == publication
    assert json.flix.locale == locale
    assert json.flix.sdate == "2014-07-09T00:00:00.000Z"
    assert json.flix.edate == "2014-07-12T00:00:00.000Z"
    assert json.flix.processId
    assert json.flix.categoryFilteredOutUrns == ["urn:global_sku:$publicationLC:$localeLC:b"]
    assert json.flix.deltaUrns == [
            "urn:global_sku:$publicationLC:$localeLC:a",
            "urn:global_sku:$publicationLC:$localeLC:b",
            "urn:global_sku:$publicationLC:$localeLC:c",
            "urn:global_sku:$publicationLC:$localeLC:d"
    ]

    assert json.result.stats."number of delta products" == 4
    assert json.result.stats."number of products filtered out by category" == 1
    assert json.result.stats."number of success" == 2
    assert json.result.stats."number of errors" == 1

    assert json.result.list.size() == 3
    assert json.result.list.contains([statusCode: 200, success: true, urn: "urn:global_sku:$publicationLC:$localeLC:a".toString()])
    assert json.result.list.contains([statusCode: 200, success: true, urn: "urn:global_sku:$publicationLC:$localeLC:c".toString()])
    assert json.result.list.contains([statusCode: 500, success: false, urn: "urn:global_sku:$publicationLC:$localeLC:d".toString(), errors: ["err1", "err2"]])
}

Given(~"Flix json for sheet (.*)") { String sheet ->
    server.request(by(uri("/repository/file/urn:global_sku:score:en_GB:$sheet")))
            .response('{"a":"1"}')

    server.request(by(uri("/product/eancode/${sheet.toUpperCase()}")))
            .response('<eancodes><eancode material="$sheet" code="4905524328974"/></eancodes>')

    server.post(by(uri("/repository/file/urn:flixmedia:score:en_gb:${sheet}.xml")))
            .response('done')
}

When(~"I request flix sheet import for process (.*) sheet (.*)") { process, sheet ->
    get("flix/sheet/urn:global_sku:score:en_GB:$sheet?processId=$process")
}

Then(~"Flix sheet import for process (.*) sheet (.*) should be done") { process, sheet ->
    assert response.statusCode == 200
    def json = parseJson(response)
    assert json.status == 200
    assert json.result == ["success"]
    assert json.flixSheet.processId == process
    assert json.flixSheet.urnStr == "urn:global_sku:score:en_GB:$sheet"
}
