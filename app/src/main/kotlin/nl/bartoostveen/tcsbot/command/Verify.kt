package nl.bartoostveen.tcsbot.command

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onButton
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.interactions.components.row
import dev.minn.jda.ktx.interactions.components.success
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.MessageCreate
import dev.minn.jda.ktx.messages.send
import io.ktor.http.encodeURLPath
import io.ktor.util.generateNonce
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import nl.bartoostveen.tcsbot.AppConfig
import nl.bartoostveen.tcsbot.adminPermissions
import nl.bartoostveen.tcsbot.database.*
import nl.bartoostveen.tcsbot.printException
import nl.bartoostveen.tcsbot.suspendTransaction
import nl.bartoostveen.tcsbot.unaryPlus
import java.awt.Color
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

context(list: CommandListUpdateAction)
fun JDA.verifyCommands() {
  with(list) {
    slash("verify", "Show verification dialog") {
      restrict(guild = true, adminPermissions)
    }

    slash("setverifiedrole", "Set the verified role") {
      restrict(guild = true, adminPermissions)

      option<Role>("role", "The role", required = true)
    }
  }

  onCommand("verify") { event ->
    +event.deferReply(true)
    if (getGuild(event.guild!!.id)?.verifiedRole == null)
      return@onCommand +event.hook.editOriginal("Set the verified role first using /setverifiedrole!")
    +event.hook.editOriginal(":white_check_mark:")

    (event.channel as? TextChannel)?.sendMessage(MessageCreate {
      embeds += Embed(
        title = "Before you can access significant channels, you need to verify yourself.",
        description = "You can log in using your UT account by tapping the button below\n" +
          "This way, we'll update your display name on all module servers you're in, just like in the old official server\n\n" +
          "We won't store any information (see Microsoft authentication dialog) except for your email\n" +
          "Don't believe me? This bot is entirely open source! (go laugh at my awful code!)\n" +
          "https://github.com/25huizengek1/tcs-bot",
        authorIcon = selfUser.effectiveAvatarUrl,
        authorName = selfUser.asTag,
        color = Color.BLUE.rgb
      )
      components += row(
        success("verify", "Link Discord to UT")
      )
    })?.queue()
  }

  onButton("verify") { event ->
    +event.deferReply(true)
    val dbMember = getMember(event.member!!.id)
    if (dbMember?.email != null) {
      assignRole(dbMember)
      return@onButton +event.hook.editOriginal("Already verified, applying changes...")
    }

    val nonce = generateNonce()
    runCatching {
      editMember(event.member!!.id, event.guild!!.id) {
        authNonce = nonce
      }
    }.printException().onFailure {
      return@onButton +event.hook.editOriginal("An error occurred")
    }

    val url = "${AppConfig.HOSTNAME}/oauth/redirect?nonce=${nonce.encodeURLPath()}"
    +event.hook.editOriginal("Press this link to authorize your UT account: **$url**")
  }

  onCommand("setverifiedrole") { event ->
    val role = event.getOption<Role>("role")!!
    +event.deferReply(true)

    runCatching {
      editGuild(event.guild!!.id) {
        verifiedRole = role.id
      }
    }.printException().onFailure {
      return@onCommand +event.hook.editOriginal("An error occurred")
    }

    +event.hook.editOriginal(":white_check_mark:")
  }
}

suspend fun JDA.assignRole(
  name: String,
  email: String,
  nonce: String
) = assignRole(
  name = name,
  email = email,
  dbMember = getMemberByNonce(nonce) ?: error("Invalid nonce")
)

@OptIn(ExperimentalTime::class)
suspend fun JDA.assignRole(name: String, email: String, dbMember: Member): Boolean {
  runCatching {
    editMember(dbMember) {
      this.name = name
      this.email = email
      this.authNonce = null
    }
  }
  runCatching {
    +retrieveUserById(dbMember.discordId).await().openPrivateChannel().await().send(
      embeds = listOf(
        Embed(
          title = "Successfully retrieved UT Account Info!",
          description = "You just verified as **$name** with $email, welcome to our server(s)!",
          authorName = selfUser.asTag,
          authorUrl = selfUser.effectiveAvatarUrl,
          color = Color.GREEN.rgb,
          timestamp = Clock.System.now().toJavaInstant()
        )
      )
    )
  }
  return assignRole(dbMember)
}

suspend fun JDA.assignRole(dbMember: Member): Boolean = runCatching {
  val name = dbMember.name?.let { it.take(min(it.length, 32)) }
    ?: error("Invalid state: member cannot have null name")

  suspendTransaction {
    dbMember.guilds.map { dbGuild ->
      runCatching {
        val guild = getGuildById(dbGuild.discordId) ?: error("Guild does not exist anymore")
        val role = dbGuild.verifiedRole?.let { guild.getRoleById(it) } ?: error("Role does not exist")
        val member = guild.retrieveMemberById(dbMember.discordId).await() ?: error("Member left guild")

        +guild.addRoleToMember(member, role)
        +member.modifyNickname(name)
      }.printException().isSuccess
    }.any { it }
  }
}.printException().getOrDefault(false)
