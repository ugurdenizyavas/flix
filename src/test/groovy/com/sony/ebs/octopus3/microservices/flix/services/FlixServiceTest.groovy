package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.process.ProcessIdImpl
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
        ProcessId processId = new ProcessIdImpl()

        def finished = new Object()
        execController.start {
            flixService.flixFlow(processId, "SCORE", "en_GB").subscribe { String result ->
                synchronized (finished) {
                    assert result == "flix started"
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
