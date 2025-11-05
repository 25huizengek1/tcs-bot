package nl.bartoostveen.tcsbot.database

import nl.bartoostveen.tcsbot.suspendTransaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.ULongEntity
import org.jetbrains.exposed.v1.dao.ULongEntityClass
import org.jetbrains.exposed.v1.jdbc.upsert

object Courses : ULongIdTable("courses") {
  var canvasId = varchar("canvas_id", 20).uniqueIndex()
  var newest = long("newest")
}

class Course(id: EntityID<ULong>) : ULongEntity(id) {
  companion object : ULongEntityClass<Course>(Courses)

  var canvasId by Courses.canvasId
  var newest by Courses.newest
}

suspend fun getNewest(courseId: String) = suspendTransaction {
  Course
    .find { Courses.canvasId eq courseId }
    .firstOrNull()
    ?.newest
}

suspend fun setNewest(courseId: String, newest: Long) = suspendTransaction {
  Courses.upsert {
    it[this.canvasId] = courseId
    it[this.newest] = newest
  }
}
