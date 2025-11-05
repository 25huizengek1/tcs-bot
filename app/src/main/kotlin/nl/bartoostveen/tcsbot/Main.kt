package nl.bartoostveen.tcsbot

import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.default
import dev.minn.jda.ktx.jdabuilder.intents
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.defaultheaders.DefaultHeaders
import io.ktor.server.plugins.forwardedheaders.ForwardedHeaders
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.serialization.json.Json
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.events.session.ReadyEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import nl.bartoostveen.tcsbot.command.announceCommands
import nl.bartoostveen.tcsbot.command.modCommands
import nl.bartoostveen.tcsbot.command.roleCommands
import nl.bartoostveen.tcsbot.command.utilityCommands
import nl.bartoostveen.tcsbot.command.verifyCommands
import nl.bartoostveen.tcsbot.routing.authRouter
import java.io.File

val env = System.getenv().toMutableMap()

val json = Json {
  isLenient = true
  ignoreUnknownKeys = true
}

fun main(args: Array<String>) {
  val envFile = File(args.firstOrNull() ?: ".env")
  if (envFile.exists()) runCatching {
    envFile.readLines().map { line ->
      val (key, value) = line.splitAtIndex(line.indexOf('='))
      env[key] = value
      println("Loading env var $key")
    }
  }.onFailure {
    println("Note: loading $envFile env file failed! Please check the formatting and try again.")
  }

  val jda = default(AppConfig.DISCORD_ACCESS_TOKEN, enableCoroutines = true) {
    intents += listOf(GatewayIntent.MESSAGE_CONTENT, GatewayIntent.GUILD_MEMBERS)
    setMemberCachePolicy(MemberCachePolicy.ALL)
    setActivity(Activity.watching("Canvas announcements"))
  }

  jda.addEventListener(GlobalEventListener)
  jda.awaitReady()

  +jda.updateCommands {
    jda.announceCommands()
    jda.modCommands()
    jda.roleCommands()
    jda.utilityCommands()
    jda.verifyCommands()
  }

  println("Starting Ktor server on ${AppConfig.HOST}:${AppConfig.PORT}")
  println(
    "Please note this server should always be behind a trusted reverse proxy, since this server assumes the X-Forwarded-For header is valid"
  )

  embeddedServer(
    factory = Netty,
    host = AppConfig.HOST,
    port = AppConfig.PORT
  ) {
    install(ContentNegotiation) { json(json) }
    statusPages()
    processing()
    monitoring()
    routing {
      get("/") {
        call.respondText(
          "Greetings! You have reached the TCS bot auth server. " +
            "There is nothing really you can do without being a part of our Discord server. " +
            "How did you even get here?"
        )
      }
      authRouter(jda)
    }
  }.start(wait = true)
}

object GlobalEventListener : ListenerAdapter() {
  override fun onReady(event: ReadyEvent) {
    println("[JDA] Bot ready")
    println("Operating as ${event.jda.selfUser.asTag}")
    println("Watching Canvas course IDs -> ${AppConfig.CANVAS_COURSE_CODE}")
  }
}

private fun Application.statusPages() = install(StatusPages) {
  exception<Throwable> { call, cause ->
    when (cause) {
      is HttpResponseException -> call.respondText(text = cause.body, status = cause.code)
      is NotFoundException ->
        call.respondText(
          text = "404: ${call.request.path()} not found", status = HttpStatusCode.NotFound
        )

      else -> call.respondText(text = "500: $cause", status = HttpStatusCode.InternalServerError)
    }
  }
}

private fun Application.processing() {
  install(ForwardedHeaders)
  install(XForwardedHeaders)
  install(DefaultHeaders) { header("X-Engine", "Ktor") }
  install(CORS) {
    allowMethod(HttpMethod.Options)
    allowMethod(HttpMethod.Put)
    allowMethod(HttpMethod.Delete)
    allowMethod(HttpMethod.Patch)
    allowHeader(HttpHeaders.Authorization)
    allowNonSimpleContentTypes = true
    allowCredentials = true
    if (AppConfig.ENVIRONMENT == AppConfig.Environment.DEVELOPMENT) anyHost()
    else allowHost(AppConfig.HOSTNAME.substringAfter("://"))
  }
  install(ConditionalHeaders)
  install(Compression) { gzip { priority = 1.0 } }
}

private fun Application.monitoring() {
  val registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

  install(CallLogging)
  install(MicrometerMetrics) {
    this.registry = registry
  }

  routing {
    get("/metrics") {
      if (
        AppConfig.ENVIRONMENT == AppConfig.Environment.PRODUCTION &&
        !call.request.origin.remoteHost.startsWith(AppConfig.METRICS_PREFIX)
      ) notFound()

      call.respond(registry.scrape())
    }
  }
}
