package com.sony.ebs.octopus3.microservices.flix.model

import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
class Flix {

    ProcessId processId
    String publication
    String locale
    String sdate
    String edate

    URN getCategoryUrn() {
        new URNImpl("flixMedia", ["flixCategory", publication, locale])
    }

    URN getDeltaUrn() {
        new URNImpl("flixMedia", ["flixDelta", publication, locale])
    }

}
