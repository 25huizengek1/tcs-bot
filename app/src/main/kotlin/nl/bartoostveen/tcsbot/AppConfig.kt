package nl.bartoostveen.tcsbot

import io.github.crackthecodeabhi.kreds.connection.Endpoint
import io.github.crackthecodeabhi.kreds.connection.newClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlin.properties.PropertyDelegateProvider
import kotlin.properties.ReadOnlyProperty

object AppConfig {
  private val id: (String) -> String = { it }
  private val int: (String) -> Int = { it.toInt() }
  private fun <T> list(mapper: (String) -> T): (String) -> List<T> = {
    it.split(',').map(mapper)
  }

  private inline fun <reified T : Enum<T>> enum(): (String) -> T = {
    enumValues<T>().first { value -> value.name.equals(it, ignoreCase = true) }
  }

  private val required = { error("Property required, but not given!") }

  private fun <T : Any> variable(
    default: () -> T,
    mapper: (String) -> T,
    envName: String? = null
  ) = PropertyDelegateProvider<Any?, ReadOnlyProperty<Any?, T>> { _, property ->
    val name = envName ?: property.name
    ReadOnlyProperty { _, _ ->
      runCatching {
        env[name]?.let { mapper(it) } ?: default()
      }.getOrElse { th ->
        throw RuntimeException("Property $name in Configuration had an error mapping its value", th)
      }
    }
  }

  private fun variable(envName: String? = null) = variable(
    default = required,
    mapper = id,
    envName = envName
  )

  enum class Environment {
    DEVELOPMENT, PRODUCTION
  }

  val DISCORD_ACCESS_TOKEN by variable()
  val CANVAS_ACCESS_TOKEN by variable()
  val CANVAS_COURSE_CODE by variable(::emptyList, list(id))
  val CANVAS_BASE_URL by variable({ "https://canvas.utwente.nl" }, id)
  val REDIS_CONNECTION_STRING by variable()
  val MICROSOFT_CLIENT_ID by variable()
  val MICROSOFT_CLIENT_SECRET by variable()
  val MICROSOFT_AUTH_ENDPOINT by variable()
  val HOST by variable({ "0.0.0.0" }, id)
  val PORT by variable({ 6969 }, int)
  val HOSTNAME by variable()
  val ENVIRONMENT by variable({ Environment.PRODUCTION }, enum<Environment>())
  val METRICS_PREFIX by variable({ "100." }, id)

  val redisClient =
    runCatching { newClient(Endpoint.from(REDIS_CONNECTION_STRING)) }
      .printException()
      .getOrNull()

  val httpClient = HttpClient(CIO) {
    install(Logging) {
      level = if (ENVIRONMENT == Environment.DEVELOPMENT) LogLevel.ALL else LogLevel.INFO
    }
    install(ContentNegotiation) { json(json) }
    install(HttpRequestRetry) {
      exponentialDelay()
      retryOnException(
        maxRetries = 3,
        retryOnTimeout = true
      )
    }
  }
}
