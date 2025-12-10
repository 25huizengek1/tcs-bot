package nl.bartoostveen.tcsbot.util

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.requests.RestAction

val adminPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)

operator fun RestAction<*>.unaryPlus() = queue()

val String.trimmedAsDescription get() = trim().take(MessageEmbed.DESCRIPTION_MAX_LENGTH)
