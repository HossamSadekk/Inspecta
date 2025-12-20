package org.plugin.inspecta.task

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.text.DecimalFormat
import java.util.zip.ZipFile
import kotlin.math.log10

abstract class InspectTask : DefaultTask() {

    @TaskAction
    fun analyze() {
        val appDir = project.projectDir
        val srcDir = File(appDir, "src/main")
        val resDir = File(srcDir, "res")
        val assetsDir = File(srcDir, "assets")
        val javaDir = File(srcDir, "java")
        val kotlinDir = File(srcDir, "kotlin")
        val jniLibsDir = File(srcDir, "jniLibs")

        println("\n📦 Inspecta - App Size Audit")
        println("=" .repeat(60))

        // --- 1. ANALYZE BUILT APK/AAB (Most Accurate) ---
        val buildOutputs = File(appDir, "build/outputs")
        val apkAnalysis = analyzeBuiltArtifacts(buildOutputs)

        // --- 2. SCAN RESOURCES ---
        val pngs = mutableListOf<File>()
        val jpgs = mutableListOf<File>()
        val webps = mutableListOf<File>()
        val svgs = mutableListOf<File>()
        val lotties = mutableListOf<File>()
        val fonts = mutableListOf<File>()
        val xmlLayouts = mutableListOf<File>()
        var totalResSize = 0L

        fun categorize(file: File) {
            if (file.isDirectory) return
            val size = file.length()
            totalResSize += size
            val ext = file.extension.lowercase()

            when {
                ext == "png" -> pngs.add(file)
                ext == "jpg" || ext == "jpeg" -> jpgs.add(file)
                ext == "webp" -> webps.add(file)
                ext == "xml" && file.parent.contains("drawable") -> {
                    if (file.readText().contains("<vector")) svgs.add(file)
                }
                ext == "xml" && file.parent.contains("layout") -> xmlLayouts.add(file)
                ext == "json" && (file.parent.contains("raw") || file.parent.contains("assets")) -> lotties.add(file)
                ext == "ttf" || ext == "otf" -> fonts.add(file)
            }
        }

        if (resDir.exists()) resDir.walkTopDown().forEach { categorize(it) }
        if (assetsDir.exists()) assetsDir.walkTopDown().forEach { categorize(it) }

        // --- 3. ANALYZE NATIVE LIBRARIES ---
        var nativeLibsSize = 0L
        val nativeLibs = mutableListOf<Pair<String, Long>>()
        if (jniLibsDir.exists()) {
            jniLibsDir.walkTopDown()
                .filter { it.isFile && it.extension == "so" }
                .forEach {
                    val size = it.length()
                    nativeLibs.add(it.name to size)
                    nativeLibsSize += size
                }
        }

        // --- 4. DETECT UNUSED RESOURCES (Enhanced) ---
        val codeBuilder = StringBuilder()
        sequenceOf(javaDir, kotlinDir, resDir)
            .filter { it.exists() }
            .flatMap { it.walkTopDown() }
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java" || it.extension == "xml") }
            .forEach { codeBuilder.append(it.readText()).append("\n") }

        val codeBlob = codeBuilder.toString()
        val unusedPngs = pngs.filter { !isResourceReferenced(it, codeBlob) }
        val unusedJpgs = jpgs.filter { !isResourceReferenced(it, codeBlob) }

        // Check for density variants
        val densityAnalysis = analyzeDensityVariants(pngs + jpgs + webps)

        // --- 5. SCAN LIBRARIES ---
        val libraries = mutableListOf<Triple<String, Long, String>>()
        var totalLibSize = 0L

        try {
            val config = project.configurations.findByName("releaseRuntimeClasspath")
                ?: project.configurations.findByName("debugRuntimeClasspath")

            config?.resolvedConfiguration?.resolvedArtifacts?.forEach { artifact ->
                val id = artifact.moduleVersion.id
                val size = artifact.file.length()
                val version = id.version
                libraries.add(Triple("${id.group}:${id.name}", size, version))
                totalLibSize += size
            }
        } catch (e: Exception) {
            println("⚠️  Could not analyze libraries: ${e.message}")
            println("   Try running after a successful build or sync")
        }

        // --- 6. GENERATE COMPREHENSIVE REPORT ---
        println("\n📊 SIZE BREAKDOWN")
        println("-" .repeat(60))

        if (apkAnalysis != null) {
            println("✅ Built APK Found: ${apkAnalysis.name}")
            println("   Total Size: ${formatSize(apkAnalysis.totalSize)}")
            println("\n   APK Composition:")
            apkAnalysis.breakdown.sortedByDescending { it.second }.forEach { (category, size) ->
                val percent = (size * 100.0 / apkAnalysis.totalSize).toInt()
                println("   - ${category.padEnd(20)}: ${formatSize(size).padStart(10)} ($percent%)")
            }
        } else {
            println("⚠️  No built APK found. Build your app first for accurate size.")
            println("   Estimated from source files:")
            val estimatedTotal = totalResSize + nativeLibsSize + totalLibSize
            println("   Resources: ${formatSize(totalResSize)}")
            println("   Native Libs: ${formatSize(nativeLibsSize)}")
            println("   Dependencies: ${formatSize(totalLibSize)} (JAR sizes, not final APK size)")
            println("   Estimated Total: ${formatSize(estimatedTotal)}")
        }

        println("\n🖼️  IMAGES & ASSETS")
        println("-" .repeat(60))
        println("PNG: ${pngs.size} files (${formatSize(pngs.sumOf { it.length() })})")
        if (unusedPngs.isNotEmpty()) {
            println("  ⚠️  ${unusedPngs.size} potentially unused (${formatSize(unusedPngs.sumOf { it.length() })})")
        }
        println("JPG: ${jpgs.size} files (${formatSize(jpgs.sumOf { it.length() })})")
        if (unusedJpgs.isNotEmpty()) {
            println("  ⚠️  ${unusedJpgs.size} potentially unused (${formatSize(unusedJpgs.sumOf { it.length() })})")
        }
        println("WebP: ${webps.size} files (${formatSize(webps.sumOf { it.length() })})")
        println("Vector Drawables: ${svgs.size} files")
        println("Lottie Animations: ${lotties.size} files (${formatSize(lotties.sumOf { it.length() })})")
        println("Fonts: ${fonts.size} files (${formatSize(fonts.sumOf { it.length() })})")

        if (densityAnalysis.isNotEmpty()) {
            println("\nDensity Variants:")
            densityAnalysis.forEach { (density, count) ->
                println("  - $density: $count images")
            }
        }

        if (nativeLibs.isNotEmpty()) {
            println("\n🔧 NATIVE LIBRARIES (.so files)")
            println("-" .repeat(60))
            println("Total: ${nativeLibs.size} files (${formatSize(nativeLibsSize)})")
            nativeLibs.sortedByDescending { it.second }.take(5).forEach { (name, size) ->
                println("  - ${name}: ${formatSize(size)}")
            }
        }

        println("\n📚 DEPENDENCIES")
        println("-" .repeat(60))
        println("Total: ${libraries.size} libraries")
        if (libraries.isNotEmpty()) {
            libraries.sortedByDescending { it.second }.take(10).forEach { (name, size, version) ->
                println("  - ${name}:${version}")
                println("    ${formatSize(size)}")
            }
            if (libraries.size > 10) println("  ... and ${libraries.size - 10} more")
        }

        println("\n💡 OPTIMIZATION SUGGESTIONS")
        println("-" .repeat(60))

        val suggestions = mutableListOf<String>()

        if (unusedPngs.size + unusedJpgs.size > 5) {
            val wastedSize = unusedPngs.sumOf { it.length() } + unusedJpgs.sumOf { it.length() }
            suggestions.add("🗑️  Remove ${unusedPngs.size + unusedJpgs.size} unused images → Save ${formatSize(wastedSize)}")
        }

        if (pngs.size > 20 && webps.size < pngs.size / 2) {
            val potentialSavings = pngs.sumOf { it.length() } * 0.3
            suggestions.add("🖼️  Convert PNGs to WebP → Save ~${formatSize(potentialSavings.toLong())}")
        }

        if (jpgs.size > 10) {
            suggestions.add("📸 Consider WebP for JPEGs → Better compression with same quality")
        }

        if (nativeLibsSize > 5 * 1024 * 1024) {
            suggestions.add("🔧 Native libs are large (${formatSize(nativeLibsSize)})")
            suggestions.add("   → Use app bundles to split by ABI")
        }

        libraries.find { it.first.contains("guava") && it.second > 2 * 1024 * 1024 }?.let {
            suggestions.add("⚠️  Guava library detected (${formatSize(it.second)}) → Consider alternatives")
        }

        libraries.find { it.first.contains("gson") }?.let { gson ->
            libraries.find { it.first.contains("moshi") || it.first.contains("kotlinx.serialization") }?.let {
                suggestions.add("🔄 Multiple JSON libraries detected → Pick one (Gson, Moshi, or kotlinx.serialization)")
            }
        }

        if (lotties.isNotEmpty() && lotties.any { it.length() > 100 * 1024 }) {
            suggestions.add("🎬 Large Lottie files detected → Optimize in LottieFiles or reduce complexity")
        }

        if (apkAnalysis == null) {
            suggestions.add("🏗️  Build your app to get accurate size analysis:")
            suggestions.add("   ./gradlew assembleRelease")
        }

        if (suggestions.isEmpty()) {
            println("✨ Looking good! No major issues detected.")
        } else {
            suggestions.forEach { println(it) }
        }

        println("\n" + "=".repeat(60))
        println("💡 Tip: Run './gradlew assembleRelease' first for most accurate results")
        println("=" .repeat(60) + "\n")
    }

    private fun isResourceReferenced(file: File, codeBlob: String): Boolean {
        val name = file.nameWithoutExtension
        // Check multiple reference patterns
        return codeBlob.contains("R.drawable.$name") ||
                codeBlob.contains("@drawable/$name") ||
                codeBlob.contains("\"$name\"") ||
                codeBlob.contains("'$name'")
    }

    private fun analyzeDensityVariants(images: List<File>): Map<String, Int> {
        val densities = listOf("ldpi", "mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi", "nodpi", "anydpi")
        return densities.associateWith { density ->
            images.count { it.parent.contains("-$density") }
        }.filterValues { it > 0 }
    }

    private data class ApkAnalysis(
        val name: String,
        val totalSize: Long,
        val breakdown: List<Pair<String, Long>>
    )

    private fun analyzeBuiltArtifacts(buildOutputs: File): ApkAnalysis? {
        if (!buildOutputs.exists()) return null

        // Look for APK or AAB
        val apkFile = buildOutputs.walkTopDown()
            .firstOrNull { it.extension == "apk" && !it.name.contains("unaligned") }
            ?: return null

        val breakdown = mutableListOf<Pair<String, Long>>()
        var dexSize = 0L
        var resSize = 0L
        var libSize = 0L
        var assetsSize = 0L
        var otherSize = 0L

        try {
            ZipFile(apkFile).use { zip ->
                zip.entries().asIterator().forEach { entry ->
                    if (entry.isDirectory) return@forEach
                    val size = entry.size

                    when {
                        entry.name.endsWith(".dex") -> dexSize += size
                        entry.name.startsWith("res/") -> resSize += size
                        entry.name.startsWith("lib/") -> libSize += size
                        entry.name.startsWith("assets/") -> assetsSize += size
                        else -> otherSize += size
                    }
                }
            }

            breakdown.add("Code (DEX)" to dexSize)
            breakdown.add("Resources" to resSize)
            breakdown.add("Native Libs" to libSize)
            breakdown.add("Assets" to assetsSize)
            breakdown.add("Other" to otherSize)

            return ApkAnalysis(apkFile.name, apkFile.length(), breakdown)
        } catch (e: Exception) {
            println("⚠️  Could not analyze APK: ${e.message}")
            return null
        }
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (log10(size.toDouble()) / log10(1024.0)).toInt()
        return DecimalFormat("#,##0.#").format(size / Math.pow(1024.0, digitGroups.toDouble())) + " " + units[digitGroups]
    }
}