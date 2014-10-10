package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
import com.sony.ebs.octopus3.commons.ratpack.product.cadc.delta.model.RepoDelta
import com.sony.ebs.octopus3.commons.urn.URN
import com.sony.ebs.octopus3.commons.urn.URNImpl
import com.sony.ebs.octopus3.microservices.flix.model.Flix
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
    NingHttpClient httpClient

    String createOpsRecipe(Map recipeParams) {
        def packageUrnStr = recipeParams["baseUrn"].toString()

        def getZip = {
            it.zip {
                source recipeParams["packageUrnStr"]
            }
        }
        def getRename = {
            it.rename {
                source "${packageUrnStr}.zip"
                destination recipeParams["packageName"]
            }
        }
        def getCopyThirdParty = {
            it.copy {
                source "${packageUrnStr}.zip"
                destination recipeParams["outputUrnStr"]
            }
        }
        def getCopyArchive = {
            it.copy {
                source "${packageUrnStr}.zip"
                destination recipeParams["archiveUrnStr"]
            }
        }
        def getDelete = {
            it.delete {
                source "${packageUrnStr}.zip"
            }
        }

        def builder = new groovy.json.JsonBuilder()
        builder {
            ops getZip(builder), getRename(builder), getCopyThirdParty(builder), getCopyArchive(builder), getDelete(builder)
        }

        def result = builder.toString()
        log.info "recipe for {} is {}", baseUrn, result
        result
    }

    rx.Observable<String> packageFlow(RepoDelta delta, Flix flix) {
        log.info "creating package for {}", delta
        rx.Observable.just("starting").flatMap({
            observe(execControl.blocking {
                def packageName = "Flix_${delta.locale}_${new DateTime().toString(FMT)}.zip"

                def outputUrnStr = FlixUtils.getThirdPartyUrn()?.toString()
                flix.outputUrl = repositoryFileServiceUrl.replace(":urn", outputUrnStr)

                def archiveUrnStr = FlixUtils.getArchiveUrn()?.toString()
                flix.archiveUrl = repositoryFileServiceUrl.replace(":urn", archiveUrnStr)

                def basePackageUrn = new URNImpl(delta.baseUrn, [packageName])?.toString()
                def recipeParams = [
                        baseUrn: delta.baseUrn,
                        outputUrnStr: outputUrnStr,
                        archiveUrnStr: archiveUrnStr,
                        packageName: packageName,
                        basePackageUrn: basePackageUrn
                ]

                createOpsRecipe(recipeParams)
            })
        }).flatMap({ String recipe ->
            httpClient.doPost(repositoryOpsServiceUrl, IOUtils.toInputStream(recipe, EncodingUtil.CHARSET))
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "calling repo ops service", delta.errors)
        }).map({
            "success"
        })
    }

}
