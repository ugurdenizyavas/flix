import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.core.ConsoleAppender
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy
import ch.qos.logback.core.rolling.RollingFileAppender
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy

import static ch.qos.logback.classic.Level.*

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

defaultLevel = INFO
println "[LOGBACK] Default logging level is ${defaultLevel}"

//***********************************
// Log Level Configurations
//***********************************

// Create the appenders. These will all be rolling file appenders.
createConsoleAppender()
createStandardAppender("defaultAppender", "output")
createStandardAppender("activityAppender", "activity")

// Create the loggers
root(defaultLevel, ["consoleAppender", "defaultAppender"])
logger("activity", DEBUG, ["activityAppender"])
logger("com.sony.ebs.octopus3.commons.ratpack.product.enhancer.EanCodeEnhancer", DEBUG)

if (environment == "production") {
    root(INFO, ["consoleAppender", "defaultAppender"])
}

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
def createStandardAppender(String appenderName, String fileName) {
    def dir = logDirectory
    def format = logFormat
    println "Adding appender ${appenderName} with file name ${fileName} in directory ${dir}"
    appender(appenderName, RollingFileAppender) {
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
    }
}

