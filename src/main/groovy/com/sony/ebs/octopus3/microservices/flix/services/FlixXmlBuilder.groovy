package com.sony.ebs.octopus3.microservices.flix.services

import groovy.util.logging.Slf4j
import groovy.xml.MarkupBuilder
import org.springframework.stereotype.Service

@Slf4j
@Service
class FlixXmlBuilder {

    String buildXml(Object json) {

        def generate
        generate = { builder, Map map ->
            map.each { k, v ->
                if (v instanceof Map) {
                    builder."$k" {
                        generate(builder, v)
                    }
                } else if (v instanceof List) {
                    v.each {
                        builder."$k" it
                    }
                } else {
                    builder."$k" v
                }
            }
        }

        def writer = new StringWriter()
        def builder = new MarkupBuilder(writer)

        builder.content {
            generate(builder, json)
        }

        String result = writer.toString()
        log.debug result
        result
    }

}
