package com.sony.ebs.octopus3.microservices.flix.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
class FlixSheet {

    String processId
    String urnStr

    @JsonIgnore
    List errors = []

    @JsonIgnore
    URN getUrn() {
        new URNImpl(urnStr)
    }

    @JsonIgnore
    URN getSheetUrn() {
        new URNImpl(FlixUrnValue.flixMedia.toString(), new URNImpl(urnStr).values)
    }
}
