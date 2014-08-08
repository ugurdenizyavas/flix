package com.sony.ebs.octopus3.microservices.flix.services.dates

import com.sony.ebs.octopus3.microservices.flix.model.Flix

public interface DeltaDatesProvider {

    rx.Observable<String> updateLastModified(Flix flix)

    rx.Observable<String> createDateParams(Flix flix)
}