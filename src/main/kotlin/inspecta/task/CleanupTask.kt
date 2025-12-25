package org.plugin.inspecta.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

abstract class CleanupTask : DefaultTask() {

    @TaskAction
    fun cleanup() {
        println("\n")
        println("╔═══════════════════════════════════════════════════════════════════════════╗")
        println("║                                                                           ║")
        println("║                    🧹  INSPECTA  —  Resource Cleanup                      ║")
        println("║                                                                           ║")
        println("╚═══════════════════════════════════════════════════════════════════════════╝")
        println()

        // Get parameters from project properties
        val resourceType = project.findProperty("type")?.toString()?.lowercase() ?: ""
        val confirm = project.findProperty("confirm")?.toString()?.lowercase() ?: ""

        // Validate resource type
        val validTypes = listOf("png", "jpg", "jpeg", "webp", "svg", "all")

        if (resourceType.isEmpty() || resourceType !in validTypes) {
            println("❌ Error: Please specify a valid resource type using -Ptype=<type>")
            println()
            println("Valid types:")
            println("  -Ptype=png      Remove unused PNG files")
            println("  -Ptype=jpg      Remove unused JPG/JPEG files")
            println("  -Ptype=webp     Remove unused WebP files")
            println("  -Ptype=svg      Remove unused SVG (vector drawable) files")
            println("  -Ptype=all      Remove all unused image resources")
            println()
            println("Example usage:")
            println("  ./gradlew cleanupResources -Ptype=png")
            println("  ./gradlew cleanupResources -Ptype=all -Pconfirm=yes")
            println()
            return
        }

        // Collect resources from Android modules only
        val allModules = project.rootProject.allprojects
        val androidModules = allModules.filter { module ->
            module.plugins.hasPlugin("com.android.application") ||
            module.plugins.hasPlugin("com.android.library")
        }

        if (androidModules.isEmpty()) {
            println("❌ Error: No Android modules found!")
            println("   Make sure your project has Android app or library modules.")
            println()
            return
        }

        println("🔍 Scanning ${androidModules.size} Android modules for unused resources...")
        println()

        val unusedFiles = mutableListOf<File>()

        // Scan code from Android modules only
        val codeBuilder = StringBuilder()
        androidModules.forEach { subproject ->
            val srcDir = File(subproject.projectDir, "src/main")
            val javaDir = File(srcDir, "java")
            val kotlinDir = File(srcDir, "kotlin")
            val resDir = File(srcDir, "res")

            sequenceOf(javaDir, kotlinDir, resDir)
                .filter { it.exists() }
                .flatMap { it.walkTopDown() }
                .filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.extension == "xml") }
                .forEach { codeBuilder.append(it.readText()).append("\n") }
        }

        val codeBlob = codeBuilder.toString()

        // Scan resources from Android modules only
        androidModules.forEach { subproject ->
            val srcDir = File(subproject.projectDir, "src/main")
            val resDir = File(srcDir, "res")

            if (resDir.exists()) {
                resDir.walkTopDown()
                    .filter { it.isFile }
                    .forEach { file ->
                        val ext = file.extension.lowercase()
                        val shouldCheck = when (resourceType) {
                            "png" -> ext == "png"
                            "jpg", "jpeg" -> ext == "jpg" || ext == "jpeg"
                            "webp" -> ext == "webp"
                            "svg" -> {
                                if (ext == "xml" && file.parent.contains("drawable")) {
                                    val content = file.readText()
                                    content.contains("<vector") || content.contains("android:pathData")
                                } else {
                                    false
                                }
                            }
                            "all" -> {
                                when {
                                    ext in listOf("png", "jpg", "jpeg", "webp") -> true
                                    ext == "xml" && file.parent.contains("drawable") -> {
                                        val content = file.readText()
                                        content.contains("<vector") || content.contains("android:pathData")
                                    }
                                    else -> false
                                }
                            }
                            else -> false
                        }

                        if (shouldCheck && !isResourceReferenced(file, codeBlob)) {
                            unusedFiles.add(file)
                        }
                    }
            }
        }

        if (unusedFiles.isEmpty()) {
            println("✨ No unused ${if (resourceType == "all") "resources" else resourceType.uppercase()} files found!")
            println()
            return
        }

        // Calculate total size
        val totalSize = unusedFiles.sumOf { it.length() }

        println("📊 Found ${unusedFiles.size} unused ${if (resourceType == "all") "resource" else resourceType.uppercase()} files")
        println("💾 Total size: ${formatSize(totalSize)}")

        // Show breakdown by type when using "all"
        if (resourceType == "all") {
            val pngFiles = unusedFiles.filter { it.extension.lowercase() == "png" }
            val jpgFiles = unusedFiles.filter { it.extension.lowercase() in listOf("jpg", "jpeg") }
            val webpFiles = unusedFiles.filter { it.extension.lowercase() == "webp" }
            val svgFiles = unusedFiles.filter { it.extension.lowercase() == "xml" }

            println()
            println("   By type:")
            if (pngFiles.isNotEmpty()) {
                println("   • PNG: ${pngFiles.size} files (${formatSize(pngFiles.sumOf { it.length() })})")
            }
            if (jpgFiles.isNotEmpty()) {
                println("   • JPG: ${jpgFiles.size} files (${formatSize(jpgFiles.sumOf { it.length() })})")
            }
            if (webpFiles.isNotEmpty()) {
                println("   • WebP: ${webpFiles.size} files (${formatSize(webpFiles.sumOf { it.length() })})")
            }
            if (svgFiles.isNotEmpty()) {
                println("   • SVG (Vector): ${svgFiles.size} files (${formatSize(svgFiles.sumOf { it.length() })})")
            }
        }
        println()

        // Group by module
        val byModule = unusedFiles.groupBy { file ->
            androidModules
                .filter { module ->
                    file.absolutePath.startsWith(module.projectDir.absolutePath)
                }
                .maxByOrNull { it.projectDir.absolutePath.length }  // Longest path = most specific module
                ?.name ?: "unknown"
        }

        println("📂 Breakdown by module:")
        byModule.forEach { (moduleName, files) ->
            val moduleSize = files.sumOf { it.length() }
            println("   ${moduleName}: ${files.size} files (${formatSize(moduleSize)})")
        }
        println()

        // Show sample of files
        println("📄 Files to be removed (showing first 20):")
        unusedFiles.take(20).forEach { file ->
            val relativePath = file.relativeTo(project.rootProject.projectDir).path
            println("   • $relativePath (${formatSize(file.length())})")
        }

        if (unusedFiles.size > 20) {
            println("   ... and ${unusedFiles.size - 20} more files")
        }
        println()

        // Check if this is a dry run or actual deletion
        val shouldDelete = confirm == "yes"

        if (shouldDelete) {
            println("⚠️  DELETING FILES...")
            println()

            var successCount = 0
            var failCount = 0

            unusedFiles.forEach { file ->
                try {
                    if (file.delete()) {
                        successCount++
                    } else {
                        failCount++
                        println("   ❌ Failed to delete: ${file.name}")
                    }
                } catch (e: Exception) {
                    failCount++
                    println("   ❌ Error deleting ${file.name}: ${e.message}")
                }
            }

            println()
            if (successCount > 0) {
                println("✅ Successfully deleted $successCount files (${formatSize(totalSize)})")
            }
            if (failCount > 0) {
                println("❌ Failed to delete $failCount files")
            }
            println()
            println("🎉 Cleanup complete!")

        } else {
            println("🔍 DRY RUN MODE - No files were deleted")
            println()
            println("To actually delete these files, run:")
            println("   ./gradlew cleanupResources -Ptype=$resourceType -Pconfirm=yes")
            println()
            println("⚠️  Warning: This action cannot be undone!")
            println("   Make sure you have a backup or are using version control.")
        }

        println()
        println("═══════════════════════════════════════════════════════════════════════════")
        println()
    }

    private fun isResourceReferenced(file: File, codeBlob: String): Boolean {
        val name = file.nameWithoutExtension
        return codeBlob.contains("R.drawable.$name") ||
                codeBlob.contains("@drawable/$name") ||
                codeBlob.contains("\"$name\"") ||
                codeBlob.contains("'$name'")
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (kotlin.math.log10(size.toDouble()) / kotlin.math.log10(1024.0)).toInt()
        return java.text.DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}