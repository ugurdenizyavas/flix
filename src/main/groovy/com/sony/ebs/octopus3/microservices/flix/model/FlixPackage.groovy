package com.sony.ebs.octopus3.microservices.flix.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
class FlixPackage {

    @JsonIgnore
    final static DateTimeFormatter FMT = DateTimeFormat.forPattern("yyyyMMdd_HHmmss")

    String publication
    String locale

    @JsonIgnore
    List errors = []

    @JsonIgnore
    URN getBaseUrn() {
        new URNImpl(FlixUrnValue.flixMedia.toString(), [publication, locale])
    }

    @JsonIgnore
    URN getDestinationUrn() {
        def name = "Flix_${locale}_${new DateTime().toString(FMT)}.zip"
        new URNImpl(FlixUrnValue.thirdparty.toString(), [FlixUrnValue.flixMedia.toString(), name])
    }

}
