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
        new URNImpl(FlixUrnValue.flixMedia.toString(), [publication, locale, FlixUrnValue.category.toString()])
    }

    URN getDeltaUrn() {
        new URNImpl(FlixUrnValue.global_sku.toString(), [publication, locale])
    }

    URN getLastModifiedUrn() {
        new URNImpl(FlixUrnValue.last_modified.toString(), [publication, locale])
    }

    URN getBaseUrn() {
        new URNImpl(FlixUrnValue.flixMedia.toString(), [publication, locale])
    }

}
