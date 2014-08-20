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
                rx.Observable.just("xxx")
            }
        }
        mockRequestValidator.demand.with {
            validateFlixSheet(1) { FlixSheet flixSheet ->
                []
            }
        }

        handle(new FlixSheetFlowHandler(flixSheetService: mockFlixSheetService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([urn: URN])
            uri "/?processId=$PROCESS_ID&eanCode=123"
        }).with {
            assert status.code == 200
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 200
            assert ren.flixSheet.processId == PROCESS_ID
            assert ren.flixSheet.urnStr == URN
            assert ren.flixSheet.eanCode == "123"
            assert !ren.errors
            assert ren.result == ["xxx"]
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
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 400
            assert ren.flixSheet.processId == PROCESS_ID
            assert ren.flixSheet.urnStr == URN
            assert ren.errors == ["error"]
        }
    }

    @Test
    void "error in sheet flow"() {
        mockFlixSheetService.demand.with {
            sheetFlow(1) { FlixSheet flixSheet ->
                flixSheet.errors << "error in sheet flow"
                rx.Observable.just(null)
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
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.flixSheet.processId == PROCESS_ID
            assert ren.flixSheet.urnStr == URN
            assert ren.errors == ["error in sheet flow"]
            assert !ren.result
        }
    }

    @Test
    void "exception in sheet flow"() {
        mockFlixSheetService.demand.with {
            sheetFlow(1) { FlixSheet flixSheet ->
                rx.Observable.just("starting").map({
                    throw new Exception("exp in sheet flow")
                })
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
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.flixSheet.processId == PROCESS_ID
            assert ren.flixSheet.urnStr == URN
            assert ren.errors == ["exp in sheet flow"]
            assert !ren.result
        }
    }
}
