package nl.bartoostveen.tcsbot.util

import io.ktor.http.HttpStatusCode
import io.ktor.server.plugins.NotFoundException
import io.ktor.server.routing.RoutingContext

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
