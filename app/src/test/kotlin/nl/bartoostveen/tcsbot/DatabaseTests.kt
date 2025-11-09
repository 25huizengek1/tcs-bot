package nl.bartoostveen.tcsbot

import nl.bartoostveen.tcsbot.database.*
import nl.bartoostveen.tcsbot.util.suspendTransaction
import org.junit.jupiter.api.*

private const val guildId = "1111111111111111111"
private const val roleId = "1111111111111111111"
private const val memberId = "1111111111111111111"
private const val courseId = "12345"

// Dumb tests for someone who forgets dumb constraints all the time
class DatabaseTests {
  @Test
  @Order(1)
  fun `test addCourse`() = databaseTest {
    editGuild(guildId) {}
    addCourse(guildId, courseId, true)
    addCourse(guildId, "23456", true)

    val course = getCourse(guildId, "course_$courseId")
    assertNotNull(course)
    assert(!course.primary)
  }

  @Test
  @Order(2)
  fun `test getGuild and editGuild`() = databaseTest {
    editGuild(guildId) {}
    assertDoesNotThrow {
      getGuild(guildId, fetchCourses = true)!!.courses.count()
    }
    assertThrows<IllegalStateException> {
      getGuild(guildId, fetchCourses = false)!!.courses.count()
    }
  }

  @Test
  @Order(3)
  fun `test getRole, getRoles, editRole`() = databaseTest {
    val menuName = "default"
    editRole(guildId, roleId, null, menuName)
    assert(getRoles(guildId, menuName).any { it.discordId == roleId })
    assertNotNull(getRole(guildId, roleId, menuName))
  }

  @Test
  @Order(4)
  fun `test getMember, editMember, getMember (by nonce)`() = databaseTest {
    editMember(memberId) {}
    assertThrows<NullPointerException> {
      getMember(memberId, fetchGuilds = false)!!.guilds.count()
    }
    assertDoesNotThrow {
      getMember(memberId, fetchGuilds = true)!!.guilds.count()
    }
    assert(getMember(memberId, fetchGuilds = true)!!.guilds.empty())

    val nonce = "thisisrandom"
    editMember(memberId) {
      this.authNonce = nonce
    }
    assert(getMemberByNonce(nonce)!!.discordId == memberId)

    suspendTransaction { // shouldn't fetch members eagerly when testing this
      assert(getMember(memberId, guildId)!!.guilds.any { it.discordId == guildId })
    }
  }
}
