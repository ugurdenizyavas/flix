package com.sony.ebs.octopus3.microservices.flix.service

import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpClient
import com.sony.ebs.octopus3.commons.ratpack.http.Oct3HttpResponse
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.DeltaResult
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.urn.URNImpl
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
@org.springframework.context.annotation.Lazy
class PackageService {

    final static DateTimeFormatter FMT = DateTimeFormat.forPattern("yyyyMMdd_HHmmss")

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.flix.repositoryOpsServiceUrl}')
    String repositoryOpsServiceUrl

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    @Qualifier("internalHttpClient")
    Oct3HttpClient httpClient

    String createOpsRecipe(Map recipeParams, boolean upload) {
        def getZip = {
            it.zip {
                source recipeParams["baseUrnStr"]
            }
        }
        def getRename = {
            it.rename {
                source "${recipeParams["baseUrnStr"]}.zip"
                targetName recipeParams["packageName"]
            }
        }
        def getCopyThirdParty = {
            it.copy {
                source recipeParams["basePackageUrnStr"]
                destination recipeParams["outputUrnStr"]
            }
        }
        def getCopyArchive = {
            it.copy {
                source recipeParams["basePackageUrnStr"]
                destination recipeParams["archiveUrnStr"]
            }
        }
        def getDelete = {
            it.delete {
                source recipeParams["basePackageUrnStr"]
            }
        }

        def builder = new groovy.json.JsonBuilder()
        if (upload) {
            builder {
                ops getZip(builder), getRename(builder), getCopyThirdParty(builder), getCopyArchive(builder), getDelete(builder)
            }
        } else {
            builder {
                ops getZip(builder), getRename(builder), getCopyArchive(builder), getDelete(builder)
            }
        }

        def result = builder.toString()
        log.info "recipe for {} is {}", recipeParams["baseUrnStr"], result
        result
    }

    rx.Observable<String> processPackage(RepoDelta delta, DeltaResult deltaResult) {
        log.info "creating package for {}", delta
        rx.Observable.just("starting").flatMap({
            observe(execControl.blocking {
                def packageName = "Flix_${delta.locale}_${new DateTime().toString(FMT)}.zip"

                def outputUrnStr = FlixUtils.getThirdPartyUrn()?.toString()
                if (delta.upload) {
                    deltaResult.other.outputPackageUrl = repositoryFileServiceUrl.replace(":urn", FlixUtils.getThirdPartyPackageUrn(packageName)?.toString())
                }

                def archiveUrnStr = FlixUtils.getArchiveUrn()?.toString()
                deltaResult.other.archivePackageUrl = repositoryFileServiceUrl.replace(":urn", FlixUtils.getArchivePackageUrn(packageName)?.toString())

                def basePackageUrnStr = new URNImpl(delta.type.toString(), [delta.publication, packageName])?.toString()
                def recipeParams = [
                        baseUrnStr       : delta.baseUrn?.toString(),
                        outputUrnStr     : outputUrnStr,
                        archiveUrnStr    : archiveUrnStr,
                        packageName      : packageName.toLowerCase(),
                        basePackageUrnStr: basePackageUrnStr
                ]

                createOpsRecipe(recipeParams, delta.upload)
            })
        }).flatMap({ String recipe ->
            httpClient.doPost(repositoryOpsServiceUrl, IOUtils.toInputStream(recipe, EncodingUtil.CHARSET))
        }).filter({ Oct3HttpResponse response ->
            response.isSuccessful("calling repo ops service", deltaResult.errors)
        }).map({
            "success"
        })
    }

}
