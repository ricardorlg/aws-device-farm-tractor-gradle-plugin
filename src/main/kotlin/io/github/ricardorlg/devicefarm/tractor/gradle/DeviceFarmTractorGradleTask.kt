package io.github.ricardorlg.devicefarm.tractor.gradle

import arrow.core.Either
import io.github.ricardorlg.devicefarm.tractor.controller.services.implementations.DefaultDeviceFarmTractorLogger
import io.github.ricardorlg.devicefarm.tractor.factory.DeviceFarmTractorFactory
import io.github.ricardorlg.devicefarm.tractor.model.DeviceFarmTractorError
import kotlinx.coroutines.runBlocking
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import software.amazon.awssdk.services.devicefarm.model.ExecutionResult

abstract class DeviceFarmTractorGradleTask : DefaultTask() {
    init {
        group = "Device farm tractor"
        description = "Run serenity BDD appium tests on AWS Device farm"
    }

    @Input
    @Option(option = "aws.accessKeyId", description = "aws access key")
    var accessKeyId = ""

    @Input
    @Option(option = "aws.secretAccessKey", description = "aws secret access key")
    var secretAccessKey = ""

    @Input
    @Option(option = "aws.sessionToken", description = "aws session token")
    var sessionToken = ""

    @Input
    @Option(option = "aws.region", description = "aws region name")
    var region = ""

    @get:Input
    @set:Option(option = "aws.project.name", description = "device farm project name")
    abstract var projectName: String

    @get:Input
    @set:Option(option = "aws.device.pool", description = "device farm device pool name")
    var devicePool: String = ""

    @get:Input
    @set:Option(option = "aws.app.path", description = "app path to upload to device farm")
    abstract var appPath: String

    @get:Input
    @set:Option(option = "aws.tests.path", description = "test project zip path to upload to device farm")
    abstract var testsProjectPath: String

    @get:Input
    @set:Option(option = "aws.test.spec.path", description = "aws test spec file path")
    abstract var testSpecFilePath: String

    @Input
    @Option(option = "aws.capture.video", description = "enable device farm video capture")
    var captureVideo = true

    @Input
    @Option(option = "aws.run.name", description = "aws device farm test run name to be used")
    var testRunName = ""

    @Input
    @Option(option = "aws.reports.base.dir", description = "base directory path where test results will be stored")
    var testReportsBaseDirectory: String = ""

    @Input
    @Option(option = "aws.download.reports", description = "download test reports, enabled by default")
    var downloadReports = true

    @Input
    @Option(
        option = "aws.clean.uploads",
        description = "clean upload artifacts from device farm, enabled by default"
    )
    var cleanState = true

    @Input
    @Option(option = "aws.strict", description = "throw exceptions on any failure, enabled by default")
    var strictRun = true

    @Input
    @Option(option = "aws.metered", description = "run tests using a metered pricing option, enabled by default")
    var meteredTest = true

    @Input
    @Option(
        option = "aws.disable.app.performance.monitoring",
        description = "disable app performance monitoring in device farm, disabled by default"
    )
    var disableAppPerformanceMonitoring = false

    @Input
    @Option(
        option = "aws.profileName",
        description = "Profile name to be used loading AWS credentials"
    )
    var profileName: String = ""

    private val banner = """
 _______           _______  _          ______  _________ _______ __________________ _______  _          ______   _______          _________ _______  _______    _______  _______  _______  _______ 
(  ___  )|\     /|(  ___  )( \        (  __  \ \__   __/(  ____ \\__   __/\__   __/(  ___  )( \        (  __  \ (  ____ \|\     /|\__   __/(  ____ \(  ____ \  (  ____ \(  ___  )(  ____ )(       )
| (   ) || )   ( || (   ) || (        | (  \  )   ) (   | (    \/   ) (      ) (   | (   ) || (        | (  \  )| (    \/| )   ( |   ) (   | (    \/| (    \/  | (    \/| (   ) || (    )|| () () |
| (___) || |   | || (___) || |        | |   ) |   | |   | |         | |      | |   | (___) || |        | |   ) || (__    | |   | |   | |   | |      | (__      | (__    | (___) || (____)|| || || |
|  ___  |( (   ) )|  ___  || |        | |   | |   | |   | | ____    | |      | |   |  ___  || |        | |   | ||  __)   ( (   ) )   | |   | |      |  __)     |  __)   |  ___  ||     __)| |(_)| |
| (   ) | \ \_/ / | (   ) || |        | |   ) |   | |   | | \_  )   | |      | |   | (   ) || |        | |   ) || (       \ \_/ /    | |   | |      | (        | (      | (   ) || (\ (   | |   | |
| )   ( |  \   /  | )   ( || (____/\  | (__/  )___) (___| (___) |___) (___   | |   | )   ( || (____/\  | (__/  )| (____/\  \   /  ___) (___| (____/\| (____/\  | )      | )   ( || ) \ \__| )   ( |
|/     \|   \_/   |/     \|(_______/  (______/ \_______/(_______)\_______/   )_(   |/     \|(_______/  (______/ (_______/   \_/   \_______/(_______/(_______/  |/       |/     \||/   \__/|/     \|

With love from ricardorlg
---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
"""

    @TaskAction
    fun execute() {
        runBlocking {
            val logger = DefaultDeviceFarmTractorLogger("Device Farm Tractor")
            val runner = DeviceFarmTractorFactory.createRunner(
                logger = logger,
                accessKeyId = accessKeyId,
                secretAccessKey = secretAccessKey,
                sessionToken = sessionToken,
                region = region,
                profileName = profileName
            )
            logger.logMessage("\r\n" + banner)
            when (runner) {
                is Either.Left -> {
                    if (strictRun) {
                        throw GradleException("There was an error creating the tractor runner", runner.value)
                    } else {
                        logger.logError(runner.value, "There was an error creating the tractor runner")
                    }
                }
                is Either.Right -> {
                    kotlin.runCatching {
                        runner
                            .value
                            .runTests(
                                projectName = projectName,
                                devicePoolName = devicePool,
                                appPath = appPath,
                                testProjectPath = testsProjectPath,
                                testSpecPath = testSpecFilePath,
                                captureVideo = captureVideo,
                                runName = testRunName,
                                testReportsBaseDirectory = testReportsBaseDirectory,
                                downloadReports = downloadReports,
                                cleanStateAfterRun = cleanState,
                                meteredTests = meteredTest,
                                disablePerformanceMonitoring = disableAppPerformanceMonitoring
                            )
                    }.fold(
                        onFailure = {
                            when {
                                strictRun -> when (it) {
                                    is DeviceFarmTractorError -> throw GradleException(it.message, it.cause)
                                    else -> throw GradleException("There was an error in the test execution", it)
                                }
                                it is DeviceFarmTractorError -> logger.logError(it.cause, it.message)
                                else -> logger.logError(it, it.message ?: "There was an error in the text execution")
                            }
                        },
                        onSuccess = {
                            logger.logMessage("Test execution has been completed")
                            val devicesResultsTable = runner.value.getDeviceResultsTable(it)
                            if (devicesResultsTable.isNotEmpty())
                                logger.logMessage("\r\n" + devicesResultsTable)
                            if (it.result() != ExecutionResult.PASSED) {
                                if (strictRun)
                                    throw GradleException("Tests result was not success - actual result = ${it.result()}")
                                else
                                    logger.logError(msg = "Tests result was not success - actual result = ${it.result()}")
                            } else {
                                logger.logMessage("Test execution has successfully finished")
                            }
                        }
                    )
                }
            }
        }
    }
}