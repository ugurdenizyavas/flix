package com.sony.ebs.octopus3.microservices.flix

import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.enhancer.EanCodeEnhancer
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import ratpack.exec.ExecControl
import ratpack.launch.LaunchConfig

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

    @Autowired
    @org.springframework.context.annotation.Lazy
    LaunchConfig launchConfig

    @Bean
    @Qualifier("localHttpClient")
    @org.springframework.context.annotation.Lazy
    public NingHttpClient localHttpClient() {
        new NingHttpClient(launchConfig, localProxyHost, localProxyPort, localProxyUser, localProxyPassword, localNonProxyHosts, "", "")
    }

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

    @Value('${octopus3.flix.eanCodeUrl}')
    String eanCodeUrl

    @Bean
    @Qualifier("eanCodeEnhancer")
    @org.springframework.context.annotation.Lazy
    public EanCodeEnhancer eanCodeEnhancer() {
        new EanCodeEnhancer(execControl: execControl, serviceUrl: eanCodeUrl, httpClient: localHttpClient())
    }

}

