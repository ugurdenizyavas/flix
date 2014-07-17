package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.microservices.flix.model.FlixPackage
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixPackageService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.mock.interceptor.StubFor
import groovy.util.logging.Slf4j
import org.junit.Before
import org.junit.Test
import ratpack.jackson.internal.DefaultJsonRender

import static ratpack.groovy.test.GroovyUnitTest.handle

@Slf4j
class FlixPackageFlowHandlerTest {

    StubFor mockFlixPackageService, mockRequestValidator

    @Before
    void before() {
        mockFlixPackageService = new StubFor(FlixPackageService)
        mockRequestValidator = new StubFor(RequestValidator)
    }

    @Test
    void "main flow"() {
        mockFlixPackageService.demand.with {
            packageFlow(1) { FlixPackage flixPackage ->
                assert flixPackage.publication == "SCORE"
                assert flixPackage.locale == "en_GB"
                rx.Observable.from("xxx")
            }
        }
        mockRequestValidator.demand.with {
            validateFlixPackage(1) { FlixPackage flixPackage ->
                []
            }
        }

        handle(new FlixPackageFlowHandler(flixPackageService: mockFlixPackageService.proxyInstance(), validator: mockRequestValidator.proxyInstance()), {
            pathBinding([publication: "SCORE", locale: "en_GB"])
            uri "/"
        }).with {
            assert status.code == 202
            def ren = rendered(DefaultJsonRender).object
            assert ren.message == "flixPackage started"
            assert ren.status == 202
            assert ren.flixPackage.publication == "SCORE"
            assert ren.flixPackage.locale == "en_GB"
            assert !ren.errors
            log.info "result assertions finished for $ren.flixPackage"
        }
    }

    @Test
    void "error in params"() {
        mockRequestValidator.demand.with {
            validateFlixPackage(1) { FlixPackage flixPackage ->
                ["error"]
            }
        }
        handle(new FlixPackageFlowHandler(validator: mockRequestValidator.proxyInstance()), {
            uri "/"
        }).with {
            assert status.code == 400
            def ren = rendered(DefaultJsonRender).object
            assert ren.errors == ["error"]
            assert ren.status == 400
            assert ren.flixPackage != null
        }
    }
}
