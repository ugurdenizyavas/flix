package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
class FlixService {

    @Autowired
    @Lazy
    ExecControl execControl

    rx.Observable<String> flixFlow(ProcessId processId, String publication, String locale) {
        observe(execControl.blocking {
            log.info "flix started for processId $processId.id, publication $publication, locale $locale"
            "flix started"
        })
    }

}

