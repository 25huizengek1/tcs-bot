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
import io.github.crackthecodeabhi.kreds.args.SetOption
import io.ktor.http.encodeURLPath
import io.ktor.util.generateNonce
import kotlinx.serialization.Serializable
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Role
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import nl.bartoostveen.tcsbot.*
import java.awt.Color
import kotlin.math.min
import kotlin.time.Duration.Companion.hours

context(list: CommandListUpdateAction)
fun JDA.verifyCommands() {
  val redis = AppConfig.redisClient ?: return

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
    if (redis.exists("verifiedrole:${event.guild!!.id}") == 0L)
      return@onCommand +event.hook.editOriginal("Set the verified role first using /setverifiedrole!")
    +event.hook.editOriginal(":white_check_mark:")

    (event.channel as? TextChannel)?.sendMessage(MessageCreate {
      embeds += Embed(
        title = "Before you can access significant channels, you need to verify yourself.",
        description = "You can log in using your UT account by tapping the button below\n" +
          "We won't store any information (see Microsoft authentication dialog) except for your email\n\n" +
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

    val nonce = generateNonce()
    runCatching {
      redis.set(
        key = "nonce:$nonce",
        value = json.encodeToString(
          GuildMemberRef(
            guild = event.guild!!.id,
            member = event.member!!.id
          )
        ),
        setOption = SetOption.Builder(exSeconds = 1.hours.inWholeSeconds.toULong()).build()
      )
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
      redis.set("verifiedrole:${event.guild!!.id}", role.id)
    }.printException().onFailure {
      return@onCommand +event.hook.editOriginal("An error occurred")
    }

    +event.hook.editOriginal(":white_check_mark:")
  }
}

@Serializable
data class GuildMemberRef(
  val guild: String,
  val member: String
)

suspend fun JDA.assignRole(name: String, memberRef: String) = assignRole(
  name,
  json.decodeFromString<GuildMemberRef>(memberRef)
)

suspend fun JDA.assignRole(name: String, memberRef: GuildMemberRef) = runCatching {
  val role = AppConfig.redisClient?.get("verifiedrole:${memberRef.guild}")
    ?: error("Role not set, should be unreachable")
  val guild = getGuildById(memberRef.guild) ?: error("Guild does not exist anymore")
  val member = guild.retrieveMemberById(memberRef.member).await() ?: error("Member left guild")
  +guild.addRoleToMember(member, guild.getRoleById(role) ?: error("Role does not exist anymore"))
  +member.modifyNickname(name.take(min(name.length, 32)))
}.printException().isSuccess
