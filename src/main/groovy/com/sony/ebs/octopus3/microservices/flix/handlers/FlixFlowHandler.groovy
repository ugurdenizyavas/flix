package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheetServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixPackageService
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity")
@Component
@org.springframework.context.annotation.Lazy
class FlixFlowHandler extends GroovyHandler {

    @Autowired
    FlixService flixService

    @Autowired
    FlixPackageService flixPackageService

    @Autowired
    RequestValidator validator


    @Override
    protected void handle(GroovyContext context) {
        context.with {
            Flix flix = new Flix(processId: new ProcessIdImpl(), publication: pathTokens.publication,
                    locale: pathTokens.locale, sdate: request.queryParams.sdate, edate: request.queryParams.edate)
            activity.info "starting $flix"

            List sheetServiceResults = []
            List errors = validator.validateFlix(flix)
            if (errors) {
                activity.error "error validating $flix : $errors"
                response.status(400)
                render json(status: 400, flix: flix, errors: errors)
            } else {
                flixService.flixFlow(flix).subscribe({
                    sheetServiceResults << it
                    activity.info "sheet result: $it"
                }, { e ->
                    flix.errors << e.message ?: e.cause?.message
                    activity.error "error in $flix", e
                }, {
                    if (flix.errors) {
                        response.status(500)
                        render json(status: 500, flix: flix, errors: flix.errors)
                    } else {
                        handleFlixPackage(context, flix, sheetServiceResults)
                    }
                })
            }

        }
    }

    void handleFlixPackage(GroovyContext context, Flix flix, List sheetServiceResults) {
        context.with {
            flixPackageService.packageFlow(flix).subscribe({
                activity.info "$flix finished: $it"
            }, { e ->
                flix.errors << e.message ?: e.cause?.message
                activity.error "error in $flix", e
            }, {
                if (flix.errors) {
                    response.status(500)
                    render json(status: 500, flix: flix, errors: flix.errors)
                } else {
                    response.status(200)
                    render json(status: 200, flix: flix, result: createFlixResult(flix, sheetServiceResults))
                }
            })
        }
    }

    Map createFlixResult(Flix flix, List sheetServiceResults) {
        def createSuccess = {
            sheetServiceResults.findAll({ it.success }).collect({ it.xmlFileUrl })
        }
        def createErrors = {
            Map errorMap = [:]
            sheetServiceResults.findAll({ !it.success }).each { FlixSheetServiceResult serviceResult ->
                serviceResult.errors.each { error ->
                    if (errorMap[error] == null) errorMap[error] = []
                    errorMap[error] << serviceResult.jsonUrn
                }
            }
            errorMap
        }
        [
                stats  : [
                        "number of delta products"                   : flix.deltaUrns?.size(),
                        "number of products filtered out by category": flix.categoryFilteredOutUrns?.size(),
                        "number of products filtered out by ean code": flix.eanCodeFilteredOutUrns?.size(),
                        "number of success"                          : sheetServiceResults?.findAll({
                            it.success
                        }).size(),
                        "number of errors"                           : sheetServiceResults?.findAll({
                            !it.success
                        }).size()
                ],
                success: createSuccess(),
                errors : createErrors()
        ]
    }

}
