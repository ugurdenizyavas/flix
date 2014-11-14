package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.flows.FlowTypeEnum
import com.sony.ebs.octopus3.commons.flows.ServiceTypeEnum
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HazelcastAwareDeltaHandler
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.ProductServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.basic.DeltaService
import com.sony.ebs.octopus3.microservices.flix.services.basic.PackageService
import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext

import static ratpack.jackson.Jackson.json

@Slf4j(value = "log", category = "DeltaHandler")
@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class DeltaHandler extends HazelcastAwareDeltaHandler<RepoDelta> {

    @Autowired
    DeltaService deltaService

    @Autowired
    PackageService packageService

    @Autowired
    RequestValidator validator

    @Autowired
    ResponseStorage responseStorage

    @Override
    FlowTypeEnum getFlow() {
        FlowTypeEnum.FLIX
    }

    @Override
    String getPublication(GroovyContext context) {
        context.pathTokens.publication
    }

    @Override
    String getLocale(GroovyContext context) {
        context.pathTokens.locale
    }

    @Override
    List flowValidate(GroovyContext context, RepoDelta delta) {
        delta.sdate = context.request.queryParams.sdate
        delta.edate = context.request.queryParams.edate
        validator.validateRepoDelta(delta)
    }

    @Override
    void flowHandle(GroovyContext context, RepoDelta delta) {
        def startTime = new DateTime()
        def flix = new Flix()

        List productServiceResults = []
        deltaService.processDelta(delta, flix).finallyDo({
            if (delta.errors) {
                activity.error "finished {} with errors: {}", delta, delta.errors
                def endTime = new DateTime()
                def timeStats = HandlerUtil.getTimeStats(startTime, endTime)
                context.response.status(500)

                def jsonResponse = json(status: 500, timeStats: timeStats, errors: delta.errors, delta: delta)

                responseStorage.store(delta.processId.id, ["flix", "delta", delta.publication, delta.locale, delta.processId.id], JsonOutput.toJson(jsonResponse.object))

                context.render jsonResponse
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

    @Override
    RepoDelta createDelta(ProcessIdImpl processId, FlowTypeEnum flowTypeEnum, ServiceTypeEnum serviceTypeEnum, String publication, String locale) {
        [processId: processId, flow: flowTypeEnum, service: serviceTypeEnum, publication: publication, locale: locale, type: DeltaType.flixMedia]
    }

    void handleFlixPackage(GroovyContext context, RepoDelta delta, Flix flix, List productServiceResults, DateTime startTime) {
        context.with {
            packageService.packageFlow(delta, flix).finallyDo({
                def endTime = new DateTime()
                def timeStats = HandlerUtil.getTimeStats(startTime, endTime)

                finalizeInAsyncThread(delta)

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
