plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun readDotEnv(root: java.io.File): Map<String, String> {
    val envFile = root.resolve(".env")
    if (!envFile.exists()) return emptyMap()
    return envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.startsWith("#") && it.contains('=') }
        .associate { line ->
            val separator = line.indexOf('=')
            val key = line.substring(0, separator).trim()
            val value = line.substring(separator + 1).trim().removeSurrounding("\"")
            key to value
        }
}

fun escapeBuildConfig(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")

fun gradlePropertyOrEnv(name: String): String =
    providers.gradleProperty(name).orNull ?: System.getenv(name).orEmpty()

fun signingPropertyEnvOrDotEnv(name: String, dotEnv: Map<String, String>): String =
    gradlePropertyOrEnv(name).ifBlank { dotEnv[name].orEmpty() }

fun playUploadPropertyEnvOrDotEnv(suffix: String, dotEnv: Map<String, String>): String =
    signingPropertyEnvOrDotEnv("TOASTLIFT_PLAY_UPLOAD_$suffix", dotEnv)
        .ifBlank { signingPropertyEnvOrDotEnv("TOASTLIFT_RELEASE_$suffix", dotEnv) }

data class SemanticVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
) {
    val versionCode: Int
        get() = Math.addExact(
            Math.addExact(Math.multiplyExact(major, 1_000_000), Math.multiplyExact(minor, 1_000)),
            patch,
        )
}

fun readSemanticVersion(root: java.io.File): SemanticVersion {
    val versionFile = root.resolve("version.txt")
    check(versionFile.isFile) { "Missing required version source: ${versionFile.path}" }
    val source = versionFile.readText().trim()
    val match = Regex("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$").matchEntire(source)
        ?: error("version.txt must contain a SemVer core version (for example 1.2.3), found: $source")
    return try {
        SemanticVersion(
            major = match.groupValues[1].toInt(),
            minor = match.groupValues[2].toInt(),
            patch = match.groupValues[3].toInt(),
        )
    } catch (error: NumberFormatException) {
        throw GradleException("version.txt contains a SemVer component outside Android versionCode range: $source", error)
    }
}

val dotEnv = readDotEnv(rootProject.projectDir)
val customExerciseAiProvider = dotEnv["CUSTOM_EXERCISE_AI_PROVIDER"].orEmpty().ifBlank { "gemini" }
val opencodeModel = dotEnv["OPENCODE_MODEL"].orEmpty().ifBlank { "deepseek-v4-flash" }
val opencodeChatCompletionsUrl = dotEnv["OPENCODE_CHAT_COMPLETIONS_URL"].orEmpty()
    .ifBlank { "https://opencode.ai/zen/v1/chat/completions" }
val openRouterModel = dotEnv["OPENROUTER_MODEL"].orEmpty().ifBlank { "z-ai/glm-5.2" }
val openRouterChatCompletionsUrl = dotEnv["OPENROUTER_CHAT_COMPLETIONS_URL"].orEmpty()
    .ifBlank { "https://openrouter.ai/api/v1/chat/completions" }
val openRouterGenerationUrl = dotEnv["OPENROUTER_GENERATION_URL"].orEmpty()
    .ifBlank { "https://openrouter.ai/api/v1/generation" }
val sourceVersion = readSemanticVersion(rootProject.projectDir)
val sourceVersionName = rootProject.file("version.txt").readText().trim()
val buildLabel = gradlePropertyOrEnv("TOASTLIFT_BUILD_LABEL")
    .ifBlank { System.getenv("GITHUB_SHA")?.take(12).orEmpty().ifBlank { "local" } }
val stagingStoreFile = signingPropertyEnvOrDotEnv("TOASTLIFT_STAGING_STORE_FILE", dotEnv)
val stagingStorePassword = signingPropertyEnvOrDotEnv("TOASTLIFT_STAGING_STORE_PASSWORD", dotEnv)
val stagingKeyAlias = signingPropertyEnvOrDotEnv("TOASTLIFT_STAGING_KEY_ALIAS", dotEnv)
val stagingKeyPassword = signingPropertyEnvOrDotEnv("TOASTLIFT_STAGING_KEY_PASSWORD", dotEnv)
val hasStagingSigning = listOf(
    stagingStoreFile,
    stagingStorePassword,
    stagingKeyAlias,
    stagingKeyPassword,
).all(String::isNotBlank)
// This is the Play upload key. Google Play App Signing owns the distinct app-signing key.
// The RELEASE aliases support existing local environments during migration.
val playUploadStoreFile = playUploadPropertyEnvOrDotEnv("STORE_FILE", dotEnv)
val playUploadStorePassword = playUploadPropertyEnvOrDotEnv("STORE_PASSWORD", dotEnv)
val playUploadKeyAlias = playUploadPropertyEnvOrDotEnv("KEY_ALIAS", dotEnv)
val playUploadKeyPassword = playUploadPropertyEnvOrDotEnv("KEY_PASSWORD", dotEnv)
val hasPlayUploadSigning = listOf(
    playUploadStoreFile,
    playUploadStorePassword,
    playUploadKeyAlias,
    playUploadKeyPassword,
).all(String::isNotBlank)

android {
    namespace = "dev.toastlabs.toastlift"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.toastlabs.toastlift"
        minSdk = 26
        targetSdk = 36
        versionCode = sourceVersion.versionCode
        versionName = sourceVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasStagingSigning) {
            create("staging") {
                storeFile = rootProject.file(stagingStoreFile)
                storePassword = stagingStorePassword
                keyAlias = stagingKeyAlias
                keyPassword = stagingKeyPassword
            }
        }
        if (hasPlayUploadSigning) {
            create("playUpload") {
                storeFile = rootProject.file(playUploadStoreFile)
                storePassword = playUploadStorePassword
                keyAlias = playUploadKeyAlias
                keyPassword = playUploadKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev.$buildLabel"
            buildConfigField("String", "FEATURE_CONFIG_ASSET", "\"feature-config.debug.json\"")
            buildConfigField("boolean", "PRODUCTION_FEATURE_CONFIG", "false")
            buildConfigField("String", "GEMINI_API_KEY", "\"${escapeBuildConfig(dotEnv["GEMINI_API_KEY"].orEmpty())}\"")
            buildConfigField("String", "GEMINI_PRIMARY_MODEL", "\"${escapeBuildConfig(dotEnv["GEMINI_PRIMARY_MODEL"].orEmpty())}\"")
            buildConfigField("String", "CUSTOM_EXERCISE_AI_PROVIDER", "\"${escapeBuildConfig(customExerciseAiProvider)}\"")
            buildConfigField("String", "OPENCODE_API_KEY", "\"${escapeBuildConfig(dotEnv["OPENCODE_API_KEY"].orEmpty())}\"")
            buildConfigField("String", "OPENCODE_MODEL", "\"${escapeBuildConfig(opencodeModel)}\"")
            buildConfigField("String", "OPENCODE_CHAT_COMPLETIONS_URL", "\"${escapeBuildConfig(opencodeChatCompletionsUrl)}\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"${escapeBuildConfig(dotEnv["OPENROUTER_API_KEY"].orEmpty())}\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"${escapeBuildConfig(openRouterModel)}\"")
            buildConfigField("String", "OPENROUTER_CHAT_COMPLETIONS_URL", "\"${escapeBuildConfig(openRouterChatCompletionsUrl)}\"")
            buildConfigField("String", "OPENROUTER_GENERATION_URL", "\"${escapeBuildConfig(openRouterGenerationUrl)}\"")
        }
        create("staging") {
            // Production behavior, debug signing. The suffix lets it coexist with
            // the full-featured debug app on a tester's phone. CI supplies the
            // dedicated staging signer; local builds keep Android's debug signer.
            initWith(getByName("debug"))
            applicationIdSuffix = ".staging"
            versionNameSuffix = "-rc.$buildLabel"
            matchingFallbacks += listOf("debug")
            if (hasStagingSigning) {
                signingConfig = signingConfigs.getByName("staging")
            }

            buildConfigField("String", "FEATURE_CONFIG_ASSET", "\"feature-config.production.json\"")
            buildConfigField("boolean", "PRODUCTION_FEATURE_CONFIG", "true")
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
            buildConfigField("String", "GEMINI_PRIMARY_MODEL", "\"\"")
            buildConfigField("String", "CUSTOM_EXERCISE_AI_PROVIDER", "\"\"")
            buildConfigField("String", "OPENCODE_API_KEY", "\"\"")
            buildConfigField("String", "OPENCODE_MODEL", "\"\"")
            buildConfigField("String", "OPENCODE_CHAT_COMPLETIONS_URL", "\"\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"\"")
            buildConfigField("String", "OPENROUTER_CHAT_COMPLETIONS_URL", "\"\"")
            buildConfigField("String", "OPENROUTER_GENERATION_URL", "\"\"")
        }
        release {
            isMinifyEnabled = false
            if (hasPlayUploadSigning) {
                signingConfig = signingConfigs.getByName("playUpload")
            }
            buildConfigField("String", "FEATURE_CONFIG_ASSET", "\"feature-config.production.json\"")
            buildConfigField("boolean", "PRODUCTION_FEATURE_CONFIG", "true")
            buildConfigField("String", "GEMINI_API_KEY", "\"\"")
            buildConfigField("String", "GEMINI_PRIMARY_MODEL", "\"\"")
            buildConfigField("String", "CUSTOM_EXERCISE_AI_PROVIDER", "\"\"")
            buildConfigField("String", "OPENCODE_API_KEY", "\"\"")
            buildConfigField("String", "OPENCODE_MODEL", "\"\"")
            buildConfigField("String", "OPENCODE_CHAT_COMPLETIONS_URL", "\"\"")
            buildConfigField("String", "OPENROUTER_API_KEY", "\"\"")
            buildConfigField("String", "OPENROUTER_MODEL", "\"\"")
            buildConfigField("String", "OPENROUTER_CHAT_COMPLETIONS_URL", "\"\"")
            buildConfigField("String", "OPENROUTER_GENERATION_URL", "\"\"")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.15"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/assets/database"))
}

val copySeedDatabase by tasks.registering(Copy::class) {
    from(rootProject.file("functional_fitness_workout_generator.sqlite"))
    into(layout.buildDirectory.dir("generated/assets/database"))
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(copySeedDatabase)
}

tasks.matching { it.name.contains("lint", ignoreCase = true) }.configureEach {
    dependsOn(copySeedDatabase)
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.01.01")

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3:1.3.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.runtime:runtime")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("com.appreveal:appreveal:0.10.0")
    // Staging is a debug-signed production-feature build. Keep AppReveal here
    // solely so it can be visually audited before the Play artifact is built.
    add("stagingImplementation", "com.appreveal:appreveal:0.10.0")
    releaseImplementation("com.appreveal:appreveal-noop:0.10.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}
