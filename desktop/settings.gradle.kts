// The Windows desktop app is its own Gradle build so it never needs the
// Android SDK. Shared Kotlin sources are pulled in from ../app; see
// build.gradle.kts.
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}

rootProject.name = "OneClickCopyDesktop"
