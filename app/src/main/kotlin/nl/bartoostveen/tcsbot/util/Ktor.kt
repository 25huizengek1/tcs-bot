package nl.bartoostveen.tcsbot.util

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.routing.RoutingContext
import java.io.File
import java.security.KeyStore
import java.security.cert.CertificateFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

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

/**
 * type should correspond to TrustManager return type e.g. X.509 => X509TrustManager
 */
inline fun <reified T : TrustManager> buildTrustManager(
  path: String,
  type: String = "X.509"
): T {
  val ks = KeyStore.getInstance(KeyStore.getDefaultType())
  ks.load(null, null)
  val certificateFactory = CertificateFactory.getInstance(type)

  File(path).inputStream().use { input ->
    val all = input.readBytes()
    val regex = "-----BEGIN CERTIFICATE-----[\\s\\S]+?-----END CERTIFICATE-----".toRegex()
    val matches = regex.findAll(all.decodeToString())

    matches.forEachIndexed { i, match ->
      runCatching {
        val certBytes = match.value.toByteArray()
        val cert = certificateFactory.generateCertificate(certBytes.inputStream())
        ks.setCertificateEntry("cert-$i", cert)
      }.exceptionOrNull()?.printStackTrace()
    }
  }

  val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
  tmf.init(ks)
  return tmf.trustManagers.filterIsInstance<T>().first()
}
