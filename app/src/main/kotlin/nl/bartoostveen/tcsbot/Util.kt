@file:OptIn(ExperimentalTime::class)

package nl.bartoostveen.tcsbot

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.routing.RoutingContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.requests.RestAction
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.JdbcTransaction
import org.jetbrains.exposed.v1.jdbc.transactions.experimental.newSuspendedTransaction
import java.security.MessageDigest
import java.time.format.DateTimeFormatter
import kotlin.coroutines.CoroutineContext
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

fun dataSource(configuration: HikariConfig.() -> Unit) =
  HikariDataSource(HikariConfig().apply(configuration))

fun String.splitAtIndex(index: Int) = require(index in 0..length).let {
  take(index) to substring(index + 1)
}

fun <T> Result<T>.printException() = also { it.exceptionOrNull()?.printStackTrace() }

object InstantSerializer : KSerializer<Instant> {
  private val format = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX")
  override val descriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

  override fun serialize(encoder: Encoder, value: Instant) {
    encoder.encodeString(format.format(value.toJavaInstant()))
  }

  override fun deserialize(decoder: Decoder) =
    java.time.Instant.from(format.parse(decoder.decodeString())).toKotlinInstant()
}

typealias SerializableInstant = @Serializable(with = InstantSerializer::class) Instant

val adminPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)

operator fun RestAction<*>.unaryPlus() = queue()

fun RoutingContext.nullableQueryParameter(parameter: String) =
  call.queryParameters[parameter]?.trim()?.takeIf { it.isNotEmpty() }

fun RoutingContext.queryParameter(parameter: String) = nullableQueryParameter(parameter)
  ?: badRequest("Invalid parameter ${parameter}.")

class HttpResponseException(val code: HttpStatusCode, val body: String, cause: Throwable? = null) :
  RuntimeException(body, cause)

fun badRequest(message: String, code: HttpStatusCode = HttpStatusCode.BadRequest): Nothing =
  throw HttpResponseException(code, message)

fun notFound(): Nothing = throw NotFoundException()

fun internalServerError(
  message: String = "An internal error occured",
  cause: Throwable? = null
): Nothing = throw HttpResponseException(HttpStatusCode.InternalServerError, message, cause)

private val sha256Digest = MessageDigest.getInstance("SHA-256")
val ByteArray.sha256 get(): ByteArray = sha256Digest.digest(this)

val Claim.asNullableString get() = if (isNull) null else asString()
fun DecodedJWT.string(claim: String) = getClaim(claim).asNullableString

@Suppress("DEPRECATION") // stop deprecating stuff for no reason Jetbrains
suspend fun <T> suspendTransaction(
  context: CoroutineContext? = null,
  db: Database? = AppConfig.database,
  transactionIsolation: Int? = null,
  readOnly: Boolean? = null,
  statement: suspend JdbcTransaction.() -> T
): T = newSuspendedTransaction(context, db, transactionIsolation, readOnly, statement)
