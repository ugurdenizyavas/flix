package com.sony.ebs.octopus3.microservices.flix.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonInclude
import com.sony.ebs.octopus3.commons.process.ProcessId
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNCreationException
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

@ToString(includeNames = true, includePackage = false, ignoreNulls = true, excludes = ['errors', 'deltaUrns', 'categoryFilteredOutUrns'])
@EqualsAndHashCode
@JsonInclude(JsonInclude.Include.NON_NULL)
class Flix {

    @JsonIgnore
    final static DateTimeFormatter FMT = DateTimeFormat.forPattern("yyyyMMdd_HHmmss")

    ProcessId processId
    String publication
    String locale
    String sdate
    String edate
    List deltaUrns
    List categoryFilteredOutUrns
    List eanCodeFilteredOutUrns

    @JsonIgnore
    Map eanCodeMap = [:]

    @JsonIgnore
    List errors = []

    @JsonIgnore
    URN getCategoryUrn() {
        new URNImpl(FlixUrnValue.flixMedia.toString(), [publication, locale, FlixUrnValue.category.toString() + ".xml"])
    }

    @JsonIgnore
    URN getDeltaUrn() {
        new URNImpl(FlixUrnValue.global_sku.toString(), [publication, locale])
    }

    @JsonIgnore
    URN getLastModifiedUrn() {
        new URNImpl(FlixUrnValue.flixMedia.toString(), [FlixUrnValue.last_modified.toString(), publication, locale])
    }

    @JsonIgnore
    URN getBaseUrn() {
        new URNImpl(FlixUrnValue.flixMedia.toString(), [publication, locale])
    }

    URN getSheetUrnByMaterialName(String materialName) throws URNCreationException {
        if (!materialName) {
            throw new URNCreationException("Invalid materialName");
        }
        new URNImpl(FlixUrnValue.global_sku.toString(), [publication, locale, materialName?.toLowerCase()])
    }

    @JsonIgnore
    URN getDestinationUrn() {
        def name = "Flix_${locale}_${new DateTime().toString(FMT)}.zip"
        new URNImpl(FlixUrnValue.thirdparty.toString(), [FlixUrnValue.flixMedia.toString(), name])
    }

}
