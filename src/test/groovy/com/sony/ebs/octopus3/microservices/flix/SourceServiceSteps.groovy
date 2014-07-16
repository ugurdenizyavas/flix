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

When(~"I request flix media generation for publication (.*) locale (.*)") { publication, locale ->
    get("flix/delta/publication/$publication/locale/$locale?sdate=2014-07-09T00:00:00.000Z&edate=2014-07-12T00:00:00.000Z")
}

Then(~"Flix media generation for publication (.*) locale (.*) should be started") { publication, locale ->
    assert response.statusCode == 202
    def json = parseJson(response)
    assert json.status == 202
    assert json.message == "flix started"
    assert json.flix.publication == publication
    assert json.flix.locale == locale
    assert json.flix.sdate == "2014-07-09T00:00:00.000Z"
    assert json.flix.edate == "2014-07-12T00:00:00.000Z"
    assert json.flix.processId
}

When(~"I request flix sheet import for process (.*) sheet (.*)") { process, sheet ->
    get("flix/sheet/urn:flix:score:en_GB:$sheet?processId=$process")
}

Then(~"Flix sheet import for process (.*) sheet (.*) should be started") { process, sheet ->
    assert response.statusCode == 202
    def json = parseJson(response)
    assert json.status == 202
    assert json.message == "flixSheet started"
    assert json.flixSheet.processId == process
    assert json.flixSheet.urnStr == "urn:flix:score:en_GB:$sheet"
    assert json.flixSheet.processId
}
