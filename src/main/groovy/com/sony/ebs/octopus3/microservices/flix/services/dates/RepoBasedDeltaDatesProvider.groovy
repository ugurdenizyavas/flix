package com.sony.ebs.octopus3.microservices.flix.services.dates

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@Qualifier("repoBasedDeltaDatesProvider")
@org.springframework.context.annotation.Lazy
class RepoBasedDeltaDatesProvider implements DeltaDatesProvider {

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Value('${octopus3.flix.repositoryFileAttributesServiceUrl}')
    String repositoryFileAttributesServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    @Override
    rx.Observable<String> updateLastModified(Flix flix) {
        rx.Observable.from("starting").flatMap({
            def url = repositoryFileServiceUrl.replace(":urn", flix.lastModifiedUrn.toString())
            httpClient.doPost(url, "update")
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response)
        }).map({
            "done"
        })
    }

    rx.Observable<Map> getLastModifiedTime(URN urn) {
        rx.Observable.from("starting").flatMap({
            def url = repositoryFileAttributesServiceUrl.replace(":urn", urn.toString())
            httpClient.doGet(url)
        }).flatMap({ Response response ->
            if (NingHttpClient.isSuccess(response)) {
                observe(execControl.blocking {
                    def json = new JsonSlurper().parseText(response.responseBody)
                    def lastModifiedTime = json.result.lastModifiedTime
                    log.info "lastModifiedTime for $urn is $lastModifiedTime"
                    return [found: true, lastModifiedTime: lastModifiedTime]
                })
            } else {
                return rx.Observable.just([found: false])

            }
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

    @Override
    rx.Observable<String> createDateParams(Flix flix) {
        if (!flix.sdate) {
            getLastModifiedTime(flix.lastModifiedUrn)
                    .flatMap({ Map result ->
                observe(execControl.blocking {
                    String lmt = result.found ? result.lastModifiedTime : null
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
