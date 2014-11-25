package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.service.DeltaService
import com.sony.ebs.octopus3.microservices.flix.service.PackageService
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

@Slf4j
class DeltaHandlerTest {

    StubFor mockDeltaService, mockPackageService, mockRequestValidator, mockResponseStorage
    RepoDelta delta
    def deltaResultService

    @Before
    void before() {
        mockDeltaService = new StubFor(DeltaService)
        mockRequestValidator = new StubFor(RequestValidator)
        mockPackageService = new StubFor(PackageService)
        mockResponseStorage = new StubFor(ResponseStorage)
        deltaResultService = new DeltaResultService()

        delta = new RepoDelta(type: RepoValue.flixMedia, publication: "SCORE", locale: "en_GB")
    }

    def sheetResultA = new ProductResult(inputUrn: "a", eanCode: "1", success: true, outputUrl: "http:/repo/a.xml")
    def sheetResultB = new ProductResult(inputUrn: "b", eanCode: "1", success: false, errors: ["err3", "err4"])
    def sheetResultE = new ProductResult(inputUrn: "e", eanCode: "1", success: true, outputUrl: "http:/repo/e.xml")
    def sheetResultF = new ProductResult(inputUrn: "f", eanCode: "1", success: false, errors: ["err4", "err5"])
    def sheetResultG = new ProductResult(inputUrn: "g", success: false, errors: ["no ean code"])
    def sheetResultH = new ProductResult(inputUrn: "h", success: false, errors: ["no ean code"])

    @Test
    void "success"() {
        mockPackageService.demand.with {
            processPackage(1) { RepoDelta d, DeltaResult dr ->
                assert d.publication == "SCORE"
                assert d.locale == "en_GB"
                dr.other.outputPackageUrl = "/3rdparty/flix.zip"
                dr.other.archivePackageUrl = "/archive/flix.zip"
                rx.Observable.just("xxx")
            }
        }
        mockDeltaService.demand.with {
            processDelta(1) { RepoDelta d, DeltaResult dr ->
                assert d.processId != null
                assert d.publication == "SCORE"
                assert d.locale == "en_GB"
                assert d.sdate == "s1"
                assert d.edate == "s2"

                dr.deltaUrns = ["a", "b", "c", "d", "e", "f", "g", "h"]
                dr.categoryFilteredOutUrns = ["c", "d"]
                rx.Observable.from([sheetResultF, sheetResultE, sheetResultA, sheetResultB, sheetResultG, sheetResultH])
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
                deltaService: mockDeltaService.proxyInstance(),
                packageService: mockPackageService.proxyInstance(),
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
            assert ren.result.other.outputUrls?.sort() == ["http:/repo/a.xml", "http:/repo/e.xml"]

            assert ren.result.stats."number of delta products" == 8
            assert ren.result.stats."number of products filtered out by category" == 2
            assert ren.result.stats."number of products filtered out by ean code" == 2
            assert ren.result.stats."number of successful" == 2
            assert ren.result.stats."number of unsuccessful" == 2

            assert ren.result.productErrors.size() == 4
            assert ren.result.productErrors.err3 == ["b"]
            assert ren.result.productErrors.err4?.sort() == ["b", "f"]
            assert ren.result.productErrors.err5 == ["f"]
            assert ren.result.productErrors."no ean code"?.sort() == ["g", "h"]
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
        mockDeltaService.demand.with {
            processDelta(1) { RepoDelta d, DeltaResult dr ->
                dr.errors << "error in flix flow"
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
                deltaService: mockDeltaService.proxyInstance(),
                packageService: mockPackageService.proxyInstance(),
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
        mockDeltaService.demand.with {
            processDelta(1) { RepoDelta d, DeltaResult dr ->
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
                deltaService: mockDeltaService.proxyInstance(),
                packageService: mockPackageService.proxyInstance(),
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
        mockPackageService.demand.with {
            processPackage(1) { RepoDelta d, DeltaResult dr ->
                dr.errors << "error in package flow"
                rx.Observable.just(null)
            }
        }
        mockDeltaService.demand.with {
            processDelta(1) { RepoDelta d, DeltaResult dr ->
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
                deltaService: mockDeltaService.proxyInstance(),
                packageService: mockPackageService.proxyInstance(),
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
        mockPackageService.demand.with {
            processPackage(1) { RepoDelta d, DeltaResult dr ->
                rx.Observable.just("starting").map({
                    throw new Exception("exp in package flow")
                })
            }
        }
        mockDeltaService.demand.with {
            processDelta(1) { RepoDelta d, DeltaResult dr ->
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
                deltaService: mockDeltaService.proxyInstance(),
                packageService: mockPackageService.proxyInstance(),
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
