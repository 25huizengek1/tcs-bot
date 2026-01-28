package nl.bartoostveen.tcsbot.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpMessageBuilder
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.bartoostveen.tcsbot.AppConfig
import nl.bartoostveen.tcsbot.AppConfig.Environment
import nl.bartoostveen.tcsbot.database.Guild
import nl.bartoostveen.tcsbot.database.withCoursePrefix
import nl.bartoostveen.tcsbot.util.SerializableInstant
import nl.bartoostveen.tcsbot.util.buildTrustManager
import nl.bartoostveen.tcsbot.util.printException
import kotlin.time.ExperimentalTime

sealed class CanvasAPI(
  private val baseUrl: String = AppConfig.CANVAS_BASE_URL,
  private val accessToken: String = AppConfig.CANVAS_ACCESS_TOKEN,
  private val json: Json = nl.bartoostveen.tcsbot.json,
  private val verbose: Boolean = AppConfig.ENVIRONMENT == Environment.DEVELOPMENT,
  private val caBundlePath: String? = AppConfig.CANVAS_CA_BUNDLE
) {
  companion object Default : CanvasAPI()

  val httpClient = HttpClient(CIO) {
    install(Logging) {
      level = if (verbose) LogLevel.ALL else LogLevel.INFO
    }
    install(ContentNegotiation) { json(json) }
    install(HttpRequestRetry) {
      exponentialDelay()
      retryOnException(
        maxRetries = 3,
        retryOnTimeout = true
      )
    }
    defaultRequest {
      url(baseUrl)
    }
    engine {
      https {
        caBundlePath?.let { trustManager = buildTrustManager(it) }
      }
    }
  }

  suspend fun getAnnouncements(
    course: List<String>,
    proxy: String?
  ): Result<List<Announcement>> = runCatching {
    httpClient.get("${proxy.orEmpty()}/api/v1/announcements") {
      course.forEach { parameter("context_codes[]", it.withCoursePrefix) }
      parameter("active_only", "true")
      if (proxy == null) token()
    }.body()
  }

  suspend fun searchUser(
    name: String,
    email: String? = null,
    course: String,
    proxy: String?
  ): Result<CourseUser?> = runCatching {
    httpClient.get("${proxy.orEmpty()}/api/v1/courses/${course.removePrefix("course_")}/users") {
      parameter("include_inactive", "true")
      parameter("include[]", "enrollments")
      parameter("include[]", "email")
      parameter("search_term", name)
      if (proxy == null) token()
    }.body<List<CourseUser>>()
      .firstOrNull { it.email == email || it.name == name }
  }

  private fun HttpMessageBuilder.token() {
    header("Authorization", "Bearer $accessToken")
  }
}

/**
 * Returns sorted list of all new announcements, unless Redis fails, then returns nothing
 * If Redis is not configured, returns all announcements!
 *
 * Should run in transaction!
 */
@OptIn(ExperimentalTime::class)
suspend fun getNewAnnouncements(guild: Guild): List<Announcement> {
  val announcements = guild.courses
    .groupBy { it.proxyUrl }
    .flatMap { (proxy, courses) ->
      CanvasAPI.getAnnouncements(courses.map { it.canvasId }, proxy)
        .printException()
        .getOrElse { listOf() }
    }

  return announcements
    .groupBy { it.contextCode }
    .flatMap { (contextCode, list) ->
      val courseAnnouncements = list.sortedBy { it.position }

      runCatching {
        val course = guild.courses.first { it.canvasId == contextCode }
        val oldNewest = course.newest
        val newest = courseAnnouncements.last().position

        if (oldNewest == newest) return@runCatching listOf()

        course.newest = newest
        if (oldNewest == null) return@runCatching courseAnnouncements

        courseAnnouncements.subList(
          courseAnnouncements.indexOfFirst { it.position > oldNewest },
          courseAnnouncements.size
        )
      }.getOrElse { listOf() }
    }
}

@OptIn(ExperimentalTime::class)
@Serializable
data class Announcement(
  val position: Long,
  val title: String,
  @SerialName("created_at")
  val createdAt: SerializableInstant,
  @SerialName("posted_at")
  val postedAt: SerializableInstant,
  val author: Author? = null,
  @SerialName("user_name")
  val userName: String,
  @SerialName("context_code")
  val contextCode: String,
  @SerialName("html_url")
  val url: String,
  val message: String
) {
  val authorName get() = author?.displayName ?: userName

  @Serializable
  data class Author(
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_image_url")
    val avatarUrl: String
  )
}

@Serializable
data class CourseUser(
  val enrollments: List<Enrollment> = listOf(),
  val name: String,
  val email: String? = null
) {
  @Serializable
  data class Enrollment(
    @SerialName("type")
    val role: Role
  ) {
    @Serializable
    enum class Role {
      @SerialName("StudentEnrollment")
      Student,

      @SerialName("TaEnrollment")
      TA,

      @SerialName("TeacherEnrollment")
      Teacher
    }
  }
}
