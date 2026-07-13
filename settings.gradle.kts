pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ToastLift"
val appRevealCompositeBuild = providers.gradleProperty("apprevealCompositeBuild")
    .orElse(providers.environmentVariable("APPREVEAL_COMPOSITE_BUILD"))
    .orNull
    ?.trim()
    ?.takeIf(String::isNotEmpty)
    ?: "../appreveal-toastlift/Android"
val appRevealCompositeBuildDirectory = file(appRevealCompositeBuild)

check(appRevealCompositeBuildDirectory.isDirectory) {
    "AppReveal composite build was not found at ${appRevealCompositeBuildDirectory.path}. " +
        "Set APPREVEAL_COMPOSITE_BUILD (or -PapprevealCompositeBuild) to its Android directory."
}

includeBuild(appRevealCompositeBuildDirectory) {
    dependencySubstitution {
        substitute(module("com.appreveal:appreveal")).using(project(":appreveal"))
        substitute(module("com.appreveal:appreveal-noop")).using(project(":appreveal-noop"))
    }
}
include(":app")
