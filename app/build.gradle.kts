plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  application
}

group = "nl.bartoostveen.tcsbot"
version = "1.0.0"

configurations.all {
  resolutionStrategy {
    failOnNonReproducibleResolution()
  }
}

tasks.withType<AbstractArchiveTask>().configureEach {
  isPreserveFileTimestamps = false
  isReproducibleFileOrder = true
  dirPermissions { unix("755") }
  filePermissions { unix("644") }
}

dependencies {
  implementation(libs.jda)
  implementation(libs.jda.ktx)

  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.serialization)
  implementation(libs.ktor.server.statusPages)
  implementation(libs.ktor.server.htmlBuilder)
  implementation(libs.ktor.server.logging)
  implementation(libs.ktor.server.forwardedHeader)
  implementation(libs.ktor.server.defaultHeaders)
  implementation(libs.ktor.server.conditionalHeaders)
  implementation(libs.ktor.server.cors)
  implementation(libs.ktor.server.compression)
  implementation(libs.ktor.server.metrics)
  implementation(libs.ktor.server.metrics.prometheus)

  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.cio)
  implementation(libs.ktor.client.serialization)
  implementation(libs.ktor.client.logging)
  implementation(libs.ktor.json)

  implementation(libs.logback)
  implementation(libs.exposed.core)
  implementation(libs.exposed.dao)
  implementation(libs.exposed.jdbc)
  implementation(libs.sqlite)
  implementation(libs.postgres)
  implementation(libs.hikaricp)
  implementation(libs.kreds)

  implementation(libs.kotlinx.datetime)
  implementation(libs.flexmark.md)
  implementation(libs.jwt)
  implementation(libs.jwt.jwks)

  testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(libs.junit.jupiter.engine)
}

kotlin {
  jvmToolchain(21)
  compilerOptions {
    freeCompilerArgs.addAll(
      "-Xcontext-parameters"
    )
  }
}

application {
  mainClass = "nl.bartoostveen.tcsbot.MainKt"
}

tasks.named<Test>("test") {
  useJUnitPlatform()
}
