package com.sony.ebs.octopus3.microservices.flix.services

import groovy.util.logging.Slf4j
import groovy.xml.StreamingMarkupBuilder
import org.springframework.stereotype.Service

@Slf4j
@Service
class FlixXmlBuilder {

    String cleanNonAlphanumericChars(val) {
        return "$val".replaceAll("\\P{Alnum}", "")
    }

    String buildXml(Object json) {
        def generate
        generate = { builder, Map map ->
            map.each { k, v ->
                if (v instanceof Map) {
                    builder."${cleanNonAlphanumericChars(k)}" {
                        generate(it, v)
                    }
                } else if (v instanceof List) {
                    builder."${cleanNonAlphanumericChars(k)}" {
                        v.each { item ->
                            builder.item {
                                mkp.yieldUnescaped("<![CDATA[$item]]>")
                            }
                        }
                    }
                } else {
                    builder."${cleanNonAlphanumericChars(k)}" {
                        mkp.yieldUnescaped("<![CDATA[$v]]>")
                    }
                }
            }
        }

        def builder = new StreamingMarkupBuilder()
        def xml = builder.bind() {
            mkp.xmlDeclaration()
            //mkp.yieldUnescaped('<!DOCTYPE gsafeed PUBLIC "-//Google//DTD GSA Feeds//EN" "gsafeed.dtd">')
            sheet {
                generate(it, json)
            }
        }

        String result = xml.toString()
        log.info result
        result
    }

}
