package org.plugin.inspecta

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.plugin.inspecta.task.InspectTask

class InspectaPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.tasks.register("inspect", InspectTask::class.java) {
            it.group = "Inspecta"
            it.description = "Generates a detailed audit of app size, assets, and libraries."
        }
    }
}