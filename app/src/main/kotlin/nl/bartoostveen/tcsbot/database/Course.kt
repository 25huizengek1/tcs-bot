package nl.bartoostveen.tcsbot.database

import nl.bartoostveen.tcsbot.util.suspendTransaction
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.ULongEntity
import org.jetbrains.exposed.v1.dao.ULongEntityClass
import org.jetbrains.exposed.v1.jdbc.delete
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.upsert

object Courses : ULongIdTable("courses") {
  val canvasId = varchar("canvas_id", 20)
  val newest = long("newest").nullable()
  val primary = bool("is_primary").default(false)
  val proxyUrl = text("proxy_url").nullable()
  val guild = reference(
    name = "guild_id",
    foreign = Guilds,
    onDelete = ReferenceOption.CASCADE,
    onUpdate = ReferenceOption.CASCADE
  )

  init {
    uniqueIndex(canvasId, guild)
  }
}

class Course(id: EntityID<ULong>) : ULongEntity(id) {
  companion object : ULongEntityClass<Course>(Courses)

  var canvasId by Courses.canvasId
  var newest by Courses.newest
  var primary by Courses.primary
  var proxyUrl by Courses.proxyUrl
  var guild by Guild referencedOn Courses.guild
}

// everything should change when calling this, which is why this does not take an "operation"
suspend fun addCourse(
  guildId: String,
  courseId: String,
  primary: Boolean
) = suspendTransaction {
  val guild = getGuild(guildId, fetchCourses = primary) ?: error("Guild does not exist")

  val courseRow = Courses.upsert(Courses.guild, Courses.canvasId) {
    it[this.guild] = guild.id
    it[this.canvasId] = courseId.withCoursePrefix
  }
  if (primary) {
    commit()

    val id = courseRow[Courses.id]
    guild.courses.forUpdate().forEach {
      it.primary = it.id == id
    }
  }
}

suspend fun getCourse(guildId: String, canvasId: String) = suspendTransaction {
  (Courses innerJoin Guilds)
    .select(Courses.columns)
    .where {
      (Guilds.discordId eq guildId) and
        (Courses.canvasId eq canvasId.withCoursePrefix)
    }
    .firstOrNull()
    ?.let { Course.wrapRow(it) }
}

suspend fun editCourse(
  canvasId: String,
  guildId: String,
  operation: Course.() -> Unit
) = suspendTransaction {
  (getCourse(guildId, canvasId))?.apply(operation) != null
}

suspend fun removeCourse(guildId: String, courseId: String) = suspendTransaction {
  (Courses innerJoin Guilds).delete(Courses) {
    (Guilds.discordId eq guildId) and
      (Courses.canvasId eq courseId.withCoursePrefix)
  }
}

val String.withCoursePrefix get() = if (startsWith("course_")) this else "course_$this"
val String.withoutCoursePrefix get() = removePrefix("course_")
