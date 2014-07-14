package com.sony.ebs.octopus3.microservices.flix

import com.sony.ebs.octopus3.microservices.flix.http.NingHttpClient
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
        new NingHttpClient(execControl, "", 0, "", "", "", "")
    }

}

