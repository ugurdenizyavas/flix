package com.sony.ebs.octopus3.microservices.flix.handlers

import com.hazelcast.core.HazelcastInstance
import com.sony.ebs.octopus3.commons.flows.FlowTypeEnum
import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.handlers.HazelcastAwareDeltaHandler
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.service.DeltaService
import com.sony.ebs.octopus3.microservices.flix.service.PackageService
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext

@Slf4j(value = "log", category = "DeltaHandler")
@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class DeltaHandler extends HazelcastAwareDeltaHandler<RepoDelta> {

    DeltaService deltaService
    PackageService packageService

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
        super.setValidator(validator)
        super.setHazelcastInstance(hazelcastInstance)
        super.setDeltaResultService(deltaResultService)
        super.setResponseStorage(responseStorage)
    }

    public DeltaHandler() {
    }

    @Override
    RepoValue getDeltaType() {
        return RepoValue.flixMedia
    }

    @Override
    RepoDelta createDelta(GroovyContext context) {
        new RepoDelta()
    }

    @Override
    FlowTypeEnum getFlowType() {
        FlowTypeEnum.FLIX
    }

    @Override
    void flowHandle(GroovyContext context, RepoDelta delta) {
        def startTime = new DateTime()

        List<ProductResult> productServiceResults = []
        DeltaResult deltaResult = new DeltaResult()
        deltaService.processDelta(delta, deltaResult).finallyDo({
            if (deltaResult.errors) {
                def jsonResponse = processError(delta, deltaResult.errors, startTime)
                context.response.status(500)
                context.render jsonResponse
            } else {
                packageService.processPackage(delta, deltaResult).finallyDo({
                    if (deltaResult.errors) {
                        def jsonResponse = processError(delta, deltaResult.errors, startTime)
                        context.response.status(500)
                        context.render jsonResponse
                    } else {
                        enhanceDeltaResult(deltaResult, productServiceResults)
                        def jsonResponse = processSuccess(delta, deltaResult, startTime)
                        context.response.status(200)
                        context.render jsonResponse
                    }
                }).subscribe({
                    activity.debug "{} emitted: {}", delta, it
                }, { e ->
                    deltaResult.errors << HandlerUtil.getErrorMessage(e)
                    activity.error "error in $delta", e
                })
            }
        }).subscribe({
            productServiceResults << it
            activity.debug "flix flow emitted: {}", it
        }, { e ->
            deltaResult.errors << HandlerUtil.getErrorMessage(e)
            activity.error "error in $delta", e
        })
    }

    def enhanceDeltaResult(DeltaResult deltaResult, List<ProductResult> sheetResults) {
        Map pErrors = [:]
        sheetResults.findAll({ !it.success }).each { ProductResult serviceResult ->
            serviceResult.errors.each { error ->
                if (pErrors[error] == null) pErrors[error] = []
                pErrors[error] << serviceResult.inputUrn
            }
        }
        deltaResult.with {
            productErrors = pErrors
            categoryFilteredOutUrns = deltaResult.categoryFilteredOutUrns
            eanCodeFilteredOutUrns = sheetResults?.findAll({ !it.eanCode }).collect({ it.inputUrn })
            successfulUrns = sheetResults?.findAll({ it.success }).collect({ it.inputUrn })
            unsuccessfulUrns = sheetResults?.findAll({ it.eanCode && !it.success }).collect({ it.inputUrn })
            other = [
                    "package created" : deltaResult.other?.outputPackageUrl,
                    "package archived": deltaResult.other?.archivePackageUrl,
                    outputUrls        : (sheetResults.findAll({ it.success }).collect({ it.outputUrl }))
            ]
        }
    }

}
