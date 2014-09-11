package com.sony.ebs.octopus3.microservices.flix.services.basic

import com.ning.http.client.Response
import com.sony.ebs.octopus3.commons.ratpack.encoding.EncodingUtil
import com.sony.ebs.octopus3.commons.ratpack.http.ning.NingHttpClient
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
class FlixPackageService {

    final static DateTimeFormatter FMT = DateTimeFormat.forPattern("yyyyMMdd_HHmmss")

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    @Value('${octopus3.flix.repositoryOpsServiceUrl}')
    String repositoryOpsServiceUrl

    @Value('${octopus3.flix.repositoryFileServiceUrl}')
    String repositoryFileServiceUrl

    @Autowired
    @Qualifier("localHttpClient")
    NingHttpClient httpClient

    String createOpsRecipe(Flix flix, String outputPackageUrnStr, String archivePackageUrnStr) {
        def packageUrnStr = flix.baseUrn.toString()

        def getZip = {
            it.zip {
                source packageUrnStr
            }
        }
        def getCopyThirdParty = {
            it.copy {
                source "${packageUrnStr}.zip"
                destination outputPackageUrnStr
            }
        }
        def getCopyArchive = {
            it.copy {
                source "${packageUrnStr}.zip"
                destination archivePackageUrnStr
            }
        }
        def getDelete = {
            it.delete {
                source "${packageUrnStr}.zip"
            }
        }

        def builder = new groovy.json.JsonBuilder()
        builder {
            ops getZip(builder), getCopyThirdParty(builder), getCopyArchive(builder), getDelete(builder)
        }

        def result = builder.toString()
        log.info "recipe for {} is {}", flix, result
        result
    }

    rx.Observable<String> packageFlow(Flix flix) {
        log.info "creating package for {}", flix
        rx.Observable.just("starting").flatMap({
            observe(execControl.blocking {
                def packageName = "Flix_${flix.locale}_${new DateTime().toString(FMT)}.zip"

                def outputPackageUrnStr = flix.getThirdPartyUrn(packageName)?.toString()
                flix.outputPackageUrl = repositoryFileServiceUrl.replace(":urn", outputPackageUrnStr)

                def archivePackageUrnStr = flix.getArchiveUrn(packageName)?.toString()
                flix.archivePackageUrl = repositoryFileServiceUrl.replace(":urn", archivePackageUrnStr)

                createOpsRecipe(flix, outputPackageUrnStr, archivePackageUrnStr)
            })
        }).flatMap({ String recipe ->
            httpClient.doPost(repositoryOpsServiceUrl, IOUtils.toInputStream(recipe, EncodingUtil.CHARSET))
        }).filter({ Response response ->
            NingHttpClient.isSuccess(response, "calling repo ops service", flix.errors)
        }).map({
            "success"
        })
    }

}
