package nl.bartoostveen.tcsbot.command

import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.restrict
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.components.getOption
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction
import nl.bartoostveen.tcsbot.util.adminPermissions
import nl.bartoostveen.tcsbot.database.addCourse
import nl.bartoostveen.tcsbot.database.editCourse
import nl.bartoostveen.tcsbot.database.getGuild
import nl.bartoostveen.tcsbot.database.removeCourse
import nl.bartoostveen.tcsbot.util.printException
import nl.bartoostveen.tcsbot.util.unaryPlus

context(list: CommandListUpdateAction)
fun JDA.courseCommands() {
  with(list) {
    slash("addcourse", "Link a Canvas course to this guild") {
      restrict(guild = true, adminPermissions)

      option<String>("course", "The course ID", required = true)
      option<Boolean>("primary", "Whether this course should now be primary")
    }

    slash("removecourse", "Remove a Canvas course from this guild") {
      restrict(guild = true, adminPermissions)

      option<String>("course", "The course ID", required = true)
    }

    slash("listcourses", "List all associated courses") {
      restrict(guild = true, adminPermissions)
    }

    slash("setcourseproxyurl", "Use a proxy for a specific course") {
      restrict(guild = true, adminPermissions)

      option<String>("course", "The course to set the proxy url of", required = true)
      option<String>("proxy", "The proxy base url", required = true)
    }
  }

  onCommand("addcourse") { event ->
    +event.deferReply(true)
    val course = event.getOption<String>("course")!!
    val primary = event.getOption<Boolean>("primary") ?: false

    val success = runCatching {
      addCourse(event.guild!!.id, course, primary)
    }.printException().isSuccess
    +event.hook.editOriginal(
      if (success) ":white_check_mark:"
      else "Course already added"
    )
  }

  onCommand("removecourse") { event ->
    +event.deferReply(true)
    val course = event.getOption<String>("course")!!

    val success = runCatching {
      removeCourse(event.guild!!.id, course)
    }.printException().getOrNull().let { it != null && it > 1 }

    +event.hook.editOriginal(
      if (success) ":white_check_mark:"
      else "Course does not exist"
    )
  }

  onCommand("listcourses") { event ->
    +event.deferReply(true)

    runCatching {
      val ids = getGuild(event.guild!!.id, fetchCourses = true)?.courses
        ?.joinToString(separator = ", ") { "**${it.canvasId}**${if (it.primary) " (primary)" else ""}" }
        ?: error("Guild does not exist")
      +event.hook.editOriginal(ids)
    }.printException().onFailure {
      +event.hook.editOriginal("Failed to load courses for this guild")
    }
  }

  onCommand("setcourseproxyurl") { event ->
    +event.deferReply(true)

    val course = event.getOption<String>("course")!!
    val proxy = event.getOption<String>("proxy")!!
      .takeUnless { it.equals("null", ignoreCase = true) }

    val guild = event.guild ?: return@onCommand
    val success = runCatching {
      editCourse(course, guild.id) {
        this.proxyUrl = proxy
      }
    }.printException().getOrDefault(false)

    +event.hook.editOriginal(
      if (success) ":white_check_mark:"
      else "Course not found for ${guild.name}!"
    )
  }
}
