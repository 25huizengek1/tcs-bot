package nl.bartoostveen.tcsbot.database

import nl.bartoostveen.tcsbot.suspendTransaction
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.ULongIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.ULongEntity
import org.jetbrains.exposed.v1.dao.ULongEntityClass
import org.jetbrains.exposed.v1.dao.with
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.upsert

object GuildMembers : Table("guild_members") {
  val guild = reference(
    name = "guild",
    foreign = Guilds,
    onDelete = ReferenceOption.CASCADE,
    onUpdate = ReferenceOption.CASCADE
  )
  val member = reference(
    name = "member",
    foreign = Members,
    onDelete = ReferenceOption.CASCADE,
    onUpdate = ReferenceOption.CASCADE
  )

  override val primaryKey = PrimaryKey(guild, member)
}

object Members : ULongIdTable("members") {
  val discordId = varchar("discord_id", 20).uniqueIndex()
  val authNonce = varchar("auth_nonce", 16).nullable().uniqueIndex()
  val name = text("name").nullable()
  val email = text("email").nullable().uniqueIndex()
}

class Member(id: EntityID<ULong>) : ULongEntity(id) {
  companion object : ULongEntityClass<Member>(Members)

  val guilds by Guild via GuildMembers
  var discordId by Members.discordId
  var authNonce by Members.authNonce
  var name by Members.name
  var email by Members.email
}

suspend fun getMember(
  discordId: String,
  guildId: String? = null,
  fetchGuilds: Boolean = false
) = suspendTransaction {
  Member
    .find { Members.discordId eq discordId }
    .let { if (fetchGuilds) it else it }
    .firstOrNull()
    // add member to guild if it exists
    ?.also { member ->
      guildId?.let { getGuild(it) }?.let { guild ->
        GuildMembers.upsert(GuildMembers.guild, GuildMembers.member) {
          it[this.guild] = guild.id
          it[this.member] = member.id
        }
      }
    }
}

suspend fun getMemberByNonce(nonce: String, eager: Boolean = true) = suspendTransaction {
  Member
    .find { Members.authNonce eq nonce }
    .let {
      if (eager) it.with(Member::guilds) else it
    }
    .firstOrNull()
}

// editMember always takes a guildId in order to keep updated with whatever is going on on Discord
suspend fun editMember(discordId: String, guildId: String? = null, operation: Member.() -> Unit) = suspendTransaction {
  editMember(
    member = (getMember(discordId) ?: let {
      val member = Member.new { this.discordId = discordId }
      commit()
      member
    }),
    guildId = guildId,
    operation = operation
  )
}

suspend fun editMember(
  member: Member,
  guildId: String? = null,
  operation: Member.() -> Unit
) = suspendTransaction {
  val member = member.apply(operation)

  val guild = if (guildId == null) return@suspendTransaction
  else Guild.find { Guilds.discordId eq guildId }.firstOrNull() ?: error("Guild does not exist")

  if (guild !in member.guilds) GuildMembers.insert {
    it[this.guild] = guild.id
    it[this.member] = member.id
  }
}
