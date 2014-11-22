package com.sony.ebs.octopus3.microservices.flix.model

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@EqualsAndHashCode
class Flix {

    List categoryFilteredOutUrns
    String outputPackageUrl
    String archivePackageUrl

}
