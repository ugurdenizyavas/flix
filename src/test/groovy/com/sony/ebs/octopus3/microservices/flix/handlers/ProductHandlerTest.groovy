package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.service.ProductService
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

@Slf4j
class ProductHandlerTest {

    StubFor mockFlixSheetService, mockRequestValidator

    RepoProduct product
    def deltaResultService

    @Before
    void before() {
        mockFlixSheetService = new StubFor(ProductService)
        mockRequestValidator = new StubFor(RequestValidator)
        deltaResultService = new DeltaResultService()

        product = new RepoProduct(type: RepoValue.flixMedia, publication: "GLOBAL", locale: "fr_BE", sku: "a_2fb_2bc", processId: "123")
    }

    @Test
    void "success"() {
        mockFlixSheetService.demand.with {
            processProduct(1) { RepoProduct product, ProductResult productResult ->
                assert product.publication == "GLOBAL"
                assert product.locale == "fr_BE"
                assert product.sku == "a_2fb_2bc"
                assert product.processId == "123"

                productResult.eanCode = "ea1"
                productResult.inputUrn = "urn:global_sku:global:fr_be:a_2fb_2bc"
                productResult.inputUrl = "/repo/file/urn:global_sku:global:fr_be:a_2fb_2bc"
                productResult.outputUrn = "urn:flix:global:fr_be:a_2fb_2bc.xml"
                productResult.outputUrl = "/repo/file/urn:flix:global:fr_be:a_2fb_2bc.xml"

                rx.Observable.just("xxx")
            }
        }
        mockRequestValidator.demand.with {
            validateRepoProduct(1) {
                []
            }
        }

        handle(new ProductHandler(
                productService: mockFlixSheetService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                deltaResultService: deltaResultService), {
            pathBinding([publication: "GLOBAL", locale: "fr_BE", sku: "a_2fb_2bc"])
            uri "/?processId=123"
        }).with {
            assert status.code == 200

            def ren = rendered(DefaultJsonRender).object

            assert ren.status == 200

            assert ren.product.processId == "123"
            assert ren.product.publication == "GLOBAL"
            assert ren.product.locale == "fr_BE"
            assert ren.product.sku == "a_2fb_2bc"

            assert ren.result.eanCode == "ea1"
            assert ren.result.inputUrn == "urn:global_sku:global:fr_be:a_2fb_2bc"
            assert ren.result.inputUrl == "/repo/file/urn:global_sku:global:fr_be:a_2fb_2bc"
            assert ren.result.outputUrn == "urn:flix:global:fr_be:a_2fb_2bc.xml"
            assert ren.result.outputUrl == "/repo/file/urn:flix:global:fr_be:a_2fb_2bc.xml"

            assert !ren.errors
        }
    }

    @Test
    void "error in params"() {
        mockRequestValidator.demand.with {
            validateRepoProduct(1) {
                ["error"]
            }
        }
        handle(new ProductHandler(
                validator: mockRequestValidator.proxyInstance(),
                deltaResultService: deltaResultService), {
            pathBinding([publication: "GLOBAL", locale: "fr_BE", sku: "a_2fb_2bc"])
            uri "/"
        }).with {
            assert status.code == 400

            def ren = rendered(DefaultJsonRender).object

            assert ren.status == 400

            assert ren.product.publication == "GLOBAL"
            assert ren.product.locale == "fr_BE"
            assert ren.product.sku == "a_2fb_2bc"

            assert !ren.result

            assert ren.errors == ["error"]
        }
    }

    @Test
    void "error in flow"() {
        mockFlixSheetService.demand.with {
            processProduct(1) { RepoProduct product, ProductResult productResult ->

                productResult.inputUrn = "urn:global_sku:global:fr_be:a_2fb_2bc"
                productResult.inputUrl = "/repo/file/urn:global_sku:global:fr_be:a_2fb_2bc"
                productResult.errors << "error in sheet flow"

                rx.Observable.just(null)
            }
        }
        mockRequestValidator.demand.with {
            validateRepoProduct(1) {
                []
            }
        }

        handle(new ProductHandler(
                productService: mockFlixSheetService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                deltaResultService: deltaResultService), {
            pathBinding([publication: "GLOBAL", locale: "fr_BE", sku: "a_2fb_2bc"])
            uri "/"
        }).with {
            assert status.code == 500
            def ren = rendered(DefaultJsonRender).object

            assert ren.status == 500

            assert ren.product.publication == "GLOBAL"
            assert ren.product.locale == "fr_BE"
            assert ren.product.sku == "a_2fb_2bc"

            assert ren.errors == ["error in sheet flow"]

            assert ren.result.inputUrn == "urn:global_sku:global:fr_be:a_2fb_2bc"
            assert ren.result.inputUrl == "/repo/file/urn:global_sku:global:fr_be:a_2fb_2bc"
        }
    }

    @Test
    void "exception in flow"() {
        mockFlixSheetService.demand.with {
            processProduct(1) { RepoProduct product, ProductResult productResult ->

                productResult.inputUrn = "urn:global_sku:global:fr_be:a_2fb_2bc"
                productResult.inputUrl = "/repo/file/urn:global_sku:global:fr_be:a_2fb_2bc"

                rx.Observable.just("starting").map({
                    throw new Exception("exp in sheet flow")
                })
            }
        }
        mockRequestValidator.demand.with {
            validateRepoProduct(1) {
                []
            }
        }

        handle(new ProductHandler(
                productService: mockFlixSheetService.proxyInstance(),
                validator: mockRequestValidator.proxyInstance(),
                deltaResultService: deltaResultService), {
            pathBinding([publication: "GLOBAL", locale: "fr_BE", sku: "a_2fb_2bc"])
            uri "/"
        }).with {
            assert status.code == 500

            def ren = rendered(DefaultJsonRender).object

            assert ren.status == 500

            assert ren.product.publication == "GLOBAL"
            assert ren.product.locale == "fr_BE"
            assert ren.product.sku == "a_2fb_2bc"

            assert ren.errors == ["exp in sheet flow"]

            assert ren.result.inputUrn == "urn:global_sku:global:fr_be:a_2fb_2bc"
            assert ren.result.inputUrl == "/repo/file/urn:global_sku:global:fr_be:a_2fb_2bc"
        }
    }
}
