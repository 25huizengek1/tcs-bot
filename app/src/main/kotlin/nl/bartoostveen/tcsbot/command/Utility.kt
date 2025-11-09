package nl.bartoostveen.tcsbot.command

import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.components.getOption
import dev.minn.jda.ktx.messages.Embed
import dev.minn.jda.ktx.messages.editMessage
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import nl.bartoostveen.tcsbot.database.getMember
import nl.bartoostveen.tcsbot.util.unaryPlus
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.toJavaInstant

@OptIn(ExperimentalTime::class)
context(list: CommandListUpdateAction)
fun JDA.utilityCommands() {
  with(list) {
    slash("email", "View the email of a fellow guild member") {
      restrict(guild = true)
      option<Member>("member", "The name of the student to view the email of", required = true)
    }
  }

  onCommand("email") { event ->
    val member = event.getOption<Member>("member")!!
    +event.deferReply(true)

    val email = if (member.id == selfUser.id) return@onCommand +event.hook.editOriginal("Are you hitting on me?")
    else getMember(member.id)?.email
      ?: return@onCommand +event.hook.editOriginal("I don't know the email of ${member.asMention}!")

    +event.hook.editMessage(
      content = "", // yes this line is needed
      embeds = listOf(
        Embed(
          authorIcon = member.effectiveAvatarUrl,
          authorName = member.effectiveName,
          description = "${member.asMention}'s email is: **[$email](mailto:$email)**",
          timestamp = Clock.System.now().toJavaInstant()
        )
      )
    )
  }
}
