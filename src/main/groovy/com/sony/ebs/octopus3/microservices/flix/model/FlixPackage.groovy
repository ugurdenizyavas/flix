package com.sony.ebs.octopus3.microservices.flix.model

import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
class FlixPackage {
    String publication
    String locale

    URN getPackageUrn() {
        new URNImpl("flixMedia", [publication, locale])
    }

}
