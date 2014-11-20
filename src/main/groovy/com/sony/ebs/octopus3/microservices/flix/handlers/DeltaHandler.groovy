package com.sony.ebs.octopus3.microservices.flix.handlers

import com.hazelcast.core.HazelcastInstance
import com.sony.ebs.octopus3.commons.flows.FlowTypeEnum
import com.sony.ebs.octopus3.commons.flows.ServiceTypeEnum
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HazelcastAwareDeltaHandler
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import com.sony.ebs.octopus3.microservices.flix.model.ProductServiceResult
import com.sony.ebs.octopus3.microservices.flix.services.basic.DeltaService
import com.sony.ebs.octopus3.microservices.flix.services.basic.PackageService
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext

@Slf4j(value = "log", category = "DeltaHandler")
@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class DeltaHandler extends HazelcastAwareDeltaHandler<RepoDelta> {

    DeltaService deltaService
    PackageService packageService
    RequestValidator validator

    @Autowired
    public DeltaHandler(
            DeltaService deltaService,
            PackageService packageService,
            RequestValidator validator,
            HazelcastInstance hazelcastInstance,
            DeltaResultService deltaResultService,
            ResponseStorage responseStorage
    ) {
        this.deltaService = deltaService
        this.packageService = packageService
        this.validator = validator
        super.setHazelcastInstance(hazelcastInstance)
        super.setDeltaResultService(deltaResultService)
        super.setResponseStorage(responseStorage)
    }

    public DeltaHandler() {
    }


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

        List<ProductServiceResult> productServiceResults = []
        deltaService.processDelta(delta, flix).finallyDo({
            if (delta.errors) {
                def jsonResponse = processError(delta, delta.errors, startTime)
                context.response.status(500)
                context.render jsonResponse
            } else {
                packageService.packageFlow(delta, flix).finallyDo({
                    if (delta.errors) {
                        def jsonResponse = processError(delta, delta.errors, startTime)
                        context.response.status(500)
                        context.render jsonResponse
                    } else {
                        def deltaResult = createDeltaResult(delta, flix, productServiceResults)
                        def jsonResponse = processSuccess(delta, deltaResult, startTime)
                        context.response.status(200)
                        context.render jsonResponse
                    }
                }).subscribe({
                    activity.debug "{} emitted: {}", delta, it
                }, { e ->
                    delta.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $delta", e
                })
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

    DeltaResult createDeltaResult(RepoDelta delta, Flix flix, List<ProductServiceResult> sheetServiceResults) {

        Map productErrors = [:]
        sheetServiceResults.findAll({ !it.success }).each { ProductServiceResult serviceResult ->
            serviceResult.errors.each { error ->
                if (productErrors[error] == null) productErrors[error] = []
                productErrors[error] << serviceResult.jsonUrn
            }
        }

        def xmlFileUrls = sheetServiceResults.findAll({ it.success }).collect({ it.xmlFileUrl })
        def successfulUrns = sheetServiceResults?.findAll({ it.success }).collect({ it.jsonUrn })
        def unsuccessfulUrns = sheetServiceResults?.findAll({ !it.success }).collect({ it.jsonUrn })

        new DeltaResult(
                productErrors: productErrors,
                deltaUrns: delta.deltaUrns,
                categoryFilteredOutUrns: flix.categoryFilteredOutUrns,
                eanCodeFilteredOutUrns: flix.eanCodeFilteredOutUrns,
                successfulUrns: successfulUrns,
                unsuccessfulUrns: unsuccessfulUrns,
                other: [
                        "package created" : flix.outputPackageUrl,
                        "package archived": flix.archivePackageUrl,
                        xmlFileUrls       : xmlFileUrls
                ]

        )
    }

}
