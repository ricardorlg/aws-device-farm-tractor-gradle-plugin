package io.github.ricardorlg.devicefarm.tractor.gradle


import arrow.core.left
import arrow.core.right
import io.github.ricardorlg.devicefarm.tractor.factory.DeviceFarmTractorFactory
import io.github.ricardorlg.devicefarm.tractor.runner.DeviceFarmTractorRunner
import io.mockk.*
import mu.KLogger
import mu.KotlinLogging
import org.assertj.core.api.Assertions.*
import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import software.amazon.awssdk.services.devicefarm.model.ExecutionResult
import software.amazon.awssdk.services.devicefarm.model.Run
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createFile
import kotlin.io.path.writeText

@ExperimentalPathApi
class DeviceFarmTractorGradlePluginTest {

    private val buildGradleContentWithMandatoryParametersAndStrictRun = """  plugins {
            id 'io.github.ricardorlg.DeviceFarmTractorGradlePlugin'
        }
        runAwsTests{
        accessKeyId = accessKeyId
        secretAccessKey = secretAccessKey
        region = 'us-west-2'
        projectName='test project'
        appPath = 'users/test-app.apk'
        testSpecFilePath='users/test-spec-file.apk'
        testsProjectPath='users/test-project.zip'
        }
        """.trimMargin()

    private val buildGradleWithMandatoryParametersAndStrictRunDisable = """  plugins {
            id 'io.github.ricardorlg.DeviceFarmTractorGradlePlugin'
        }
        runAwsTests{
        accessKeyId = accessKeyId
        secretAccessKey = secretAccessKey
        region = 'us-west-2'
        strictRun = false
        projectName='test project'
        appPath = 'users/test-app.apk'
        testSpecFilePath='users/test-spec-file.apk'
        testsProjectPath='users/test-project.zip'
        }
        """.trimMargin()

    private val projectName = "test project"
    private val appPath = "user/app.apk"
    private val testsProjectPath = "src/project.zip"
    private val testSpecFilePath = "src/specfile.yaml"


    @Test
    fun `test plugin is added to project`() {
        //GIVEN
        val project = ProjectBuilder.builder().build()

        //WHEN
        project.pluginManager.apply("io.github.ricardorlg.DeviceFarmTractorGradlePlugin")

        //THEN
        assertThat(project.tasks.map { it.name }).containsOnly("runAwsTests")
        assertThat(project.tasks.getByName("runAwsTests")).isInstanceOf(DeviceFarmTractorGradleTask::class.java)
        assertThat(project.tasks.getByName("runAwsTests").group).isEqualTo("Device farm tractor")
    }

    @Test
    fun `when no mandatory parameters are configured, a build error should happens`(@TempDir testProjectDir: Path) {
        //GIVEN
        val settingsFile = testProjectDir.resolve("settings.gradle").createFile()
        val buildGradleFile = testProjectDir.resolve("build.gradle").createFile()
        settingsFile.writeText("rootProject.name = 'test-tractor-plugin'")
        buildGradleFile.writeText(
            """  plugins {
            id 'io.github.ricardorlg.DeviceFarmTractorGradlePlugin'
        }
        """.trimMargin()
        )

        //WHEN
        val error = catchThrowable {
            GradleRunner
                .create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments("runAwsTests")
                .withPluginClasspath()
                .build()
        }

        //THEN
        assertThat(error)
            .isInstanceOf(UnexpectedBuildFailure::class.java)
            .hasMessageContaining("Build failed with an exception.")
            .hasMessageContaining("property 'appPath' doesn't have a configured value")
            .hasMessageContaining("property 'projectName' doesn't have a configured value")
            .hasMessageContaining("property 'testSpecFilePath' doesn't have a configured value")
            .hasMessageContaining("property 'testsProjectPath' doesn't have a configured value")
    }

    @Test
    fun `when an error happens in the task execution and strict run is used, the build should fails`(@TempDir testProjectDir: Path) {
        //GIVEN
        val settingsFile = testProjectDir.resolve("settings.gradle").createFile()
        val buildGradleFile = testProjectDir.resolve("build.gradle").createFile()
        settingsFile.writeText("rootProject.name = 'test-tractor-plugin'")
        buildGradleFile.writeText(buildGradleContentWithMandatoryParametersAndStrictRun)

        //WHEN
        val error = catchThrowable {
            GradleRunner
                .create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments("runAwsTests")
                .withPluginClasspath()
                .build()
        }

        //THEN
        assertThat(error)
            .isInstanceOf(UnexpectedBuildFailure::class.java)
            .hasMessageContaining("Build failed with an exception.")
            .hasMessageContaining("There was an error fetching projects from AWS")
    }

    @Test
    fun `when an error happens in the task execution and strict run is disable, the task should not fail`(@TempDir testProjectDir: Path) {
        //GIVEN
        val settingsFile = testProjectDir.resolve("settings.gradle").createFile()
        val buildGradleFile = testProjectDir.resolve("build.gradle").createFile()
        settingsFile.writeText("rootProject.name = 'test-tractor-plugin'")
        buildGradleFile.writeText(buildGradleWithMandatoryParametersAndStrictRunDisable)

        assertThatCode {
            GradleRunner
                .create()
                .withProjectDir(testProjectDir.toFile())
                .withArguments("runAwsTests")
                .withPluginClasspath()
                .build()

        }.doesNotThrowAnyException()
    }

    @Test
    fun `when test execution is not success and strict run is enable the build should fails`() {
        //GIVEN
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ricardorlg.DeviceFarmTractorGradlePlugin")
        val pluginTask = project.tasks.getByName("runAwsTests") as DeviceFarmTractorGradleTask
        pluginTask.projectName = projectName
        pluginTask.appPath = appPath
        pluginTask.testsProjectPath = testsProjectPath
        pluginTask.testSpecFilePath = testSpecFilePath

        //MOCKS
        val result = Run.builder().result(ExecutionResult.FAILED).build()
        val runner = mockk<DeviceFarmTractorRunner>()
        coEvery { runner.getDeviceResultsTable(any()) } returns ""
        coEvery {
            runner.runTests(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns result
        mockkObject(DeviceFarmTractorFactory)
        coEvery {
            DeviceFarmTractorFactory.createRunner(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns runner.right()


        //WHEN
        val executionError = catchThrowable { pluginTask.execute() }

        //THEN
        assertThat(executionError)
            .isInstanceOf(GradleException::class.java)
            .hasMessage("Tests result was not success - actual result = ${ExecutionResult.FAILED}")

        coVerify {
            runner.runTests(
                projectName = projectName,
                "",
                appPath = appPath,
                testProjectPath = testsProjectPath,
                testSpecPath = testSpecFilePath
            )
            runner.getDeviceResultsTable(result)
        }
        confirmVerified(runner)
        clearMocks(runner)
        unmockkObject(DeviceFarmTractorFactory)

    }

    @Test
    fun `when test execution is not success and strict run is disabled the build should not fails`() {
        //GIVEN
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ricardorlg.DeviceFarmTractorGradlePlugin")
        val pluginTask = project.tasks.getByName("runAwsTests") as DeviceFarmTractorGradleTask
        pluginTask.projectName = projectName
        pluginTask.appPath = appPath
        pluginTask.testsProjectPath = testsProjectPath
        pluginTask.testSpecFilePath = testSpecFilePath
        pluginTask.strictRun = false

        //MOCKS
        val mockLogger = mockk<KLogger>()
        val errorMessageSlot = slot<() -> Any?>()
        every { mockLogger.error(any<Throwable>(), capture(errorMessageSlot)) } just runs
        val result = Run.builder().result(ExecutionResult.FAILED).build()
        val runner = mockk<DeviceFarmTractorRunner>()
        coEvery { runner.getDeviceResultsTable(any()) } returns ""
        coEvery {
            runner.runTests(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns result
        mockkObject(DeviceFarmTractorFactory, KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns mockLogger
        coEvery {
            DeviceFarmTractorFactory.createRunner(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns runner.right()


        //WHEN-THEN
        assertThatCode { pluginTask.execute() }.doesNotThrowAnyException()
        assertThat(errorMessageSlot.captured.invoke())
            .isEqualTo("Tests result was not success - actual result = ${ExecutionResult.FAILED}")

        coVerify {
            runner.runTests(
                projectName = projectName,
                "",
                appPath = appPath,
                testProjectPath = testsProjectPath,
                testSpecPath = testSpecFilePath
            )
            runner.getDeviceResultsTable(result)
        }
        confirmVerified(runner)
        clearMocks(runner, mockLogger)
        unmockkObject(DeviceFarmTractorFactory, KotlinLogging)

    }

    @Test
    fun `when test execution is success a message should be logged`() {
        //GIVEN
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ricardorlg.DeviceFarmTractorGradlePlugin")
        val pluginTask = project.tasks.getByName("runAwsTests") as DeviceFarmTractorGradleTask
        pluginTask.projectName = projectName
        pluginTask.appPath = appPath
        pluginTask.testsProjectPath = testsProjectPath
        pluginTask.testSpecFilePath = testSpecFilePath

        //MOCKS
        val mockLogger = mockk<KLogger>()
        val slot = slot<String>()
        val result = Run.builder().result(ExecutionResult.PASSED).build()
        val runner = mockk<DeviceFarmTractorRunner>()
        every { mockLogger.info(capture(slot)) } just runs
        coEvery { runner.getDeviceResultsTable(any()) } returns ""
        coEvery {
            runner.runTests(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns result
        mockkObject(DeviceFarmTractorFactory, KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns mockLogger
        coEvery {
            DeviceFarmTractorFactory.createRunner(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns runner.right()

        //WHEN-THEN
        assertThatCode { pluginTask.execute() }.doesNotThrowAnyException()
        assertThat(slot.captured).isEqualTo("Test execution has successfully finished")

        coVerify {
            runner.runTests(
                projectName = projectName,
                "",
                appPath = appPath,
                testProjectPath = testsProjectPath,
                testSpecPath = testSpecFilePath
            )
            runner.getDeviceResultsTable(result)
        }
        verify {
            mockLogger.info(any<String>()) //banner log
            mockLogger.info(any<String>()) // execution finish log
            mockLogger.info(slot.captured) // execution result log
        }
        confirmVerified(runner, mockLogger)
        clearMocks(runner, mockLogger)
        unmockkObject(DeviceFarmTractorFactory, KotlinLogging)

    }

    @Test
    fun `when there is an error creating the tractor runner and strict run is enabled, the plugin should fails`() {
        //GIVEN
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ricardorlg.DeviceFarmTractorGradlePlugin")
        val pluginTask = project.tasks.getByName("runAwsTests") as DeviceFarmTractorGradleTask
        pluginTask.projectName = projectName
        pluginTask.appPath = appPath
        pluginTask.testsProjectPath = testsProjectPath
        pluginTask.testSpecFilePath = testSpecFilePath

        //MOCKS
        val expectedException = RuntimeException("expected exception")
        mockkObject(DeviceFarmTractorFactory)
        coEvery {
            DeviceFarmTractorFactory.createRunner(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns expectedException.left()

        //WHEN-THEN
        assertThatExceptionOfType(GradleException::class.java)
            .isThrownBy(pluginTask::execute)
            .withCause(expectedException)

        unmockkObject(DeviceFarmTractorFactory)
    }

    @Test
    fun `when there is an error creating the tractor runner but strict run is disabled, the plugin should not fails`() {
        //GIVEN
        val project = ProjectBuilder.builder().build()
        project.pluginManager.apply("io.github.ricardorlg.DeviceFarmTractorGradlePlugin")
        val pluginTask = project.tasks.getByName("runAwsTests") as DeviceFarmTractorGradleTask
        pluginTask.projectName = projectName
        pluginTask.appPath = appPath
        pluginTask.testsProjectPath = testsProjectPath
        pluginTask.testSpecFilePath = testSpecFilePath
        pluginTask.strictRun = false

        //MOCKS
        val thrownException = RuntimeException("expected exception")
        val mockLogger = mockk<KLogger>()
        val errorSlot = slot<Throwable>()
        val errorMessageSlot = slot<() -> Any?>()
        every { mockLogger.error(capture(errorSlot), capture(errorMessageSlot)) } just runs
        mockkObject(DeviceFarmTractorFactory, KotlinLogging)
        every { KotlinLogging.logger(any<String>()) } returns mockLogger
        coEvery {
            DeviceFarmTractorFactory.createRunner(
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
            )
        } returns thrownException.left()

        //WHEN-THEN
        assertThatCode { pluginTask.execute() }.doesNotThrowAnyException()
        assertThat(errorSlot.captured).isEqualTo(thrownException)
        assertThat(errorMessageSlot.captured.invoke())
            .isEqualTo("There was an error creating the tractor runner")
        clearMocks(mockLogger)
        unmockkObject(DeviceFarmTractorFactory, KotlinLogging)

    }
}