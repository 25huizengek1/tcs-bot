package nl.bartoostveen.tcsbot.database

import nl.bartoostveen.tcsbot.util.suspendTransaction
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.ULongEntity
import org.jetbrains.exposed.v1.dao.ULongEntityClass
import org.jetbrains.exposed.v1.dao.with

object Guilds : ULongIdTable("guilds") {
  val discordId = varchar("discord_id", 20).uniqueIndex()
  val announcementChannel = varchar("announcement_channel_id", 20).nullable()
  val announcementText = text("announcement_text").nullable()
  val verifiedRole = varchar("verified_role_id", 20).nullable()
  val teacherRole = varchar("teacher_role_id", 20).nullable()
  val enrolledRole = varchar("enrolled_role_id", 20).nullable()
}

class Guild(id: EntityID<ULong>) : ULongEntity(id) {
  companion object : ULongEntityClass<Guild>(Guilds)

  var discordId by Guilds.discordId
  var announcementChannel by Guilds.announcementChannel
  var announcementText by Guilds.announcementText
  var verifiedRole by Guilds.verifiedRole
  var teacherRole by Guilds.teacherRole
  var enrolledRole by Guilds.enrolledRole
  val members by Member via GuildMembers
  val courses by Course referrersOn Courses.guild
  val roles by GuildRole referrersOn GuildRoles.guild

  val allRoles get() = listOf(verifiedRole, teacherRole, enrolledRole)

  val primaryCourse: Course? get() = courses.firstOrNull { it.primary } ?: courses.firstOrNull()
}

suspend fun getGuild(discordId: String, fetchCourses: Boolean = false) = suspendTransaction {
  Guild
    .find { Guilds.discordId eq discordId }
    .let { if (fetchCourses) it.with(Guild::courses) else it }
    .firstOrNull()
}

suspend fun editGuild(discordId: String, operation: Guild.() -> Unit) = suspendTransaction {
  (getGuild(discordId) ?: Guild.new { this.discordId = discordId }).apply(operation)
}
