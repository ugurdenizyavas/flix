package com.sony.ebs.octopus3.microservices.flix.services

import com.sony.ebs.octopus3.commons.date.ISODateUtils
import com.sony.ebs.octopus3.commons.file.FileUtils
import com.sony.ebs.octopus3.microservices.flix.model.Flix
import groovy.util.logging.Slf4j
import org.joda.time.DateTime
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import ratpack.exec.ExecControl

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.BasicFileAttributes

import static ratpack.rx.RxRatpack.observe

@Slf4j
@Service
public class DateParamsProvider {

    @Value('${octopus3.flix.storageFolder}')
    String storageFolder

    @Autowired
    @org.springframework.context.annotation.Lazy
    ExecControl execControl

    private Path createLastModifiedPath(Flix flix) {
        Paths.get("$storageFolder/${flix?.lastModifiedUrn?.toPath()}")
    }

    private String getLastModifiedTime(Path path) {
        if (Files.exists(path)) {
            def lastModifiedTime = Files.readAttributes(path, BasicFileAttributes.class)?.lastModifiedTime()?.toMillis()
            def str = ISODateUtils.toISODateString(new DateTime(lastModifiedTime))
            log.info "lastModifiedTime for $path is $str"
            str
        } else {
            log.info "$path doesnot exist"
            null
        }
    }

    rx.Observable<String> updateLastModified(Flix flix) {
        observe(execControl.blocking {
            def path = createLastModifiedPath(flix)
            log.info "starting update last modified time for $flix"
            FileUtils.writeFile(path, "", true, true)
            def lmt = getLastModifiedTime(path)
            log.info "finished update last modified time for $flix as $lmt"
            lmt
        })
    }

    rx.Observable<String> createDateParams(Flix flix) {
        observe(execControl.blocking {
            def sb = new StringBuilder()

            def sdate = flix.sdate
            if (!sdate) {
                sdate = getLastModifiedTime(createLastModifiedPath(flix))
            }
            if (sdate) {
                sb.append("?sdate=").append(sdate)
            }
            if (!flix.edate) {
                sb.size() == 0 ? sb.append("?") : "&"
                sb.append("edate=").append(flix.edate)
            }
            sb.toString()
        })
    }

}
