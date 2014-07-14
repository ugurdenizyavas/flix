package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.services.FlixService
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
                log.info "service assertions finished"
                rx.Observable.from("xxx")
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
            assert status.code == 202
            def result = rendered(DefaultJsonRender).object
            assert result.message == "flix started"
            assert result.status == 202
            assert result.flix.publication == "SCORE"
            assert result.flix.locale == "en_GB"
            assert result.flix.sdate == "s1"
            assert result.flix.edate == "s2"
            assert result.flix.processId.id != null
            assert !result.errors
            log.info "result assertions finished for $result.flix"
        }
    }

    @Test
    void "error in params"() {
        mockRequestValidator.demand.with {
            validateFlix(1) { Flix flix ->
                ["error"]
            }
        }
        handle(new FlixFlowHandler(validator: mockRequestValidator.proxyInstance()), {
            uri "/"
        }).with {
            assert status.code == 400
            def result = rendered(DefaultJsonRender).object
            assert result.errors == ["error"]
            assert result.status == 400
            assert result.flix.processId != null
        }
    }
}
