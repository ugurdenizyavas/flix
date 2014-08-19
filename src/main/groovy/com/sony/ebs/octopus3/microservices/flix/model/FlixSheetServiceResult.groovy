package com.sony.ebs.octopus3.microservices.flix.model

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@Sortable(includes = ['jsonUrn', 'success', 'statusCode'])
@EqualsAndHashCode(includes = ['jsonUrn', 'success', 'statusCode', 'errors', 'xmlFileUrl'])
@JsonInclude(JsonInclude.Include.NON_NULL)
class FlixSheetServiceResult {

    String jsonUrn
    int statusCode
    boolean success
    List errors
    String xmlFileUrl
    String xmlFileAttributesUrl

}
