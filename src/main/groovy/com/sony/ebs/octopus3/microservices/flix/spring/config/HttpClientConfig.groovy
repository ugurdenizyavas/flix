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

    @Value('${octopus3.flix.local.proxy.host}')
    String localProxyHost

    @Value('${octopus3.flix.local.proxy.port}')
    int localProxyPort

    @Value('${octopus3.flix.local.proxy.user}')
    String localProxyUser

    @Value('${octopus3.flix.local.proxy.password}')
    String localProxyPassword

    @Value('${octopus3.flix.local.proxy.nonProxyHosts}')
    String localNonProxyHosts

    @Bean
    @Qualifier("localHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient localHttpClient() {
        new NingHttpClient(launchConfig, localProxyHost, localProxyPort, localProxyUser, localProxyPassword, localNonProxyHosts, "", "", 5000, 20000)
    }

}

