package com.sony.ebs.octopus3.microservices.flix

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import ratpack.exec.ExecControl

@Configuration
@ComponentScan(value = "com.sony.ebs.octopus3.microservices.flix")
@PropertySource(value = ['classpath:/default.properties', 'classpath:/${environment}.properties'], ignoreResourceNotFound = true)
class SpringConfig {

    @Bean
    public static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
        return new PropertySourcesPlaceholderConfigurer();
    }

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Bean
    @Qualifier("proxyHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient cadcHttpClient() {
        new NingHttpClient(execControl, proxyHost, proxyPort, proxyUser, proxyPassword, "", "")
    }

    @Value('${octopus3.flix.proxyHost}')
    String proxyHost

    @Value('${octopus3.flix.proxyPort}')
    int proxyPort

    @Value('${octopus3.flix.proxyUser}')
    String proxyUser

    @Value('${octopus3.flix.proxyPassword}')
    String proxyPassword


    @Bean
    @Qualifier("localHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient localHttpClient() {
        new NingHttpClient(execControl, localProxyHost, localProxyPort, localProxyUser, localProxyPassword, "", "")
    }

    @Value('${octopus3.flix.localProxyHost}')
    String localProxyHost

    @Value('${octopus3.flix.localProxyPort}')
    int localProxyPort

    @Value('${octopus3.flix.localProxyUser}')
    String localProxyUser

    @Value('${octopus3.flix.localProxyPassword}')
    String localProxyPassword

}

