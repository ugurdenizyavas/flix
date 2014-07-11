package com.sony.ebs.octopus3.microservices.flix.validators

import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.util.logging.Slf4j
import org.springframework.stereotype.Component

@Slf4j
@Component
class RequestValidator {

    /**
     * The urn needs to be valid and should have a host
     * @param url
     * @return
     */
    URN createUrn(String urnStr) {
        def urn
        try {
            urn = new URNImpl(urnStr)
            log.debug "urn is $urn for $urnStr"
        } catch (e) {
            log.error "invalid urn value $urnStr", e
        }
        urn
    }

}
