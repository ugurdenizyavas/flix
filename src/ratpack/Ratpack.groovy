import com.sony.ebs.octopus3.commons.ratpack.handlers.ErrorHandler
import com.sony.ebs.octopus3.commons.ratpack.handlers.HealthCheckHandler
import com.sony.ebs.octopus3.commons.ratpack.monitoring.MonitoringService
import com.sony.ebs.octopus3.microservices.flix.handlers.DeltaHandler
import com.sony.ebs.octopus3.microservices.flix.handlers.ProductHandler
import com.sony.ebs.octopus3.microservices.flix.spring.config.SpringConfig
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import ratpack.error.ClientErrorHandler
import ratpack.error.ServerErrorHandler
import ratpack.jackson.JacksonModule
import ratpack.rx.RxRatpack

import static ratpack.groovy.Groovy.ratpack

ratpack {

    DeltaHandler deltaHandler
    ProductHandler productHandler
    HealthCheckHandler healthCheckHandler

    bindings {
        add new JacksonModule()
        bind ClientErrorHandler, new ErrorHandler()
        bind ServerErrorHandler, new ErrorHandler()
        init {
            RxRatpack.initialize()

            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class)
            ctx.beanFactory.registerSingleton "launchConfig", launchConfig
            ctx.beanFactory.registerSingleton "execControl", launchConfig.execController.control

            deltaHandler = ctx.getBean(DeltaHandler.class)
            productHandler = ctx.getBean(ProductHandler.class)
            healthCheckHandler = new HealthCheckHandler(monitoringService: new MonitoringService())
        }
    }

    handlers {
        get("healthcheck", healthCheckHandler)
        get("flix/delta/publication/:publication/locale/:locale", deltaHandler)
        get("flix/product/publication/:publication/locale/:locale/sku/:sku", productHandler)
    }
}
