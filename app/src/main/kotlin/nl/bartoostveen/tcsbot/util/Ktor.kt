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
private inline fun <reified T : TrustManager> buildTrustManager(
  path: String,
  type: String
): T {
  val keyStore = KeyStore.getInstance(KeyStore.getDefaultType())
  keyStore.load(null, null)
  val certificateFactory = CertificateFactory.getInstance(type)

  File(path).inputStream().use { input ->
    "-----BEGIN CERTIFICATE-----[\\s\\S]+?-----END CERTIFICATE-----".toRegex()
      .findAll(input.readBytes().decodeToString())
      .forEachIndexed { i, match ->
        runCatching {
          val cert = certificateFactory.generateCertificate(
            match
              .value
              .toByteArray()
              .inputStream()
          )
          keyStore.setCertificateEntry("cert-$i", cert)
        }.exceptionOrNull()?.printStackTrace()
      }
  }

  return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
    .apply {
      init(keyStore)
    }.trustManagers
    .filterIsInstance<T>()
    .first()
}

fun buildTrustManager(path: String) = buildTrustManager<X509TrustManager>(path, "X.509")
