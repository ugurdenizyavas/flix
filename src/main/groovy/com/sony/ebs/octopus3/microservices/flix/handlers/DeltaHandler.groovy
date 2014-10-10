package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.ProductServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.basic.PackageService
import com.sony.ebs.octopus3.microservices.flix.services.basic.DeltaService
import groovy.json.JsonOutput
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
class DeltaHandler extends GroovyHandler {

    @Autowired
    DeltaService deltaService

    @Autowired
    PackageService packageService

    @Autowired
    RequestValidator validator

    @Autowired
    ResponseStorage responseStorage

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            RepoDelta delta = new RepoDelta(type: DeltaType.flixMedia, processId: new ProcessIdImpl(), publication: pathTokens.publication,
                    locale: pathTokens.locale, sdate: request.queryParams.sdate, edate: request.queryParams.edate)
            activity.info "starting {}", delta

            List productServiceResults = []
            List errors = validator.validateRepoDelta(delta)
            if (errors) {
                activity.error "error validating {} : {}", delta, errors
                response.status(400)

                def jsonResponse = json(status: 400, errors: errors, delta: delta)

                responseStorage.store(delta.processId.id, ["flix", "delta", delta.publication, delta.locale, delta.processId.id], JsonOutput.toJson(jsonResponse.object))

                render jsonResponse
            } else {
                def startTime = new DateTime()
                def flix = new Flix()
                deltaService.processDelta(delta, flix).finallyDo({
                    if (delta.errors) {
                        activity.error "finished {} with errors: {}", delta, delta.errors
                        def endTime = new DateTime()
                        def timeStats = HandlerUtil.getTimeStats(startTime, endTime)
                        response.status(500)

                        def jsonResponse = json(status: 500, timeStats: timeStats, errors: delta.errors, delta: delta)

                        responseStorage.store(delta.processId.id, ["flix", "delta", delta.publication, delta.locale, delta.processId.id], JsonOutput.toJson(jsonResponse.object))

                        render jsonResponse
                    } else {
                        handleFlixPackage(context, delta, flix, productServiceResults, startTime)
                    }
                }).subscribe({
                    productServiceResults << it
                    activity.debug "flix flow emitted: {}", it
                }, { e ->
                    delta.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $delta", e
                })
            }

        }
    }

    void handleFlixPackage(GroovyContext context, RepoDelta delta, Flix flix, List productServiceResults, DateTime startTime) {
        context.with {
            packageService.packageFlow(delta, flix).finallyDo({
                def endTime = new DateTime()
                def timeStats = HandlerUtil.getTimeStats(startTime, endTime)
                if (delta.errors) {
                    activity.error "finished {} with errors: {}", delta, delta.errors
                    response.status(500)

                    def jsonResponse = json(status: 500, timeStats: timeStats, errors: delta.errors, delta: delta)

                    responseStorage.store(delta.processId.id, ["flix", "delta", delta.publication, delta.locale, delta.processId.id], JsonOutput.toJson(jsonResponse.object))

                    render jsonResponse
                } else {
                    activity.info "finished {} with success", delta
                    response.status(200)

                    def jsonResponse = json(status: 200, timeStats: timeStats, result: createDeltaResult(delta, flix, productServiceResults), delta: delta)

                    responseStorage.store(delta.processId.id, ["flix", "delta", delta.publication, delta.locale, delta.processId.id], JsonOutput.toJson(jsonResponse.object))

                    render jsonResponse
                }
            }).subscribe({
                activity.debug "{} emitted: {}", delta, it
            }, { e ->
                delta.errors << HandlerUtil.getErrorMessage(e)
                activity.error "error in $delta", e
            })
        }
    }

    Map createDeltaResult(RepoDelta delta, Flix flix, List sheetServiceResults) {
        def createSuccess = {
            sheetServiceResults.findAll({ it.success }).collect({ it.xmlFileUrl })
        }
        def createErrors = {
            Map errorMap = [:]
            sheetServiceResults.findAll({ !it.success }).each { ProductServiceResult serviceResult ->
                serviceResult.errors.each { error ->
                    if (errorMap[error] == null) errorMap[error] = []
                    errorMap[error] << serviceResult.jsonUrn
                }
            }
            errorMap
        }
        [
                "package created"      : flix.outputPackageUrl,
                "package archived"     : flix.archivePackageUrl,
                stats                  : [
                        "number of delta products"                   : delta.deltaUrns?.size(),
                        "number of products filtered out by category": flix.categoryFilteredOutUrns?.size(),
                        "number of products filtered out by ean code": flix.eanCodeFilteredOutUrns?.size(),
                        "number of success"                          : sheetServiceResults?.findAll({
                            it.success
                        }).size(),
                        "number of errors"                           : sheetServiceResults?.findAll({
                            !it.success
                        }).size()
                ],
                success                : createSuccess(),
                errors                 : createErrors(),
                categoryFilteredOutUrns: flix.categoryFilteredOutUrns,
                eanCodeFilteredOutUrns : flix.eanCodeFilteredOutUrns
        ]
    }

}
