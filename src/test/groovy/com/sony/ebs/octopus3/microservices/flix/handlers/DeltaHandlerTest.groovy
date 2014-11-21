package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.ProductServiceResult
import com.sony.ebs.octopus3.microservices.flix.service.PackageService
import com.sony.ebs.octopus3.microservices.flix.service.DeltaService
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

@Slf4j
class DeltaHandlerTest {

    StubFor mockFlixService, mockFlixPackageService, mockRequestValidator, mockResponseStorage
    RepoDelta delta
    Flix flix
    def deltaResultService

    @Before
    void before() {
        mockFlixService = new StubFor(DeltaService)
        mockRequestValidator = new StubFor(RequestValidator)
        mockFlixPackageService = new StubFor(PackageService)
        mockResponseStorage = new StubFor(ResponseStorage)
        deltaResultService = new DeltaResultService()

        delta = new RepoDelta(type: RepoValue.flixMedia, publication: "SCORE", locale: "en_GB")
        flix = new Flix()
    }

    def sheetResultA = new ProductServiceResult(jsonUrn: "a", success: true, xmlFileUrl: "http:/repo/a.xml")
    def sheetResultB = new ProductServiceResult(jsonUrn: "b", success: false, errors: ["err3", "err4"])
    def sheetResultE = new ProductServiceResult(jsonUrn: "e", success: true, xmlFileUrl: "http:/repo/e.xml")
    def sheetResultF = new ProductServiceResult(jsonUrn: "f", success: false, errors: ["err4", "err5"])

    @Test
    void "success"() {
        mockFlixPackageService.demand.with {
            packageFlow(1) { RepoDelta d, Flix f ->
                assert d.publication == "SCORE"
                assert d.locale == "en_GB"
                f.outputPackageUrl = "/3rdparty/flix.zip"
                f.archivePackageUrl = "/archive/flix.zip"
                rx.Observable.just("xxx")
            }
        }
        mockFlixService.demand.with {
            processDelta(1) { RepoDelta d, Flix f ->
                assert d.processId != null
                assert d.publication == "SCORE"
                assert d.locale == "en_GB"
                assert d.sdate == "s1"
                assert d.edate == "s2"

                d.deltaUrns = ["a", "b", "c", "d", "e", "f"]
                f.categoryFilteredOutUrns = ["c", "d"]
                rx.Observable.from([sheetResultF, sheetResultE, sheetResultA, sheetResultB])
            }
        }
        mockRequestValidator.demand.with {
            validateDelta(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockFlixService.proxyInstance(),
                packageService: mockFlixPackageService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService
        ), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/?sdate=s1&edate=s2"
        }).with {
            assert status.code == 200
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 200
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.delta.sdate == "s1"
            assert ren.delta.edate == "s2"
            assert ren.delta.processId.id != null
            assert !ren.errors

            assert ren.result.other."package created" == "/3rdparty/flix.zip"
            assert ren.result.other."package archived" == "/archive/flix.zip"
            assert ren.result.other.xmlFileUrls?.sort() == ["http:/repo/a.xml", "http:/repo/e.xml"]

            assert ren.result.stats."number of delta products" == 6
            assert ren.result.stats."number of products filtered out by category" == 2
            assert ren.result.stats."number of successful" == 2
            assert ren.result.stats."number of unsuccessful" == 2

            assert ren.result.productErrors.size() == 3
            assert ren.result.productErrors.err3 == ["b"]
            assert ren.result.productErrors.err4?.sort() == ["b", "f"]
            assert ren.result.productErrors.err5 == ["f"]

        }
    }

    @Test
    void "error in params"() {
        mockRequestValidator.demand.with {
            validateDelta(1) {
                ["error"]
            }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }
        handle(new DeltaHandler(
                validator: mockRequestValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService), {
            uri "/"
        }).with {
            assert status.code == 400
            def ren = rendered(DefaultJsonRender).object
            assert ren.errors == ["error"]
            assert ren.status == 400
            assert ren.delta.processId != null
        }
    }

    @Test
    void "error in flix flow"() {
        mockFlixService.demand.with {
            processDelta(1) { RepoDelta d, Flix f ->
                d.errors << "error in flix flow"
                rx.Observable.just(null)
            }
        }
        mockRequestValidator.demand.with {
            validateDelta(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockFlixService.proxyInstance(),
                packageService: mockFlixPackageService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.delta.processId.id != null
            assert ren.errors == ["error in flix flow"]
            assert !ren.result
        }
    }

    @Test
    void "exception in flix flow"() {
        mockFlixService.demand.with {
            processDelta(1) { RepoDelta d, Flix f ->
                rx.Observable.just("starting").map({
                    throw new Exception("exp in flix flow")
                })
            }
        }
        mockRequestValidator.demand.with {
            validateDelta(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockFlixService.proxyInstance(),
                packageService: mockFlixPackageService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.delta.processId.id != null
            assert ren.errors == ["exp in flix flow"]
            assert !ren.result
        }
    }

    @Test
    void "error in package flow"() {
        mockFlixPackageService.demand.with {
            packageFlow(1) { RepoDelta d, Flix f ->
                d.errors << "error in package flow"
                rx.Observable.just(null)
            }
        }
        mockFlixService.demand.with {
            processDelta(1) { RepoDelta d, Flix f ->
                rx.Observable.from([sheetResultF, sheetResultE, sheetResultA, sheetResultB])
            }
        }
        mockRequestValidator.demand.with {
            validateDelta(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockFlixService.proxyInstance(),
                packageService: mockFlixPackageService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.delta.processId.id != null
            assert ren.errors == ["error in package flow"]
            assert !ren.result
        }
    }

    @Test
    void "exception in package flow"() {
        mockFlixPackageService.demand.with {
            packageFlow(1) { RepoDelta d, Flix f ->
                rx.Observable.just("starting").map({
                    throw new Exception("exp in package flow")
                })
            }
        }
        mockFlixService.demand.with {
            processDelta(1) { RepoDelta d, Flix f ->
                rx.Observable.from([sheetResultF, sheetResultE, sheetResultA, sheetResultB])
            }
        }
        mockRequestValidator.demand.with {
            validateDelta(1) { [] }
        }

        mockResponseStorage.demand.with {
            store(1) { String st1, List list1, String st2 ->
                true
            }
        }

        handle(new DeltaHandler(
                deltaService: mockFlixService.proxyInstance(),
                packageService: mockFlixPackageService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                responseStorage: mockResponseStorage.proxyInstance(),
                deltaResultService: deltaResultService), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object
            assert ren.status == 500
            assert ren.delta.publication == "SCORE"
            assert ren.delta.locale == "en_GB"
            assert ren.delta.processId.id != null
            assert ren.errors == ["exp in package flow"]
            assert !ren.result
        }
    }
}
