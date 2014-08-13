package com.sony.ebs.octopus3.microservices.flix.services.dates

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class DeltaDatesProvider {

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Autowired
    FileAttributesProvider fileAttributesProvider

    rx.Observable<String> updateLastModified(Flix flix) {
        rx.Observable.just("starting").flatMap({
            def url = repositoryFileServiceUrl.replace(":urn", flix.lastModifiedUrn.toString())
            httpClient.doPost(url, "update")
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "updating last modified date", flix.errors)
        }).map({
            "done"
        })
    }

    String createDateParamsInner(Flix flix, String lastModifiedTime) {
        def sb = new StringBuilder()

        def sdate = flix.sdate
        if (!sdate) {
            sdate = lastModifiedTime
        }
        if (sdate) {
            sb.append("?sdate=").append(URLEncoder.encode(sdate, "UTF-8"))
        }
        if (flix.edate) {
            sb.size() == 0 ? sb.append("?") : sb.append("&")
            sb.append("edate=").append(URLEncoder.encode(flix.edate, "UTF-8"))
        }
        sb.toString()
    }

    rx.Observable<String> createDateParams(Flix flix) {
        if (!flix.sdate) {
            fileAttributesProvider.getLastModifiedTime(flix.lastModifiedUrn)
                    .flatMap({ result ->
                observe(execControl.blocking {
                    String lmt = result.found ? result.value : null
                    createDateParamsInner(flix, lmt)
                })
            })
        } else {
            observe(execControl.blocking {
                createDateParamsInner(flix, null)
            })
        }
    }
}
