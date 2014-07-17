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
        new URNImpl(FlixConstants.FLIX_MEDIA, [publication, locale, FlixConstants.CATEGORY])
    }

    URN getDeltaUrn() {
        new URNImpl(FlixConstants.GLOBAL_SKU, [publication, locale])
    }

    URN getLastModifiedUrn() {
        new URNImpl(FlixConstants.LAST_MODIFIED, [publication, locale])
    }

    URN getBaseUrn() {
        new URNImpl(FlixConstants.FLIX_MEDIA, [publication, locale])
    }

}
