package nl.bartoostveen.tcsbot.command

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.future.await
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import nl.bartoostveen.tcsbot.util.adminPermissions
import nl.bartoostveen.tcsbot.util.printException
import nl.bartoostveen.tcsbot.util.unaryPlus
import java.util.concurrent.TimeUnit

context(list: CommandListUpdateAction)
fun JDA.modCommands() {
  with(list) {
    slash("kick", "Kick a user, sending them the reason why") {
      restrict(guild = true, adminPermissions)

      option<Member>("member", "Who to kick", required = true)
      option<String>("reason", "The reason they got kicked")
    }
    slash("ban", "Ban a user, sending them the reason why") {
      restrict(guild = true, adminPermissions)

      option<Member>("member", "Who to ban", required = true)
      option<String>("reason", "The reason they got banned")
      option<Int>("deletedays", "Amount of days of messages to delete")
    }
    slash("purge", "Purge x amount of messages") {
      restrict(guild = true, adminPermissions)

      option<Int>("x", "Amount of messages to delete (1-100)", required = true)
    }
  }

  // What even is a DRY
  onCommand("kick") { event ->
    val reason = event.getOption<String>("reason") ?: "No reason given"
    val member = event.getOption<Member>("member")!!

    +event.deferReply(true)

    runCatching {
      val channel = member.user.openPrivateChannel().await()
      channel.send(
        embeds = listOf(
          Embed(
            title = "Kicked from the server",
            description = "You got kicked from **${member.guild.name}**\n\nReason: $reason\nContact the moderators in order to appeal."
          )
        )
      ).await()
    }.printException()
      .onSuccess {
        +event.hook.editOriginal(":white_check_mark:")
      }.onFailure {
        +event.hook.editOriginal("Member kicked, but failed to send them a dm. Maybe they blocked the bot?")
      }
    +member.kick().reason(reason)
  }

  onCommand("ban") { event ->
    val deleteDays = event.getOption<Int>("delete days")
    val reason = event.getOption<String>("reason") ?: "No reason given"
    val member = event.getOption<Member>("member")!!

    +event.deferReply(true)

    runCatching {
      val channel = member.user.openPrivateChannel().await()
      channel.send(
        embeds = listOf(
          Embed(
            title = "Banned from the server",
            description = "You got banned from **${member.guild.name}**\n\nReason: $reason\nContact the moderators in order to appeal."
          )
        )
      ).await()
    }.printException()
      .onSuccess {
        +event.hook.editOriginal(":white_check_mark:")
      }.onFailure {
        +event.hook.editOriginal("Member banned, but failed to send them a dm. Maybe they blocked the bot?")
      }
    +member.ban(deleteDays ?: 0, TimeUnit.DAYS).reason(reason)
  }

  onCommand("purge") { event ->
    val x = event.getOption<Int>("x")!!.coerceIn(1, 100)

    +event.deferReply(true)
    event.messageChannel.purgeMessages(event.messageChannel.iterableHistory.takeAsync(x).await())
    +event.hook.editOriginal("Started deletion of $x messages, this may take some time...")
  }
}
