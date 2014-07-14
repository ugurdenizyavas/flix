package com.sony.ebs.octopus3.microservices.flix.http

import com.ning.http.client.AsyncHttpClient
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import ratpack.exec.ExecControl

import javax.annotation.PostConstruct

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Component
class NingHttpClient {

    enum RequestType {
        GET_LOCAL, POST_LOCAL
    }

    @Autowired
    @Lazy
    ExecControl execControl

    AsyncHttpClient asyncHttpClient

    @PostConstruct
    void init() {
        asyncHttpClient = new AsyncHttpClient()
    }

    private String getByNing(RequestType requestType, String urlString, String data = null) {
        def url = new URIBuilder(urlString).toString()

        log.info "starting $requestType for $url"
        def f
        if (RequestType.GET_LOCAL == requestType) {
            f = asyncHttpClient.prepareGet(url)
                    .addHeader('Accept-Charset', 'UTF-8')
                    .execute()
        } else if (RequestType.POST_LOCAL == requestType) {
            f = asyncHttpClient.preparePost(url)
                    .addHeader('Accept-Charset', 'UTF-8')
                    .setBody(data)
                    .execute()
        }
        def response = f.get()

        if (response.statusCode != 200 && response.statusCode != 202) {
            def message = "error getting $url with http status code $response.statusCode"
            log.error message
            throw new Exception(message)
        } else {
            log.info "finished $requestType for $url with status code $response.statusCode"
            return response.responseBody
        }
    }

    private rx.Observable<String> getObservableNing(RequestType requestType, String url, String data = null) {
        observe(execControl.blocking {
            getByNing(requestType, url, data)
        })
    }

    rx.Observable<String> getLocal(String url) {
        getObservableNing(RequestType.GET_LOCAL, url)
    }

    rx.Observable<String> postLocal(String url, String data) {
        getObservableNing(RequestType.POST_LOCAL, url, data)
    }
}
