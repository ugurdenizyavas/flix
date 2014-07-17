package com.sony.ebs.octopus3.microservices.flix.services.sub

import groovy.util.logging.Slf4j
import groovy.xml.StreamingMarkupBuilder
import org.springframework.stereotype.Service

@Slf4j
@Service
class FlixXmlBuilder {

    def nonAlphanumeric = {
        "$it".replaceAll("\\P{Alnum}", "")
    }

    def cdata = {
        "<![CDATA[${it ?: ''}]]>"
    }

    String buildXml(Object json) {
        def generate
        generate = { builder, Map map ->
            map.each { k, v ->
                if (v instanceof Map) {
                    builder."${nonAlphanumeric(k)}" {
                        generate(it, v)
                    }
                } else if (v instanceof List) {
                    builder."${nonAlphanumeric(k)}" {
                        v.each { item ->
                            generate(it, [item: item])
                        }
                    }
                } else {
                    builder."${nonAlphanumeric(k)}" {
                        mkp.yieldUnescaped(cdata(v))
                    }
                }
            }
        }

        def builder = new StreamingMarkupBuilder()
        def xml = builder.bind() {
            mkp.xmlDeclaration()
            //mkp.yieldUnescaped('<!DOCTYPE gsafeed PUBLIC "-//Google//DTD GSA Feeds//EN" "gsafeed.dtd">')
            product(xmlns: "http://www.sony.eu/syndication") {
                generate(it, json)
            }
        }

        String result = xml.toString()
        log.debug result
        result
    }

}
