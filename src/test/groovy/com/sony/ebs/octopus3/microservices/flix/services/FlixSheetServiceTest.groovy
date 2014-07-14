package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.microservices.flix.model.FlixSheet
import groovy.util.logging.Slf4j
import org.junit.After
import org.junit.Before
import org.junit.Test
import ratpack.exec.ExecController
import ratpack.launch.LaunchConfigBuilder

@Slf4j
class FlixSheetServiceTest {

    FlixSheetService flixSheetService
    ExecController execController

    @Before
    void before() {
        execController = LaunchConfigBuilder.noBaseDir().build().execController
        flixSheetService = new FlixSheetService(execControl: execController.control)
    }

    @After
    void after() {
        if (execController) execController.close()
    }

    @Test
    void "delta flow"() {
        def flixSheet = new FlixSheet(processId: "123", urnStr: "urn:flix:score:en_gb")

        def finished = new Object()
        execController.start {
            flixSheetService.importSheet(flixSheet).subscribe { String result ->
                synchronized (finished) {
                    assert result == "$flixSheet started"
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
