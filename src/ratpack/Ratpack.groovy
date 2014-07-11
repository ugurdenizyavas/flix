import com.sony.ebs.octopus3.microservices.flix.SpringConfig
import com.sony.ebs.octopus3.microservices.flix.handlers.FlixFlowHandler
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import ratpack.error.DebugErrorHandler
import ratpack.error.ServerErrorHandler
import ratpack.jackson.JacksonModule
import ratpack.rx.RxRatpack

import static ratpack.groovy.Groovy.ratpack

Logger log = LoggerFactory.getLogger("ratpack");

ratpack {

    FlixFlowHandler flixFlowHandler

    bindings {
        add new JacksonModule()
        bind ServerErrorHandler, new DebugErrorHandler()
        init {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class)
            ctx.beanFactory.registerSingleton "launchConfig", launchConfig
            ctx.beanFactory.registerSingleton "execControl", launchConfig.execController.control

            flixFlowHandler = ctx.getBean(FlixFlowHandler.class)

            RxRatpack.initialize()
        }
    }

    handlers {
        get("flix/publication/:publication/locale/:locale", flixFlowHandler)
    }
}
