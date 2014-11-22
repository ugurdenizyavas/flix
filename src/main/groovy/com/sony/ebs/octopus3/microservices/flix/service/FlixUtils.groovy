package com.sony.ebs.octopus3.microservices.flix.service

import com.sony.ebs.octopus3.commons.flows.RepoValue
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import org.apache.http.client.utils.URIBuilder

class FlixUtils {

    static URN getXmlUrn(String urnStr) {
        def urn = new URNImpl(urnStr)
        def values = urn.values
        if (values && values.last()) {
            def last = values.last()
            values = values - last + "${last}.xml"
        }
        new URNImpl(RepoValue.flixMedia.toString(), values)
    }

    static URN getCategoryUrn(String publication, String locale) {
        new URNImpl(RepoValue.flixMedia.toString(), [publication, locale, RepoValue.category.toString() + ".xml"])
    }

    static URN getThirdPartyUrn() {
        new URNImpl(RepoValue.thirdparty.toString(), [RepoValue.flixMedia.toString()])
    }

    static URN getArchiveUrn() {
        new URNImpl(RepoValue.archive.toString(), [RepoValue.flix_sku.toString()])
    }

    static URN getThirdPartyPackageUrn(String packageName) {
        new URNImpl(RepoValue.thirdparty.toString(), [RepoValue.flixMedia.toString(), packageName.toLowerCase()])
    }

    static URN getArchivePackageUrn(String packageName) {
        new URNImpl(RepoValue.archive.toString(), [RepoValue.flix_sku.toString(), packageName.toLowerCase()])
    }

    static String addProcessId(String initialUrl, String processId) {
        new URIBuilder(initialUrl).with {
            if (processId) {
                addParameter("processId", processId)
            }
            it.toString()
        }
    }
}

