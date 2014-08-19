package com.sony.ebs.octopus3.microservices.flix.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true, excludes = ['errors'])
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
class FlixSheet {

    String processId
    String urnStr
    String eanCode

    @JsonIgnore
    List errors = []

    @JsonIgnore
    URN getUrn() {
        new URNImpl(urnStr)
    }

    @JsonIgnore
    URN getXmlUrn() {
        def values = new URNImpl(urnStr).values
        if (values && values.last()) {
            def last = values.last()
            values = values - last + "${last}.xml"
        }
        new URNImpl(FlixUrnValue.flixMedia.toString(), values)
    }
}
