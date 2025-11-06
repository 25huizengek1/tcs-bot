package nl.bartoostveen.tcsbot.canvas

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import nl.bartoostveen.tcsbot.AppConfig
import nl.bartoostveen.tcsbot.SerializableInstant
import nl.bartoostveen.tcsbot.database.Guild
import nl.bartoostveen.tcsbot.printException
import kotlin.time.ExperimentalTime

open class CanvasAPI(
  private val baseUrl: String = AppConfig.CANVAS_BASE_URL,
  private val accessToken: String = AppConfig.CANVAS_ACCESS_TOKEN,
  private val json: Json = nl.bartoostveen.tcsbot.json
) {
  companion object Default : CanvasAPI()

  val httpClient = HttpClient(CIO) {
    install(Logging) { level = LogLevel.ALL }
    install(ContentNegotiation) { json(json) }
    defaultRequest {
      url(baseUrl)
      header("Authorization", "Bearer $accessToken")
    }
  }

  suspend fun getAnnouncements(course: List<String>): Result<List<Announcement>> = runCatching {
    httpClient.get("/api/v1/announcements") {
      course.forEach { parameter("context_codes[]", if (it.startsWith("course_")) it else "course_$it") }
      parameter("active_only", "true")
    }.body()
  }

  // Best effort guess, I guess
  suspend fun searchUser(
    name: String,
    email: String? = null,
    course: String
  ): Result<CourseUser?> = runCatching {
    httpClient.get("/api/v1/courses/${course.removePrefix("course_")}/users") {
      parameter("include_inactive", "true")
      parameter("include[]", "enrollments")
      parameter("include[]", "email")
      parameter("search_term", name)
    }.body<List<CourseUser>>()
      .firstOrNull { it.email == email || it.name == name }
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
  val announcements = CanvasAPI.getAnnouncements(guild.courses.map { it.canvasId })
    .printException()
    .getOrElse { return listOf() }

  return announcements
    .groupBy { it.contextCode }
    .flatMap { (contextCode, list) ->
      val announcements = list.sortedBy { it.position }
      runCatching {
        val course = guild.courses.first { it.canvasId == contextCode }
        val oldNewest = course.newest
        val newest = announcements.last().position

        if (oldNewest == newest) return@runCatching listOf()

        course.newest = newest
        if (oldNewest == null) return@runCatching announcements

        announcements.subList(
          announcements.binarySearch { (newest - oldNewest).toInt() } + 1,
          announcements.size
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
