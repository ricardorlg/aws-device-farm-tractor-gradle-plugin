package com.ricardorlg.devicefarm.tractor.gradle

import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DeviceFarmTractorGradlePluginTest {

    @Test
    fun `test plugin is added to project`() {
        //GIVEN
        val project = ProjectBuilder.builder().build()

        //WHEN
        project.pluginManager.apply("com.ricardorlg.devicefarm.tractor-gradle-plugin")

        //THEN
        assertTrue(project.tasks.any { it.name == "runAwsTests" }, "the project should have the runAwsTests task")
        assertTrue(
            project.tasks.getByName("runAwsTests") is DeviceFarmTractorGradleTask,
            "the project should have the runAwsTests task"
        )
        assertEquals(project.tasks.getByName("runAwsTests").group, "Device farm tractor")
    }

}