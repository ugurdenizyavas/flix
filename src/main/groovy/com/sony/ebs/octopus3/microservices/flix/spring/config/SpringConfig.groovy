package com.sony.ebs.octopus3.microservices.flix.spring.config

import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
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

    @Value('${octopus3.flix.octopusEanCodeServiceUrl}')
    String octopusEanCodeServiceUrl

    @Autowired
    @Qualifier("externalHttpClient")
    @org.springframework.context.annotation.Lazy
    NingHttpClient externalHttpClient

    @Autowired
    @Qualifier("internalHttpClient")
    @org.springframework.context.annotation.Lazy
    NingHttpClient internalHttpClient

    @Value('${octopus3.flix.octopusIdentifiersServiceUrl}')
    String octopusIdentifiersServiceUrl

    @Bean
    @org.springframework.context.annotation.Lazy
    public EanCodeEnhancer eanCodeEnhancer() {
        new EanCodeEnhancer(execControl: execControl,
                serviceUrl: octopusIdentifiersServiceUrl,
                httpClient: externalHttpClient)
    }

    @Value('${octopus3.flix.repositoryFileAttributesServiceUrl}')
    String repositoryFileAttributesServiceUrl

    @Bean
    @org.springframework.context.annotation.Lazy
    public FileAttributesProvider attributesProvider() {
        new FileAttributesProvider(execControl: execControl,
                repositoryFileAttributesServiceUrl: repositoryFileAttributesServiceUrl,
                httpClient: externalHttpClient)
    }

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Bean
    @org.springframework.context.annotation.Lazy
    public DeltaUrlHelper deltaUrlHelper() {
        new DeltaUrlHelper(execControl: execControl,
                repositoryFileServiceUrl: repositoryFileServiceUrl,
                httpClient: externalHttpClient,
                fileAttributesProvider: attributesProvider()
        )
    }

    @Bean
    public RequestValidator requestValidator() {
        new RequestValidator()
    }


    @Bean
    @Qualifier('fileStorage')
    @org.springframework.context.annotation.Lazy
    public ResponseStorage responseStorage(
            @Value('${octopus3.flix.repositoryFileServiceUrl}') String saveUrl) {
        new ResponseStorage(
                ningHttpClient: externalHttpClient,
                saveUrl: saveUrl
        )
    }

}

