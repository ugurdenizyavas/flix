package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.service.ProductService
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class ProductHandler extends GroovyHandler {

    @Autowired
    ProductService productService

    @Autowired
    RequestValidator validator

    @Autowired
    DeltaResultService deltaResultService

    @Override
    protected void handle(GroovyContext context) {
        RepoProduct product = new RepoProduct(
                type: RepoValue.flixMedia,
                publication: context.pathTokens.publication,
                locale: context.pathTokens.locale,
                sku: context.pathTokens.sku,
                eanCode: context.request.queryParams.eanCode,
                processId: context.request.queryParams.processId
        )
        activity.debug "starting {}", product

        def startTime = new DateTime()

        List result = []
        List errors = validator.validateRepoProduct(product)
        if (errors) {
            activity.error "error validating {} : {}", product, errors
            context.response.status(400)
            context.render deltaResultService.createProductResultInvalid(product, errors)
        } else {
            productService.processProduct(product).finallyDo({
                def endTime = new DateTime()
                if (product.errors) {
                    activity.error "finished {} with errors: {}", product, product.errors
                    context.response.status(500)
                    context.render deltaResultService.createProductResultWithErrors(product, product.errors, startTime, endTime)
                } else {
                    activity.debug "finished {} with success", product
                    context.response.status(200)
                    context.render deltaResultService.createProductResult(product, result, startTime, endTime)
                }
            }).subscribe({
                def flowResult = it?.toString()
                result << flowResult
                activity.debug "sheet flow for {} emitted: {}", product, flowResult
            }, { e ->
                product.errors << HandlerUtil.getErrorMessage(e)
                activity.error "error in $product", e
            })
        }

    }

}
