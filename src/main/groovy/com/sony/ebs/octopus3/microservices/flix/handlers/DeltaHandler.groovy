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
                def jsonResponse = processResult(delta, deltaResult, startTime)
                context.response.status(500)
                context.render jsonResponse
            } else {
                packageService.processPackage(delta, deltaResult).finallyDo({
                    if (deltaResult.errors) {
                        context.response.status(500)
                    } else {
                        enhanceDeltaResult(deltaResult, productServiceResults)
                        context.response.status(200)
                    }
                    def jsonResponse = processResult(delta, deltaResult, startTime)
                    context.render jsonResponse
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

    def enhanceDeltaResult(DeltaResult deltaResult, List<ProductResult> productResults) {
        deltaResult.with {
            productErrors = productResults.findAll({ !it.success }).inject([:]) { map, ProductResult productResult ->
                productResult.errors.each { error ->
                    if (map[error] == null) map[error] = []
                    map[error] << productResult.inputUrn
                }
                map
            }
            categoryFilteredOutUrns = deltaResult.categoryFilteredOutUrns
            eanCodeFilteredOutUrns = productResults?.findAll({ !it.eanCode }).collect({ it.inputUrn })
            successfulUrns = productResults?.findAll({ it.success }).collect({ it.inputUrn })
            unsuccessfulUrns = productResults?.findAll({ it.eanCode && !it.success }).collect({ it.inputUrn })
            other = [
                    "package created" : deltaResult.other?.outputPackageUrl,
                    "package archived": deltaResult.other?.archivePackageUrl,
                    outputUrls        : (productResults.findAll({ it.success }).collect({ it.outputUrl }))
            ]
        }
    }

}
