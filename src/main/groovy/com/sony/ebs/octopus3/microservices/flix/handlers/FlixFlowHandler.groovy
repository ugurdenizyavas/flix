package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.FlixSheetServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixPackageService
import com.sony.ebs.octopus3.microservices.flix.services.basic.FlixService
import com.sony.ebs.octopus3.microservices.flix.validators.RequestValidator
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity", category = "activity")
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
                render json(status: 400, errors: errors, flix: flix)
            } else {
                def startTime = new DateTime()
                flixService.flixFlow(flix).finallyDo({
                    if (flix.errors) {
                        activity.error "finished $flix with errors: $flix.errors"
                        def endTime = new DateTime()
                        def timeStats = HandlerUtil.getTimeStats(startTime, endTime)
                        response.status(500)
                        render json(status: 500, timeStats: timeStats, errors: flix.errors, flix: flix)
                    } else {
                        handleFlixPackage(context, flix, sheetServiceResults, startTime)
                    }
                }).subscribe({
                    sheetServiceResults << it
                    activity.debug "flix flow emitted: $it"
                }, { e ->
                    flix.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $flix", e
                })
            }

        }
    }

    void handleFlixPackage(GroovyContext context, Flix flix, List sheetServiceResults, DateTime startTime) {
        context.with {
            flixPackageService.packageFlow(flix).finallyDo({
                def endTime = new DateTime()
                def timeStats = HandlerUtil.getTimeStats(startTime, endTime)
                if (flix.errors) {
                    activity.error "finished $flix with errors: $flix.errors"
                    response.status(500)
                    render json(status: 500, timeStats: timeStats, errors: flix.errors, flix: flix)
                } else {
                    activity.info "finished $flix with success"
                    response.status(200)
                    render json(status: 200, timeStats: timeStats, result: createFlixResult(flix, sheetServiceResults), flix: flix)
                }
            }).subscribe({
                activity.debug "$flix emited: $it"
            }, { e ->
                flix.errors << HandlerUtil.getErrorMessage(e)
                activity.error "error in $flix", e
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
                "package created": flix.outputPackageUrl,
                stats            : [
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
                success          : createSuccess(),
                errors           : createErrors()
        ]
    }

}
