package com.sony.ebs.octopus3.microservices.flix.model

import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
class FlixPackage {
    String publication
    String locale
}
