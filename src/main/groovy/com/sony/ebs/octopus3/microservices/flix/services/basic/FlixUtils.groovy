package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaType
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl

class FlixUtils {

    static URN getXmlUrn(URN productUrn) {
        def values = productUrn.values
        if (values && values.last()) {
            def last = values.last()
            values = values - last + "${last}.xml"
        }
        new URNImpl(DeltaType.flixMedia.toString(), values)
    }

    static URN getXmlUrn(String productUrnStr) {
        getXmlUrn(new URNImpl(productUrnStr))
    }


    static URN getCategoryUrn(String publication, String locale) {
        new URNImpl(DeltaType.flixMedia.toString(), [publication, locale, DeltaType.category.toString() + ".xml"])
    }

    static URN getThirdPartyUrn(String packageName) {
        new URNImpl(DeltaType.thirdparty.toString(), [DeltaType.flixMedia.toString(), packageName])
    }

    static URN getArchiveUrn(String packageName) {
        new URNImpl(DeltaType.archive.toString(), [DeltaType.flix_sku.toString(), packageName])
    }

    static URN getGlobalSkuUrn(String publication, String locale, String sku) {
        new URNImpl(DeltaType.global_sku.toString(), [publication, locale, sku])
    }

}

