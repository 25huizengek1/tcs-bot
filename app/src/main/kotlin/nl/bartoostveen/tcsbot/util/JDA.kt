package nl.bartoostveen.tcsbot.util

import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.requests.RestAction

val adminPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)

operator fun RestAction<*>.unaryPlus() = queue()
