package com.sony.ebs.octopus3.microservices.flix.spring.config

import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.core.HazelcastInstance
import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.commons.ratpack.product.enhancer.EanCodeEnhancer
import com.sony.ebs.octopus3.commons.ratpack.product.filtering.CategoryService
import com.sony.ebs.octopus3.commons.ratpack.product.filtering.EanCodeService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.PropertySource
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import ratpack.exec.ExecControl

@Slf4j
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
    Oct3HttpClient externalHttpClient

    @Autowired
    @Qualifier("internalHttpClient")
    @org.springframework.context.annotation.Lazy
    Oct3HttpClient internalHttpClient

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
    public ResponseStorage responseStorage() {
        new ResponseStorage(
                httpClient: externalHttpClient,
                saveUrl: repositoryFileServiceUrl
        )
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public CategoryService categoryService(
            @Value('${octopus3.flix.octopusCategoryServiceUrl}') String octopusCategoryServiceUrl
    ) {
        new CategoryService(execControl: execControl,
                repositoryFileServiceUrl: repositoryFileServiceUrl,
                octopusCategoryServiceUrl: octopusCategoryServiceUrl,
                httpClient: internalHttpClient)
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public EanCodeService eanCodeService(
            @Value('${octopus3.flix.octopusEanCodeServiceUrl}') String octopusEanCodeServiceUrl
    ) {
        new EanCodeService(execControl: execControl,
                octopusEanCodeServiceUrl: octopusEanCodeServiceUrl,
                httpClient: internalHttpClient)
    }

    @Bean
    @Qualifier('hazelcastClient')
    @org.springframework.context.annotation.Lazy
    public static HazelcastInstance hazelcastClient(
            @Value('${hz.group.name}') def name,
            @Value('${hz.group.password}') def password,
            @Value('${hz.network.members}') def networkMembers
    ) {
        ClientConfig clientConfig = new ClientConfig()
        clientConfig.getGroupConfig().setName(name).setPassword(password)
        clientConfig.getNetworkConfig().addAddress(networkMembers?.split(","))
        try {
            HazelcastClient.newHazelcastClient(clientConfig)
        } catch (Exception e) {
            log.warn "no hazelcast instance"
            null
        }
    }
}

