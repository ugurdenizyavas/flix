package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl

class FlixUtils {

    static URN getXmlUrn(String urnStr) {
        def urn = new URNImpl(urnStr)
        def values = urn.values
        if (values && values.last()) {
            def last = values.last()
            values = values - last + "${last}.xml"
        }
        new URNImpl(DeltaType.flixMedia.toString(), values)
    }

    static URN getCategoryUrn(String publication, String locale) {
        new URNImpl(DeltaType.flixMedia.toString(), [publication, locale, DeltaType.category.toString() + ".xml"])
    }

    static URN getThirdPartyUrn() {
        new URNImpl(DeltaType.thirdparty.toString(), [DeltaType.flixMedia.toString()])
    }

    static URN getArchiveUrn() {
        new URNImpl(DeltaType.archive.toString(), [DeltaType.flix_sku.toString()])
    }

    static URN getThirdPartyPackageUrn(String packageName) {
        new URNImpl(DeltaType.thirdparty.toString(), [DeltaType.flixMedia.toString(), packageName.toLowerCase()])
    }

    static URN getArchivePackageUrn(String packageName) {
        new URNImpl(DeltaType.archive.toString(), [DeltaType.flix_sku.toString(), packageName.toLowerCase()])
    }

}

