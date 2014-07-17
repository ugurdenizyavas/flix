package com.sony.ebs.octopus3.microservices.flix.http

import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.ProxyServer
import com.ning.http.client.Realm
import groovy.util.logging.Slf4j
import org.apache.http.client.utils.URIBuilder
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
class NingHttpClient {

    enum RequestType {
        GET, POST, DELETE
    }

    ExecControl execControl
    String authenticationUser, authenticationPassword
    AsyncHttpClient asyncHttpClient

    public NingHttpClient() {

    }

    public NingHttpClient(ExecControl execControl, String proxyHost, int proxyPort, String proxyUser, String proxyPassword,
                          String authenticationUser, String authenticationPassword) {
        AsyncHttpClientConfig config
        if (proxyHost) {
            config = new AsyncHttpClientConfig.Builder().setProxyServer(new ProxyServer(proxyHost, proxyPort, proxyUser, proxyPassword)).build()
        } else {
            config = new AsyncHttpClientConfig.Builder().build()
        }
        asyncHttpClient = new AsyncHttpClient(config)

        this.execControl = execControl
        this.authenticationUser = authenticationUser
        this.authenticationPassword = authenticationPassword
    }

    String getByNing(RequestType requestType, String urlString, String data = null) {
        def url = new URIBuilder(urlString).toString()

        log.info "starting $requestType for $url"

        Realm realm = authenticationUser ? (new Realm.RealmBuilder()).setScheme(Realm.AuthScheme.BASIC).setPrincipal(authenticationUser).setPassword(authenticationPassword).build() : null

        def f
        if (RequestType.GET == requestType) {
            f = asyncHttpClient.prepareGet(url).addHeader('Accept-Charset', 'UTF-8').setRealm(realm).execute()
        } else if (RequestType.DELETE == requestType) {
            f = asyncHttpClient.prepareDelete(url).addHeader('Accept-Charset', 'UTF-8').setRealm(realm).execute()
        } else {
            f = asyncHttpClient.preparePost(url).addHeader('Accept-Charset', 'UTF-8').setRealm(realm).setBody(data).execute()
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

    rx.Observable<String> getObservableNing(RequestType requestType, String url, String data = null) {
        observe(execControl.blocking {
            getByNing(requestType, url, data)
        })
    }

    rx.Observable<String> doGet(String url) {
        getObservableNing(RequestType.GET, url)
    }

    rx.Observable<String> doPost(String url, String data) {
        getObservableNing(RequestType.POST, url, data)
    }

    rx.Observable<String> doDelete(String url) {
        getObservableNing(RequestType.DELETE, url)
    }
}
