@file:OptIn(ExperimentalTime::class)

package nl.bartoostveen.tcsbot.command

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.send
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import nl.bartoostveen.tcsbot.canvas.Announcement
import nl.bartoostveen.tcsbot.canvas.getNewAnnouncements
import nl.bartoostveen.tcsbot.database.Guild
import nl.bartoostveen.tcsbot.database.editGuild
import nl.bartoostveen.tcsbot.database.getGuild
import nl.bartoostveen.tcsbot.util.*
import org.jetbrains.exposed.v1.dao.with
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

context(list: CommandListUpdateAction)
fun JDA.announceCommands(reload: (() -> Unit)? = startCron(this)) {
  with(list) {
    slash("announce", "Send an announcement") {
      restrict(guild = true, adminPermissions)
      option<String>("text", "The announcement body", required = true)
    }

    slash("setannouncementchannel", "Set the announcement channel for this guild") {
      restrict(guild = true, adminPermissions)

      option<String>("text", "The text above the announcement")
    }

    if (reload != null) slash("reloadannouncements", "Restart cron timer") {
      restrict(guild = true, adminPermissions)
    }
  }

  onCommand("announce") { event ->
    val body = event.getOption<String>("text")!!
    +event.deferReply(true)

    val guildData = runCatching { getGuild(event.guild!!.id) }.getOrNull()
    val channelId = guildData?.announcementChannel
      ?: return@onCommand +event.hook.editOriginal("No announcement channel set!")

    (event.guild?.getChannel(channelId) as? TextChannel)?.send(
      content = guildData.announcementText ?: "",
      embeds = listOf(
        Embed(
          title = "Announcement",
          authorName = event.member?.nickname ?: event.user.name,
          authorIcon = event.member?.avatarUrl ?: event.user.effectiveAvatarUrl,
          timestamp = Clock.System.now().toJavaInstant(),
          description = body.trimmedAsDescription
        )
      )
    )?.queue() ?: return@onCommand +event.hook.editOriginal("Channel <#$channelId> not found!")

    +event.hook.editOriginal("Sent announcement")
  }

  onCommand("setannouncementchannel") { event ->
    +event.deferReply(true)
    val text = event.getOption<String>("text")

    runCatching {
      editGuild(event.guild!!.id) {
        announcementText = text
        announcementChannel = event.channelId!!
      }
    }
      .printException()
      .onSuccess {
        +event.hook.editOriginal("Successfully set announcement channel to ${event.channel!!.asMention}")
      }.onFailure {
        +event.hook.editOriginal("Failed to set announcement channel, check logs!")
      }
  }

  if (reload != null) onCommand("reloadannouncements") { event ->
    +event.deferReply(true)
    +event.hook.editOriginal("Sent reload signal")
    reload()
  }
}

private val flexmarkdown = FlexmarkHtmlConverter.builder().build()

fun JDA.sendAnnouncements(guildId: String, channelId: String, text: String?, announcements: List<Announcement>) =
  announcements.forEach { announcement ->
    (getGuildById(guildId)?.getChannel(channelId) as? TextChannel)
      ?.send(
        content = text ?: "",
        embeds = listOf(
          Embed(
            title = announcement.title,
            authorName = announcement.authorName,
            authorIcon = announcement.author?.avatarUrl,
            timestamp = announcement.postedAt.toJavaInstant(),
            description = flexmarkdown.convert(announcement.message).trimmedAsDescription,
            url = announcement.url
          )
        )
      )?.queue()
  }

// TODO: Should probably extract out the 'being able to restart a loop' part
/**
 * @return The reload function of the ongoing cron
 */
fun startCron(
  jda: JDA,
  coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
): () -> Unit {
  val reloadChannel = Channel<Unit>()

  coroutineScope.launch {
    while (isActive) {
      val job = launch {
        while (isActive) {
          runCatching {
            suspendTransaction {
              Guild.all().with(Guild::courses).forEach { guild ->
                val channel = guild.announcementChannel ?: return@forEach

                runCatching {
                  // wrapped inside another transaction so if one guild fails we don't bail out
                  val announcements = suspendTransaction { getNewAnnouncements(guild) }
                  jda.sendAnnouncements(
                    guildId = guild.discordId,
                    channelId = channel,
                    text = guild.announcementText,
                    announcements = announcements
                  )
                }.printException()
              }
            }

            delay(1.minutes)
          }.printException()
        }
      }

      reloadChannel.receiveCatching()
      job.cancel()
    }
  }

  return { reloadChannel.trySend(Unit) }
}
