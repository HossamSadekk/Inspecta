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
        println("\n")
        println("┌─────────────────────────────────────────────────────────────────────────┐")
        println("│                                                                         │")
        println("│                      📦  INSPECTA  —  App Size Audit                    │")
        println("│                                                                         │")
        println("└─────────────────────────────────────────────────────────────────────────┘")
        println()

        // Collect from ALL modules
        val allModules = project.rootProject.allprojects

        println("🔍 Scanning ${allModules.size} modules: ${allModules.map { it.name }.joinToString(", ")}")
        println()

        // Find the app module correctly
        val appModule = allModules.find { subproject ->
            subproject.plugins.hasPlugin("com.android.application")
        } ?: project.rootProject.findProject("app") ?: project

        println("📱 App module detected: ${appModule.name}")
        println()

        // --- 1. SCAN RESOURCES ACROSS ALL MODULES ---
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
                    // Check if it's a vector drawable by reading first few lines
                    val content = file.readText()
                    if (content.contains("<vector") || content.contains("android:pathData")) {
                        svgs.add(file)
                    }
                }
                ext == "xml" && file.parent.contains("layout") -> xmlLayouts.add(file)
                ext == "json" && (file.parent.contains("raw") || file.parent.contains("assets")) -> lotties.add(file)
                ext == "ttf" || ext == "otf" -> fonts.add(file)
            }
        }

        // Scan ALL modules for resources
        allModules.forEach { subproject ->
            val srcDir = File(subproject.projectDir, "src/main")
            val resDir = File(srcDir, "res")
            val assetsDir = File(srcDir, "assets")

            if (resDir.exists()) resDir.walkTopDown().forEach { categorize(it) }
            if (assetsDir.exists()) assetsDir.walkTopDown().forEach { categorize(it) }
        }

        // --- 2. ANALYZE NATIVE LIBRARIES ACROSS ALL MODULES ---
        var nativeLibsSize = 0L
        val nativeLibs = mutableListOf<Pair<String, Long>>()

        allModules.forEach { subproject ->
            val srcDir = File(subproject.projectDir, "src/main")
            val jniLibsDir = File(srcDir, "jniLibs")

            if (jniLibsDir.exists()) {
                jniLibsDir.walkTopDown()
                    .filter { it.isFile && it.extension == "so" }
                    .forEach {
                        val size = it.length()
                        nativeLibs.add(it.name to size)
                        nativeLibsSize += size
                    }
            }
        }

        // --- 3. DETECT UNUSED RESOURCES (Enhanced) ---
        val codeBuilder = StringBuilder()

        allModules.forEach { subproject ->
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
        val unusedPngs = pngs.filter { !isResourceReferenced(it, codeBlob) }
        val unusedJpgs = jpgs.filter { !isResourceReferenced(it, codeBlob) }

        // Check for density variants
        val densityAnalysis = analyzeDensityVariants(pngs + jpgs + webps)

        // --- 4. ANALYZE BUILT APK/AAB (from app module) ---
        val appDir = appModule.projectDir
        val buildOutputs = File(appDir, "build/outputs")
        val apkAnalysis = analyzeBuiltArtifacts(buildOutputs)

        // --- 5. DETECT UNUSED DEPENDENCIES FROM VERSION CATALOG ---
        println("\n🔍 Analyzing Version Catalog dependencies...")
        val unusedDependencies = analyzeVersionCatalogUsage(appModule)

        // --- 6. GENERATE COMPREHENSIVE REPORT ---
        println("\n📊 SIZE BREAKDOWN")
        println("-".repeat(60))

        if (apkAnalysis != null) {
            println("✅ Built APK Found: ${apkAnalysis.name}")
            println("   Total Size: ${formatSize(apkAnalysis.totalSize)} (${String.format("%,d", apkAnalysis.totalSize)} bytes)")

            // Calculate sum of analyzed components
            val analyzedSum = apkAnalysis.breakdown.sumOf { it.second }
            val overhead = apkAnalysis.totalSize - analyzedSum

            if (overhead > 0) {
                println("   (${formatSize(overhead)} ZIP overhead/alignment)")
            }

            println("\n   APK Composition:")
            apkAnalysis.breakdown.sortedByDescending { it.second }.forEach { (category, size) ->
                if (size > 0) {
                    val percent = (size * 100.0 / apkAnalysis.totalSize)
                    println("   - ${category.padEnd(30)}: ${formatSize(size).padStart(10)} (${String.format("%.1f", percent)}%)")
                }
            }
        } else {
            println("⚠️  No built APK found. Build your app first for accurate size.")
            println("   Run: ./gradlew ${appModule.name}:assembleDebug")
        }

        println("\n🖼️  IMAGES & ASSETS")
        println("-".repeat(60))
        println("PNG: ${pngs.size} files (${formatSize(pngs.sumOf { it.length() })})")
        if (unusedPngs.isNotEmpty()) {
            println("   ⚠️  ${unusedPngs.size} potentially unused (${formatSize(unusedPngs.sumOf { it.length() })})")
        }
        println("JPG: ${jpgs.size} files (${formatSize(jpgs.sumOf { it.length() })})")
        if (unusedJpgs.isNotEmpty()) {
            println("   ⚠️  ${unusedJpgs.size} potentially unused (${formatSize(unusedJpgs.sumOf { it.length() })})")
        }
        println("WebP: ${webps.size} files (${formatSize(webps.sumOf { it.length() })})")
        println("Vector Drawables: ${svgs.size} files (${formatSize(svgs.sumOf { it.length() })})")
        println("Lottie Animations: ${lotties.size} files (${formatSize(lotties.sumOf { it.length() })})")
        println("Fonts: ${fonts.size} files (${formatSize(fonts.sumOf { it.length() })})")

        if (densityAnalysis.isNotEmpty()) {
            println("\nDensity Variants:")
            densityAnalysis.forEach { (density, count) ->
                println("   - $density: $count images")
            }
        }

        if (nativeLibs.isNotEmpty()) {
            println("\n🔧 NATIVE LIBRARIES (.so files)")
            println("-".repeat(60))
            println("Total: ${nativeLibs.size} files (${formatSize(nativeLibsSize)})")
            nativeLibs.sortedByDescending { it.second }.take(10).forEach { (name, size) ->
                println("   - ${name}: ${formatSize(size)}")
            }
            if (nativeLibs.size > 10) {
                println("   ... and ${nativeLibs.size - 10} more")
            }
        }

        // Show native libraries from the built APK (the real culprit!)
        if (apkAnalysis?.nativeLibsInApk?.isNotEmpty() == true) {
            println("\n🔧 NATIVE LIBRARIES IN BUILT APK (.so files)")
            println("-".repeat(60))
            val apkNativeLibs = apkAnalysis.nativeLibsInApk
            val totalNativeSize = apkNativeLibs.sumOf { it.second }
            println("Total: ${apkNativeLibs.size} files (${formatSize(totalNativeSize)})")

            // Group by library name first to see duplicates across architectures
            val byLibName = apkNativeLibs.groupBy { it.first }

            println("\n📊 Breakdown by library:")
            val topLibs = byLibName.entries
                .sortedByDescending { it.value.sumOf { pair -> pair.second } }

            val displayCount = 15
            topLibs.take(displayCount).forEach { (libName, instances) ->
                val libTotal = instances.sumOf { it.second }
                val percent = (libTotal * 100.0 / totalNativeSize)
                println("   - ${libName.padEnd(45)}: ${formatSize(libTotal).padStart(10)} (${String.format("%.1f", percent)}%)")
                if (instances.size > 1) {
                    println("      ${instances.size} architecture variants")
                }
            }

            if (byLibName.size > displayCount) {
                val remainingLibs = byLibName.size - displayCount
                val remainingSize = topLibs.drop(displayCount).sumOf { it.value.sumOf { pair -> pair.second } }
                val remainingPercent = (remainingSize * 100.0 / totalNativeSize)
                println("   - ... and ${remainingLibs} more libraries          : ${formatSize(remainingSize).padStart(10)} (${String.format("%.1f", remainingPercent)}%)")
            }

            // Also show breakdown by architecture
            println("\n📊 Breakdown by CPU architecture:")
            val architectures = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
            architectures.forEach { arch ->
                val archLibs = apkNativeLibs.filter { it.first.contains("/$arch/") }
                if (archLibs.isNotEmpty()) {
                    val archSize = archLibs.sumOf { it.second }
                    val percent = (archSize * 100.0 / totalNativeSize)
                    println("   - ${arch.padEnd(15)}: ${formatSize(archSize).padStart(10)} (${String.format("%.1f", percent)}%) - ${archLibs.size} files")
                }
            }

            println("\n   💡 These libraries come from your Gradle dependencies:")
            println("      - ML Kit (Google OCR & Face Detection) is the largest contributor")
            println("      - Use App Bundles (.aab) to reduce size by ~75% (only include needed architecture)")
        }

        // --- 7. SHOW UNUSED DEPENDENCIES ---
        if (unusedDependencies.isNotEmpty()) {
            println("\n📚 UNUSED DEPENDENCIES IN VERSION CATALOG")
            println("-".repeat(60))
            println("Found ${unusedDependencies.size} libraries defined but not used:")
            println()

            unusedDependencies.sortedBy { it.catalogName }.forEach { dep ->
                println("   ❌ ${dep.catalogName}")
                println("      Library: ${dep.libraryName}")
                if (dep.definedIn.isNotEmpty()) {
                    println("      Defined in: ${dep.definedIn}")
                }
                println()
            }

            println("💡 These dependencies are in your version catalog but not used in any module.")
            println("   You can safely remove them from libs.versions.toml")
        } else {
            println("\n📚 DEPENDENCY USAGE")
            println("-".repeat(60))
            println("✨ All version catalog dependencies are being used!")
        }

        println("\n💡 OPTIMIZATION SUGGESTIONS")
        println("-".repeat(60))

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
            suggestions.add("📸 Convert JPEGs to WebP → Better compression with same quality")
        }

        if (nativeLibsSize > 5 * 1024 * 1024) {
            suggestions.add("🔧 Native libs are large (${formatSize(nativeLibsSize)}) → Use App Bundle for ABI splits")
        }

        if (lotties.any { it.length() > 100 * 1024 }) {
            suggestions.add("🎬 Large Lottie files detected → Optimize in LottieFiles or reduce complexity")
        }

        if (apkAnalysis == null) {
            suggestions.add("🏗️  Build your app for accurate size analysis:")
            suggestions.add("   ./gradlew ${appModule.name}:assembleRelease")
        }

        if (unusedDependencies.isNotEmpty()) {
            suggestions.add("🧹 Remove ${unusedDependencies.size} unused dependencies from libs.versions.toml")
        }

        if (suggestions.isEmpty()) {
            println("✨ Looking good! No major issues detected.")
        } else {
            suggestions.forEach { println(it) }
        }

        println("\n" + "=".repeat(60))
        println("💡 Tip: Run './gradlew ${appModule.name}:assembleRelease' for accurate APK size")
        println("=".repeat(60) + "\n")
    }

    private data class UnusedDependency(
        val catalogName: String,
        val libraryName: String,
        val definedIn: String
    )

    private fun analyzeVersionCatalogUsage(appModule: org.gradle.api.Project): List<UnusedDependency> {
        val unusedDeps = mutableListOf<UnusedDependency>()

        try {
            // Find libs.versions.toml file
            val possibleLocations = listOf(
                File(project.rootProject.projectDir, "gradle/libs.versions.toml"),
                File(project.rootProject.projectDir, "libs.versions.toml")
            )

            val catalogFile = possibleLocations.firstOrNull { it.exists() }

            if (catalogFile == null) {
                println("   ⚠️  libs.versions.toml not found")
                return emptyList()
            }

            println("   ✓ Found version catalog: ${catalogFile.name}")

            // Parse the TOML file to extract library definitions
            val catalogContent = catalogFile.readText()
            val librariesInCatalog = parseLibrariesFromToml(catalogContent)

            println("   ✓ Found ${librariesInCatalog.size} libraries defined in catalog")

            // Collect all build.gradle.kts content from all modules
            val allBuildScripts = StringBuilder()
            project.rootProject.allprojects.forEach { subproject ->
                val buildFile = File(subproject.projectDir, "build.gradle.kts")
                if (buildFile.exists()) {
                    allBuildScripts.append(buildFile.readText()).append("\n")
                }
                val buildFileGroovy = File(subproject.projectDir, "build.gradle")
                if (buildFileGroovy.exists()) {
                    allBuildScripts.append(buildFileGroovy.readText()).append("\n")
                }
            }

            val buildScriptsContent = allBuildScripts.toString()

            // Check each library for usage
            librariesInCatalog.forEach { (catalogName, libraryCoordinate) ->
                val isUsed = isLibraryUsedInBuildScripts(catalogName, buildScriptsContent)

                if (!isUsed) {
                    unusedDeps.add(
                        UnusedDependency(
                            catalogName = catalogName,
                            libraryName = libraryCoordinate,
                            definedIn = catalogFile.name
                        )
                    )
                }
            }

            println("   ✓ Analysis complete: ${unusedDeps.size} unused dependencies found")

        } catch (e: Exception) {
            println("   ⚠️  Error analyzing version catalog: ${e.message}")
        }

        return unusedDeps
    }

    private fun parseLibrariesFromToml(content: String): Map<String, String> {
        val libraries = mutableMapOf<String, String>()

        try {
            var inLibrariesSection = false

            content.lines().forEach { line ->
                val trimmed = line.trim()

                // Detect [libraries] section
                if (trimmed == "[libraries]") {
                    inLibrariesSection = true
                    return@forEach
                }

                // Exit libraries section when we hit another section
                if (trimmed.startsWith("[") && trimmed.endsWith("]") && trimmed != "[libraries]") {
                    inLibrariesSection = false
                    return@forEach
                }

                // Parse library definitions
                if (inLibrariesSection && trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                    // Format: library-name = { group = "...", name = "...", version.ref = "..." }
                    // Or: library-name = "group:name:version"

                    val parts = trimmed.split("=", limit = 2)
                    if (parts.size == 2) {
                        val libName = parts[0].trim()
                        val libDef = parts[1].trim()

                        // Extract library coordinate (simplified parsing)
                        val coordinate = when {
                            libDef.contains("group") && libDef.contains("name") -> {
                                val group = extractValue(libDef, "group")
                                val name = extractValue(libDef, "name")
                                "$group:$name"
                            }
                            libDef.startsWith("\"") || libDef.startsWith("'") -> {
                                libDef.trim('"', '\'')
                            }
                            else -> libDef
                        }

                        libraries[libName] = coordinate
                    }
                }
            }
        } catch (e: Exception) {
            println("   ⚠️  Error parsing TOML: ${e.message}")
        }

        return libraries
    }

    private fun extractValue(definition: String, key: String): String {
        val regex = """$key\s*=\s*["']([^"']+)["']""".toRegex()
        return regex.find(definition)?.groupValues?.get(1) ?: ""
    }

    private fun isLibraryUsedInBuildScripts(catalogName: String, buildScriptsContent: String): Boolean {
        // Check for various usage patterns:
        // 1. implementation(libs.library.name)
        // 2. implementation(truLibs.library.name) - for your custom catalog
        // 3. debugImplementation(libs.library.name)
        // 4. testImplementation(libs.library.name)
        // 5. ksp(libs.library.name)

        val normalizedName = catalogName.replace("-", ".").replace("_", ".")

        val patterns = listOf(
            "libs\\.$normalizedName",
            "truLibs\\.$normalizedName",
            "libs\\.plugins\\.$normalizedName",
            "truLibs\\.plugins\\.$normalizedName",
            // Also check with dashes and underscores
            "libs\\.${catalogName.replace("-", "\\.")}",
            "truLibs\\.${catalogName.replace("-", "\\.")}",
        )

        return patterns.any { pattern ->
            buildScriptsContent.contains(Regex(pattern))
        }
    }

    private fun isResourceReferenced(file: File, codeBlob: String): Boolean {
        val name = file.nameWithoutExtension
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
        val breakdown: List<Pair<String, Long>>,
        val nativeLibsInApk: List<Pair<String, Long>> = emptyList() // Add this
    )

    private fun analyzeBuiltArtifacts(buildOutputs: File): ApkAnalysis? {
        if (!buildOutputs.exists()) return null

        val artifactFile = buildOutputs.walkTopDown()
            .filter {
                (it.extension == "apk" || it.extension == "aab") &&
                        !it.name.contains("unaligned", ignoreCase = true) &&
                        !it.name.contains("androidTest", ignoreCase = true) &&
                        !it.name.contains("test-", ignoreCase = true)
            }
            .sortedByDescending {
                val score = when {
                    it.name.contains("release", ignoreCase = true) -> 1000
                    it.name.contains("debug", ignoreCase = true) -> 500
                    else -> 0
                }
                score + it.length()
            }
            .firstOrNull()
            ?: return null

        val breakdown = mutableListOf<Pair<String, Long>>()
        var dexSize = 0L
        var resSize = 0L
        var libSize = 0L
        var assetsSize = 0L
        var metaInfSize = 0L
        var manifestSize = 0L
        var otherSize = 0L

        try {
            val actualFileSize = artifactFile.length()
            val nativeLibsInApk = mutableListOf<Pair<String, Long>>()

            ZipFile(artifactFile).use { zip ->
                zip.entries().asIterator().forEach { entry ->
                    if (entry.isDirectory) return@forEach

                    // Use the actual size in the ZIP (compressed size)
                    val size = entry.compressedSize.let {
                        if (it >= 0) it else entry.size
                    }

                    when {
                        entry.name.endsWith(".dex") -> dexSize += size
                        entry.name.startsWith("res/") -> resSize += size
                        entry.name.startsWith("lib/") -> {
                            libSize += size
                            // Collect individual .so files from APK with full path
                            if (entry.name.endsWith(".so")) {
                                nativeLibsInApk.add(entry.name to size)
                            }
                        }
                        entry.name.startsWith("assets/") -> assetsSize += size
                        entry.name.startsWith("META-INF/") -> metaInfSize += size
                        entry.name == "AndroidManifest.xml" -> manifestSize += size
                        else -> otherSize += size
                    }
                }
            }

            // Calculate total ZIP overhead (headers, central directory, etc.)
            val contentTotal = dexSize + resSize + libSize + assetsSize + metaInfSize + manifestSize + otherSize
            val zipOverhead = actualFileSize - contentTotal

            // Add overhead proportionally or as separate item
            otherSize += zipOverhead

            otherSize += metaInfSize + manifestSize

            breakdown.add("Code (DEX)" to dexSize)
            breakdown.add("Native Libs" to libSize)
            breakdown.add("Resources" to resSize)
            breakdown.add("Assets" to assetsSize)
            breakdown.add("Other (Manifest, Signatures)" to otherSize)

            return ApkAnalysis(artifactFile.name, artifactFile.length(), breakdown, nativeLibsInApk)
        } catch (e: Exception) {
            println("⚠️  Could not analyze artifact: ${e.message}")
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