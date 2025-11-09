package nl.bartoostveen.tcsbot

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import nl.bartoostveen.tcsbot.AppConfig.migrate
import nl.bartoostveen.tcsbot.util.DatabaseElement
import org.jetbrains.exposed.v1.core.DatabaseConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.absolutePathString
import kotlin.io.path.createTempFile

val db = Database.connect(
  url = "jdbc:sqlite:${createTempFile().absolutePathString().also { println("Test database located at: $it") }}",
  databaseConfig = DatabaseConfig {
    keepLoadedReferencesOutOfTransaction = true
  })
  .also {
    transaction(it) {
      migrate(onlyCreate = true)
    }
  }

val databaseElement: CoroutineContext = DatabaseElement(db)

fun databaseTest(
  block: suspend CoroutineScope.() -> Unit
) = runTest {
  withContext(databaseElement, block)
}
