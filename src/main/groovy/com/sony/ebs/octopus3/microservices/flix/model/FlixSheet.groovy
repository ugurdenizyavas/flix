package com.sony.ebs.octopus3.microservices.flix.model

import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
class FlixSheet {

    String processId
    String urnStr

    URN getUrn() {
        new URNImpl(urnStr)
    }

    URN getSheetUrn() {
        new URNImpl(FlixUrnValue.flixMedia.toString(), new URNImpl(urnStr).values)
    }
}
