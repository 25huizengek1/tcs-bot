pluginManagement { repositories { gradlePluginPortal() } }

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositories { mavenCentral() }
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

rootProject.name = "tcs-discord-bot"
include(":app")
