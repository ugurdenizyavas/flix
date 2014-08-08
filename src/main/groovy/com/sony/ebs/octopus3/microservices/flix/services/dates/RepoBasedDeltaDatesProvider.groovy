package com.sony.ebs.octopus3.microservices.flix.services.dates

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value

class RepoBasedDeltaDatesProvider implements DeltaDatesProvider {

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Override
    String updateLastModified(Flix flix) {
        return null
    }

    @Override
    String createDateParams(Flix flix) {
        return null
    }
}
