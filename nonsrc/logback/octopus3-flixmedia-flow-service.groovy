import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.classic.AsyncAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy
import ch.qos.logback.classic.filter.ThresholdFilter
import ch.qos.logback.core.filter.EvaluatorFilter
//import ch.qos.logback.classic.boolex.JaninoEventEvaluator
import ch.qos.logback.classic.boolex.GEventEvaluator

import static ch.qos.logback.classic.Level.*
import static ch.qos.logback.core.spi.FilterReply.*

//***********************************
// Initialization
//***********************************

scan()
println "[LOGBACK] Log files are loaded in every 1 minute"

logFormat = "%d %5p %-25t %-10c{1}: %m%n"
//logFormat = "%d{yyyy.MM.dd HH:mm:ss.SSS} %-5p %-40c{1} - %m%n"
println "[LOGBACK] Log format is ${logFormat}"

environment = System.getProperty("environment")
println "[LOGBACK] Environment is ${environment}"

logDirectory = System.getProperty("logDirectory") ?: System.getProperty("java.io.tmpdir")
println "[LOGBACK] Logging directory is ${logDirectory}"

//***********************************
// Log Level Configurations
//***********************************

// Create the appenders
createStandardAppender("defaultAppender", "output", false)
createStandardAppender("activityAppender", "activity", false)
createStandardAppender("errorsAppender", "errors", false, ERROR)
createStandardAppender("healthcheckAppender", "healthcheck", true)

// Create the loggers
switch (environment) {
    case "production":
        root(INFO, ["defaultAppender", "errorsAppender"])
        break
    case "dev":
        root(INFO, ["defaultAppender", "errorsAppender"])
        break
    case "local":
        createConsoleAppender()
        root(DEBUG, ["consoleAppender", "defaultAppender", "errorsAppender"])
        break
    default:
        root(INFO, ["defaultAppender", "errorsAppender"])
        break
}
logger("activity", INFO, ["activityAppender"])
logger("healthcheck", DEBUG, ["healthcheckAppender"])
logger("org.springframework", ERROR)
logger("com.ning", ERROR)

//***********************************
// Console appender
//***********************************
def createConsoleAppender() {
    def format = logFormat
    appender("consoleAppender", ConsoleAppender) {
        encoder(PatternLayoutEncoder) {
            pattern = "$format"
        }
    }
}

//***********************************
// Standard Appender
//***********************************
def createStandardAppender(String appenderName, String fileName, boolean isHealthCheck, thresholdFilterLevel = null) {
    def dir = logDirectory
    def format = logFormat
    println "Adding appender ${appenderName} with file name ${fileName} in directory ${dir}"
    def rollingAppenderName = appenderName + "-roll"
    appender(rollingAppenderName, RollingFileAppender) {
        file = "${dir}/${fileName}.log"
        encoder(PatternLayoutEncoder) {
            pattern = "$format"
        }
        rollingPolicy(FixedWindowRollingPolicy) {
            maxIndex = 4
            fileNamePattern = "${dir}/${fileName}-%i.log"
        }
        triggeringPolicy(SizeBasedTriggeringPolicy) {
            maxFileSize = "100000KB"
        }
        if (thresholdFilterLevel) {
            filter(ThresholdFilter) {
                level = thresholdFilterLevel //.toString()
            }
        }
        if (!isHealthCheck) {
            filter(EvaluatorFilter) {
                evaluator(GEventEvaluator) {
                    expression = "event.loggerName == 'healthcheck'"
                }
                onMismatch  = NEUTRAL
                onMatch  = DENY
            }
        }

    }
    appender(appenderName, AsyncAppender) {
        appenderRef(rollingAppenderName)
    }
}
