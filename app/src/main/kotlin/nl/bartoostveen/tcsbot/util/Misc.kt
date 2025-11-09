package nl.bartoostveen.tcsbot.util

fun String.splitAtIndex(index: Int) = require(index in 0..length).let {
  take(index) to substring(index + 1)
}

fun <T> Result<T>.printException() = also { it.exceptionOrNull()?.printStackTrace() }
