package nl.bartoostveen.tcsbot.database

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.dao.ULongEntity
import org.jetbrains.exposed.v1.dao.ULongEntityClass

object Courses : ULongIdTable("courses") {
  val canvasId = varchar("canvas_id", 20)
  val newest = long("newest").nullable()
  val primary = bool("is_primary").default(false)
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
  var guild by Guild referencedOn Courses.guild
}
