package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

@Slf4j
class FlixFlowHandlerTest {

    StubFor mockFlixService, mockRequestValidator

    @Before
    void before() {
        mockFlixService = new StubFor(FlixService)
        mockRequestValidator = new StubFor(RequestValidator)
    }

    @Test
    void "main flow"() {
        mockFlixService.demand.with {
            flixFlow(1) { Flix flix ->
                assert flix.processId != null
                assert flix.publication == "SCORE"
                assert flix.locale == "en_GB"
                assert flix.sdate == "s1"
                assert flix.edate == "s2"
                rx.Observable.just("xxx")
            }
        }
        mockRequestValidator.demand.with {
            validateFlix(1) { Flix flix ->
                []
            }
        }

        handle(new FlixFlowHandler(flixService: mockFlixService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?sdate=s1&edate=s2"
        }).with {
            assert status.code == 200
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 200
            assert ren.flix.publication == "SCORE"
            assert ren.flix.locale == "en_GB"
            assert ren.flix.sdate == "s1"
            assert ren.flix.edate == "s2"
            assert ren.flix.processId.id != null
            assert !ren.errors
            assert ren.result == ["xxx"]
        }
    }

    @Test
    void "error in params"() {
        mockRequestValidator.demand.with {
            validateFlix(1) { Flix flix ->
                flix.errors << "error"
                flix.errors
            }
        }
        handle(new FlixFlowHandler(validator: mockRequestValidator.proxyInstance()), {
            uri "/"
        }).with {
            assert status.code == 400
            def ren = rendered(DefaultJsonRender).object
            assert ren.errors == ["error"]
            assert ren.status == 400
            assert ren.flix.processId != null
        }
    }


    @Test
    void "error in flix flow"() {
        mockFlixService.demand.with {
            flixFlow(1) { Flix flix ->
                flix.errors << "error in sheet flow"
                rx.Observable.just(null)
            }
        }
        mockRequestValidator.demand.with {
            validateFlix(1) { Flix flix ->
                []
            }
        }

        handle(new FlixFlowHandler(flixService: mockFlixService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.flix.publication == "SCORE"
            assert ren.flix.locale == "en_GB"
            assert ren.flix.processId.id != null
            assert ren.errors == ["error in sheet flow"]
            assert !ren.result
        }
    }

}
