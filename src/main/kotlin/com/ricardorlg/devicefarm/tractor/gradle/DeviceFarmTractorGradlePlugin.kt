package com.ricardorlg.devicefarm.tractor.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

class DeviceFarmTractorGradlePlugin : Plugin<Project> {
    override fun apply(project: Project) {
        with(project) {
            tasks.create("runAwsTests", DeviceFarmTractorGradleTask::class.java)
        }
    }
}