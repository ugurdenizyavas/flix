package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.ProductResult
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
                processId: context.request.queryParams.processId
        )
        activity.debug "starting {}", product

        def startTime = new DateTime()

        List errors = validator.validateRepoProduct(product)
        if (errors) {
            activity.error "error validating {} : {}", product, errors
            context.response.status(400)
            context.render deltaResultService.createProductResultInvalid(product, errors)
        } else {
            ProductResult productResult = new ProductResult()
            productService.processProduct(product, productResult).finallyDo({
                def endTime = new DateTime()
                if (productResult.errors) {
                    activity.error "finished {} with errors: {}", product, productResult.errors
                    context.response.status(500)
                } else {
                    activity.debug "finished {} with success", product
                    context.response.status(200)
                }
                context.render deltaResultService.createProductResult(product, productResult, startTime, endTime)
            }).subscribe({
                activity.debug "product flow for {} finished with {}", product, productResult
            }, { e ->
                productResult.errors << HandlerUtil.getErrorMessage(e)
                activity.error "error in $product", e
            })
        }

    }

}
