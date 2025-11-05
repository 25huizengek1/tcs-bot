package nl.bartoostveen.tcsbot.database

import nl.bartoostveen.tcsbot.suspendTransaction
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

object Guilds : ULongIdTable("guilds") {
  val discordId = varchar("discord_id", 20).uniqueIndex()
  val announcementChannel = varchar("announcement_channel_id", 20).nullable()
  val announcementText = text("announcement_text").nullable()
  val verifiedRole = varchar("verified_role_id", 20).nullable()
}

class Guild(id: EntityID<ULong>) : ULongEntity(id) {
  companion object : ULongEntityClass<Guild>(Guilds)

  var discordId by Guilds.discordId
  var announcementChannel by Guilds.announcementChannel
  var announcementText by Guilds.announcementText
  var verifiedRole by Guilds.verifiedRole
  val members by Member via GuildMembers
  val roles by GuildRole referrersOn GuildRoles.guild
}

object GuildRoles : ULongIdTable("guild_roles") {
  val guild = reference(
    name = "guild",
    foreign = Guilds,
    onDelete = ReferenceOption.CASCADE,
    onUpdate = ReferenceOption.CASCADE
  )
  val discordId = varchar("discord_id", 20).index()
  val description = text("description").nullable()
  val menuName = varchar("menu_name", 64).index()
}

class GuildRole(id: EntityID<ULong>) : ULongEntity(id) {
  companion object : ULongEntityClass<GuildRole>(GuildRoles)

  var guild by Guild referencedOn GuildRoles.guild
  var discordId by GuildRoles.discordId
  var description by GuildRoles.description
  var menuName by GuildRoles.menuName
}

suspend fun getGuild(discordId: String) = suspendTransaction {
  Guild
    .find { Guilds.discordId eq discordId }
    .firstOrNull()
}

suspend fun editGuild(discordId: String, operation: Guild.() -> Unit) = suspendTransaction {
  (getGuild(discordId) ?: Guild.new { this.discordId = discordId }).apply(operation)
}

suspend fun getRole(guildId: String, roleId: String, menuName: String) = suspendTransaction {
  (GuildRoles innerJoin Guilds)
    .select(GuildRoles.columns)
    .where {
      (Guilds.discordId eq guildId) and
        (GuildRoles.discordId eq roleId) and
        (GuildRoles.menuName eq menuName)
    }
    .let { GuildRole.wrapRows(it) }
    .firstOrNull()
}

suspend fun getRoles(guildId: String, menuName: String) = suspendTransaction {
  (GuildRoles innerJoin Guilds)
    .select(GuildRoles.columns)
    .where { (Guilds.discordId eq guildId) and (GuildRoles.menuName eq menuName) }
    .let { GuildRole.wrapRows(it) }
    .toList()
}

// everything should change when calling this, which is why this does not take an "operation"
suspend fun editRole(
  guildId: String,
  roleId: String,
  description: String?,
  menuName: String
) = suspendTransaction {
  val guild = getGuild(guildId) ?: error("Guild does not exist")

  GuildRoles.upsert(Guilds.id) {
    it[this.guild] = guild.id
    it[this.discordId] = roleId
    it[this.description] = description
    it[this.menuName] = menuName
  }
}

suspend fun removeRole(guildId: String, roleId: String, menuName: String) = suspendTransaction {
  (GuildRoles innerJoin Guilds).delete(GuildRoles) {
    (Guilds.discordId eq guildId) and
      (GuildRoles.discordId eq roleId) and
      (GuildRoles.menuName eq menuName)
  }
}
