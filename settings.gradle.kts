pluginManagement { repositories { gradlePluginPortal() } }

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
  repositories { mavenCentral() }
  repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
}

plugins {
  id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "tcs-discord-bot"
include(":app")
