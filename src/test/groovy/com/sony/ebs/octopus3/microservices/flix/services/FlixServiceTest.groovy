package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder

@Slf4j
class FlixServiceTest {

    FlixService flixService
    ExecController execController

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixService = new FlixService(execControl: execController.control)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "delta flow"() {
        def flix = new Flix(processId: new ProcessIdImpl("123"), publication: "SCORE", locale: "en_GB", sdate: "d1", edate: "d2")

        def finished = new Object()
        execController.start {
            flixService.flixFlow(flix).subscribe { String result ->
                synchronized (finished) {
                    assert result == "$flix started"
                    log.info "assertions finished"
                    finished.notifyAll()
                }
            }
        }
        synchronized (finished) {
            finished.wait 5000
        }
    }

}
