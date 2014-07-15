import com.sony.ebs.octopus3.microservices.flix.SpringConfig
import com.sony.ebs.octopus3.microservices.flix.handlers.ErrorHandler
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

    bindings {
        add new JacksonModule()
        bind ClientErrorHandler, new ErrorHandler()
        bind ServerErrorHandler, new ErrorHandler()
        init {
            AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext(SpringConfig.class)
            ctx.beanFactory.registerSingleton "launchConfig", launchConfig
            ctx.beanFactory.registerSingleton "execControl", launchConfig.execController.control

            flixFlowHandler = ctx.getBean(FlixFlowHandler.class)
            flixSheetFlowHandler = ctx.getBean(FlixSheetFlowHandler.class)

            RxRatpack.initialize()
        }
    }

    handlers {
        get("flix/publication/:publication/locale/:locale", flixFlowHandler)
        get("flix/sheet/:urn", flixSheetFlowHandler)
    }
}
