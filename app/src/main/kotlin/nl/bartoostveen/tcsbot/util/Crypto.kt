package nl.bartoostveen.tcsbot.util

import com.auth0.jwt.interfaces.Claim
import com.auth0.jwt.interfaces.DecodedJWT
import java.security.MessageDigest

private val sha256Digest = MessageDigest.getInstance("SHA-256")
val ByteArray.sha256 get(): ByteArray = sha256Digest.digest(this)

val Claim.asNullableString get() = if (isNull) null else asString()
fun DecodedJWT.string(claim: String) = getClaim(claim).asNullableString
