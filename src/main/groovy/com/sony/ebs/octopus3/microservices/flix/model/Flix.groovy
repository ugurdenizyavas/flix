package com.sony.ebs.octopus3.microservices.flix.model

import com.sony.ebs.octopus3.commons.process.ProcessId
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false)
class Flix {

    ProcessId processId
    String publication
    String locale
    String sdate
    String edate

}
