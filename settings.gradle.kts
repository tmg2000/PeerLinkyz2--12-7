pluginManagement {
    repositories {
        gradlePluginPortal()
        google()
        mavenCentral()
        maven("https://guardianproject.github.io/maven/")
        maven("https://raw.githubusercontent.com/guardianproject/gpmaven/master")
        maven("https://jitpack.io")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://guardianproject.github.io/maven/")
        maven("https://raw.githubusercontent.com/guardianproject/gpmaven/master")
        maven("https://jitpack.io")
    }
}

rootProject.name = "PeerLinkyz"
include(":app")