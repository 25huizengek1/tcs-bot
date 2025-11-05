package nl.bartoostveen.tcsbot.command

import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction

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
    // TODO
  }
}
