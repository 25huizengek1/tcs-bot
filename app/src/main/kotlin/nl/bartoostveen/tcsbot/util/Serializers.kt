@file:OptIn(ExperimentalTime::class)

package nl.bartoostveen.tcsbot.util

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.format.DateTimeFormatter
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

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
