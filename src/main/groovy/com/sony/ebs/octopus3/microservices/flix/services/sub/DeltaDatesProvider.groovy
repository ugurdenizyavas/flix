package com.sony.ebs.octopus3.microservices.flix.services.sub

import com.sony.ebs.octopus3.microservices.flix.model.Flix

public interface DeltaDatesProvider {

    String updateLastModified(Flix flix)

    String createDateParams(Flix flix)
}