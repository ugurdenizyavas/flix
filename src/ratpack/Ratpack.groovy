import com.sony.ebs.octopus3.commons.ratpack.handlers.ErrorHandler
import com.sony.ebs.octopus3.commons.ratpack.handlers.HealthCheckHandler
import com.sony.ebs.octopus3.commons.ratpack.monitoring.MonitoringService
import com.sony.ebs.octopus3.microservices.flix.spring.config.SpringConfig
import com.sony.ebs.octopus3.microservices.flix.handlers.FlixFlowHandler
import com.sony.ebs.octopus3.microservices.flix.handlers.FlixSheetFlowHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import ratpack.error.ClientErrorHandler
import ratpack.error.ServerErrorHandler
import ratpack.jackson.JacksonModule
import ratpack.rx.RxRatpack

import static ratpack.groovy.Groovy.ratpack

Logger log = LoggerFactory.getLogger("ratpack");

ratpack {

    FlixFlowHandler flixFlowHandler
    FlixSheetFlowHandler flixSheetFlowHandler
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

            flixFlowHandler = ctx.getBean(FlixFlowHandler.class)
            flixSheetFlowHandler = ctx.getBean(FlixSheetFlowHandler.class)
            healthCheckHandler = new HealthCheckHandler(monitoringService: new MonitoringService())
        }
    }

    handlers {
        get("healthcheck", healthCheckHandler)
        get("flix/delta/publication/:publication/locale/:locale", flixFlowHandler)
        get("flix/sheet/:urn", flixSheetFlowHandler)
    }
}
