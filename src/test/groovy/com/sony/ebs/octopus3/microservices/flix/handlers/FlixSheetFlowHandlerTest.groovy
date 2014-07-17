package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixSheetService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

@Slf4j
class FlixSheetFlowHandlerTest {

    final static String URN = "urn:flix:score:en_gb"
    final static String PROCESS_ID = "123"

    StubFor mockFlixSheetService, mockRequestValidator

    @Before
    void before() {
        mockFlixSheetService = new StubFor(FlixSheetService)
        mockRequestValidator = new StubFor(RequestValidator)
    }

    @Test
    void "main flow"() {

        mockFlixSheetService.demand.with {
            sheetFlow(1) { FlixSheet flixSheet ->
                assert flixSheet.processId == PROCESS_ID
                assert flixSheet.urnStr == URN
                log.info "service assertions finished"
                rx.Observable.from("xxx")
            }
        }
        mockRequestValidator.demand.with {
            validateFlixSheet(1) { FlixSheet flixSheet ->
                []
            }
        }

        handle(new FlixSheetFlowHandler(flixSheetService: mockFlixSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?processId=$PROCESS_ID"
        }).with {
            assert status.code == 202
            def result = rendered(DefaultJsonRender).object
            assert result.message == "flixSheet started"
            assert result.status == 202
            assert result.flixSheet.processId == PROCESS_ID
            assert result.flixSheet.urnStr == URN
            assert !result.errors
            log.info "result assertions finished for $result.flixSheet"
        }
    }

    @Test
    void "error in params"() {
        mockRequestValidator.demand.with {
            validateFlixSheet(1) { FlixSheet flixSheet ->
                assert flixSheet.processId == PROCESS_ID
                assert flixSheet.urnStr == URN
                log.info "service assertions finished"
                ["error"]
            }
        }
        handle(new FlixSheetFlowHandler(validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?processId=$PROCESS_ID"
        }).with {
            assert status.code == 400
            def result = rendered(DefaultJsonRender).object
            assert result.errors == ["error"]
            assert result.status == 400
            assert result.flixSheet.processId == PROCESS_ID
            assert result.flixSheet.urnStr == URN
            log.info "result assertions finished for $result.flixSheet"
        }
    }
}
