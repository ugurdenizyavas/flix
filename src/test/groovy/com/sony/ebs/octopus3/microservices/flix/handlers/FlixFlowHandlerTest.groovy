package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheetServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixPackageService
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

    StubFor mockFlixService, mockFlixPackageService, mockRequestValidator

    @Before
    void before() {
        mockFlixService = new StubFor(FlixService)
        mockRequestValidator = new StubFor(RequestValidator)
        mockFlixPackageService = new StubFor(FlixPackageService)
    }

    def sheetResultA = new FlixSheetServiceResult(jsonUrn: "a", success: true, xmlFileUrl: "http:/repo/a.xml")
    def sheetResultB = new FlixSheetServiceResult(jsonUrn: "b", success: false, errors: ["err3", "err4"])
    def sheetResultE = new FlixSheetServiceResult(jsonUrn: "e", success: true, xmlFileUrl: "http:/repo/e.xml")
    def sheetResultF = new FlixSheetServiceResult(jsonUrn: "f", success: false, errors: ["err4", "err5"])

    @Test
    void "success"() {
        mockFlixPackageService.demand.with {
            packageFlow(1) { Flix flix ->
                assert flix.publication == "SCORE"
                assert flix.locale == "en_GB"
                rx.Observable.just("xxx")
            }
        }
        mockFlixService.demand.with {
            flixFlow(1) { Flix flix ->
                assert flix.processId != null
                assert flix.publication == "SCORE"
                assert flix.locale == "en_GB"
                assert flix.sdate == "s1"
                assert flix.edate == "s2"

                flix.deltaUrns = ["a", "b", "c", "d", "e", "f"]
                flix.categoryFilteredOutUrns = ["c", "d"]
                rx.Observable.from([sheetResultF, sheetResultE, sheetResultA, sheetResultB])
            }
        }
        mockRequestValidator.demand.with {
            validateFlix(1) { Flix flix ->
                []
            }
        }

        handle(new FlixFlowHandler(flixService: mockFlixService.proxyInstance(),
                flixPackageService: mockFlixPackageService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance()), {
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

            assert ren.result.stats."number of delta products" == 6
            assert ren.result.stats."number of products filtered out by category" == 2
            assert ren.result.stats."number of success" == 2
            assert ren.result.stats."number of errors" == 2

            assert ren.result.success?.sort() == ["http:/repo/a.xml", "http:/repo/e.xml"]

            assert ren.result.errors.size() == 3
            assert ren.result.errors.err3 == ["b"]
            assert ren.result.errors.err4?.sort() == ["b", "f"]
            assert ren.result.errors.err5 == ["f"]
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

        handle(new FlixFlowHandler(flixService: mockFlixService.proxyInstance(),
                flixPackageService: mockFlixPackageService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance()), {
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

    @Test
    void "error in package flow"() {
        mockFlixPackageService.demand.with {
            packageFlow(1) { Flix flix ->
                flix.errors << "error in package flow"
                rx.Observable.just(null)
            }
        }
        mockFlixService.demand.with {
            flixFlow(1) { Flix flix ->
                rx.Observable.from([sheetResultF, sheetResultE, sheetResultA, sheetResultB])
            }
        }
        mockRequestValidator.demand.with {
            validateFlix(1) { Flix flix ->
                []
            }
        }

        handle(new FlixFlowHandler(flixService: mockFlixService.proxyInstance(),
                flixPackageService: mockFlixPackageService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance()), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.flix.publication == "SCORE"
            assert ren.flix.locale == "en_GB"
            assert ren.flix.processId.id != null
            assert ren.errors == ["error in package flow"]
            assert !ren.result
        }
    }
}
