package com.sony.ebs.octopus3.microservices.flix.spring.config

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import ratpack.launch.LaunchConfig

@Configuration
@org.springframework.context.annotation.Lazy
class HttpClientConfig {

    @Autowired
    LaunchConfig launchConfig

    @Value('${octopus3.flix.external.proxy.host}')
    String externalProxyHost

    @Value('${octopus3.flix.external.proxy.port}')
    int externalProxyPort

    @Value('${octopus3.flix.external.proxy.user}')
    String externalProxyUser

    @Value('${octopus3.flix.external.proxy.password}')
    String externalProxyPassword

    @Value('${octopus3.flix.external.proxy.nonProxyHosts}')
    String externalNonProxyHosts

    @Value('${octopus3.flix.internal.proxy.host}')
    String internalProxyHost

    @Value('${octopus3.flix.internal.proxy.port}')
    int internalProxyPort

    @Value('${octopus3.flix.internal.proxy.user}')
    String internalProxyUser

    @Value('${octopus3.flix.internal.proxy.password}')
    String internalProxyPassword

    @Value('${octopus3.flix.internal.proxy.nonProxyHosts}')
    String internalNonProxyHosts

    @Bean
    @Qualifier("externalHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient externalHttpClient() {
        new NingHttpClient(launchConfig, externalProxyHost, externalProxyPort, externalProxyUser, externalProxyPassword, externalNonProxyHosts, "", "", 5000, 60000)
    }

    @Bean
    @Qualifier("internalHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient internalHttpClient() {
        new NingHttpClient(launchConfig, internalProxyHost, internalProxyPort, internalProxyUser, internalProxyPassword, internalNonProxyHosts, "", "", 5000, 60000)
    }

}

