package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
class FlixSheetService {

    @Autowired
    @Lazy
    ExecControl execControl

    rx.Observable<String> importSheet(FlixSheet flixSheet) {
        observe(execControl.blocking {
            log.info "$flixSheet started"
            "$flixSheet started"
        })
    }

}

