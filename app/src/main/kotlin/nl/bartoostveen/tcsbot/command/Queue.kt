package nl.bartoostveen.tcsbot.command

import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.slash
import io.github.crackthecodeabhi.kreds.connection.KredsClient
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import nl.bartoostveen.tcsbot.util.unaryPlus

context(list: CommandListUpdateAction)
fun JDA.queueCommands(redis: KredsClient) {
  with(list) {
    slash("queue", "Get paired with a random student") {
      restrict(guild = true)
    }
  }

  onCommand("queue") { event ->
    +event.deferReply()

    val guild = event.guild!!
    val key = "queue:${guild.id}"
    val other = runCatching {
      redis.getDel(key)
    }.mapCatching { id ->
      id?.let { guild.retrieveMemberById(it).await() }
    }.getOrNull()

    if (other == null) runCatching {
      redis.set(key, event.user.id)
    }.onFailure {
      return@onCommand +event.hook.editOriginal("Joining queue failed for some reason")
    }

    +event.hook.editOriginal(
      when {
        other == null -> "Joined the queue"
        other.id == event.user.id -> "You left the queue."
        else -> "You got paired with ${other.asMention}!"
      }
    )
  }
}
