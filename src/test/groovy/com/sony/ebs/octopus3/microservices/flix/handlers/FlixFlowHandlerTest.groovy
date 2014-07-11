package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.services.FlixService
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

@Slf4j
class FlixFlowHandlerTest {

    StubFor mockFlixService

    @Before
    void before() {
        mockFlixService = new StubFor(FlixService)
    }

    @Test
    void "main flow"() {
        mockFlixService.demand.with {
            flixFlow(1) { processId, publication, locale ->
                assert processId != null
                assert publication == "SCORE"
                assert locale == "en_GB"
                log.info "assertions finished"
                rx.Observable.from("xxx")
            }
        }

        handle(new FlixFlowHandler(flixService: mockFlixService.proxyInstance()), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 202
            assert rendered(DefaultJsonRender).object.message == "flix media generation started"
            assert rendered(DefaultJsonRender).object.publication == "SCORE"
            assert rendered(DefaultJsonRender).object.locale == "en_GB"
            assert rendered(DefaultJsonRender).object.status == 202
            assert rendered(DefaultJsonRender).object.processId != null
        }
    }

}
