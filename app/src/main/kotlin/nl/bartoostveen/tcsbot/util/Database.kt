package nl.bartoostveen.tcsbot.util

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.currentCoroutineContext
import nl.bartoostveen.tcsbot.AppConfig
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

// Mainly used for testing
class DatabaseElement(val db: Database) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<DatabaseElement>
}

@Suppress("DEPRECATION") // stop deprecating stuff for no reason Jetbrains
suspend fun <T> suspendTransaction(
  context: CoroutineContext? = null,
  db: Database? = null,
  transactionIsolation: Int? = null,
  readOnly: Boolean? = null,
  statement: suspend JdbcTransaction.() -> T
): T = newSuspendedTransaction(
  context = context,
  db = db ?: currentCoroutineContext()[DatabaseElement.Key]?.db ?: AppConfig.database,
  transactionIsolation = transactionIsolation,
  readOnly = readOnly,
  statement = statement
)

fun dataSource(configuration: HikariConfig.() -> Unit) =
  HikariDataSource(HikariConfig().apply(configuration))
