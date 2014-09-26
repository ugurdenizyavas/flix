package com.sony.ebs.octopus3.microservices.flix.handlers

import com.sony.ebs.octopus3.commons.ratpack.handlers.HandlerUtil
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoProduct
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.microservices.flix.services.basic.ProductService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import ratpack.groovy.handling.GroovyContext
import ratpack.groovy.handling.GroovyHandler

import static ratpack.jackson.Jackson.json

@Slf4j(value = "activity", category = "activity")
@Component
@org.springframework.context.annotation.Lazy
class ProductHandler extends GroovyHandler {

    @Autowired
    ProductService productService

    @Autowired
    RequestValidator validator

    @Override
    protected void handle(GroovyContext context) {
        context.with {
            RepoProduct product = new RepoProduct(type: DeltaType.flixMedia, publication: pathTokens.publication,
                    locale: pathTokens.locale, sku: pathTokens.sku,
                    eanCode: request.queryParams.eanCode,
                    processId: request.queryParams.processId)
            activity.debug "starting {}", product

            List result = []
            List errors = validator.validateRepoProduct(product)
            if (errors) {
                activity.error "error validating {} : {}", product, errors
                response.status(400)
                render json(status: 400, errors: errors, product: product)
            } else {
                productService.processProduct(product).finallyDo({
                    if (product.errors) {
                        activity.error "finished {} with errors: {}", product, product.errors
                        response.status(500)
                        render json(status: 500, errors: product.errors, product: product)
                    } else {
                        activity.debug "finished {} with success", product
                        response.status(200)
                        render json(status: 200, product: product, result: result)
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

}
