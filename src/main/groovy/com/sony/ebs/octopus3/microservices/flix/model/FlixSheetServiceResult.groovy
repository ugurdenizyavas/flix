package com.sony.ebs.octopus3.microservices.flix.model

import com.fasterxml.jackson.annotation.JsonInclude
import groovy.transform.EqualsAndHashCode
import groovy.transform.Sortable
import groovy.transform.ToString

@ToString(includeNames = true, includePackage = false, ignoreNulls = true)
@Sortable(includes = ['urn', 'success', 'statusCode'])
@EqualsAndHashCode(includes = ['urn', 'success', 'statusCode', 'result'])
@JsonInclude(JsonInclude.Include.NON_NULL)
class FlixSheetServiceResult {

    String urn
    int statusCode
    List result = []
    boolean success

}
