package com.sony.ebs.octopus3.microservices.flix.spring.config

import com.hazelcast.client.HazelcastClient
import com.hazelcast.client.config.ClientConfig
import com.hazelcast.core.HazelcastInstance
import com.sony.ebs.octopus3.commons.ratpack.file.FileAttributesProvider
import com.sony.ebs.octopus3.commons.ratpack.file.ResponseStorage
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaResultService
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.service.DeltaUrlHelper
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.validator.RequestValidator
import com.sony.ebs.octopus3.commons.ratpack.product.enhancer.CategoryEnhancer
import com.sony.ebs.octopus3.commons.ratpack.product.enhancer.EanCodeEnhancer
import com.sony.ebs.octopus3.commons.ratpack.product.filtering.CategoryService
import groovy.util.logging.Slf4j
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

    @Bean
    @org.springframework.context.annotation.Lazy
    public EanCodeEnhancer eanCodeEnhancer(
            ExecControl execControl,
            @Value('${octopus3.flix.octopusIdentifiersServiceUrl}') String octopusIdentifiersServiceUrl,
            @Qualifier("externalHttpClient") Oct3HttpClient externalHttpClient
    ) {
        new EanCodeEnhancer(execControl: execControl,
                serviceUrl: octopusIdentifiersServiceUrl,
                httpClient: externalHttpClient)
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public FileAttributesProvider attributesProvider(
            ExecControl execControl,
            @Value('${octopus3.flix.repositoryFileAttributesServiceUrl}') String repositoryFileAttributesServiceUrl,
            @Qualifier("externalHttpClient") Oct3HttpClient externalHttpClient
    ) {
        new FileAttributesProvider(execControl: execControl,
                repositoryFileAttributesServiceUrl: repositoryFileAttributesServiceUrl,
                httpClient: externalHttpClient)
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public DeltaUrlHelper deltaUrlHelper(
            ExecControl execControl,
            FileAttributesProvider fileAttributesProvider,
            @Value('${octopus3.flix.repositoryFileServiceUrl}') String repositoryFileServiceUrl,
            @Qualifier("externalHttpClient") Oct3HttpClient externalHttpClient
    ) {
        new DeltaUrlHelper(execControl: execControl,
                repositoryFileServiceUrl: repositoryFileServiceUrl,
                httpClient: externalHttpClient,
                fileAttributesProvider: fileAttributesProvider
        )
    }

    @Bean
    @Qualifier('fileStorage')
    @org.springframework.context.annotation.Lazy
    public ResponseStorage responseStorage(
            @Value('${octopus3.flix.repositoryFileServiceUrl}') String repositoryFileServiceUrl,
            @Qualifier("externalHttpClient") Oct3HttpClient externalHttpClient
    ) {
        new ResponseStorage(
                httpClient: externalHttpClient,
                saveUrl: repositoryFileServiceUrl
        )
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public CategoryService categoryService(
            ExecControl execControl,
            @Value('${octopus3.flix.repositoryFileServiceUrl}') String repositoryFileServiceUrl,
            @Qualifier("internalHttpClient") Oct3HttpClient internalHttpClient,
            @Value('${octopus3.flix.octopusCategoryServiceUrl}') String octopusCategoryServiceUrl
    ) {
        new CategoryService(execControl: execControl,
                repositoryFileServiceUrl: repositoryFileServiceUrl,
                octopusCategoryServiceUrl: octopusCategoryServiceUrl,
                httpClient: internalHttpClient)
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public static CategoryEnhancer categoryEnhancer(
            ExecControl execControl,
            @Qualifier('internalHttpClient') Oct3HttpClient httpClient,
            @Value('${octopus3.flix.octopusCategoryServiceUrl}') String octopusCategoryServiceUrl
    ) {
        new CategoryEnhancer(
                execControl: execControl,
                httpClient: httpClient,
                categoryServiceUrl: octopusCategoryServiceUrl
        )
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public static HazelcastInstance hazelcastClient(
            @Value('${hz.group.name}') def name,
            @Value('${hz.group.password}') def password,
            @Value('${hz.network.members}') def networkMembers
    ) {
        if (name) {
            ClientConfig clientConfig = new ClientConfig()
            clientConfig.getGroupConfig().setName(name).setPassword(password)
            clientConfig.getNetworkConfig().addAddress(networkMembers?.split(","))
            try {
                return HazelcastClient.newHazelcastClient(clientConfig)
            } catch (Exception e) {
                log.warn "no hazelcast instance"
                return null
            }
        } else {
            null
        }
    }

    @Bean
    @org.springframework.context.annotation.Lazy
    public DeltaResultService deltaResultService() {
        new DeltaResultService()
    }

    @Bean
    public RequestValidator requestValidator() {
        new RequestValidator()
    }

}

