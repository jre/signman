package net.joshe.signman.cli

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.encodeToStream
import net.joshe.signman.client.AuthStore
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class JsonFileAuthStore(private val writer: ((OutputStream) -> Unit) -> Unit): AuthStore() {
    private var db = mutableMapOf<StoreKey,StoreValue>()

    @OptIn(ExperimentalSerializationApi::class)
    fun load(stream: InputStream) {
        db = Json.decodeFromStream(stream)
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun store() = writer { output ->
        Json.encodeToStream(db, output)
    }

    override fun hasDigester(algorithm: String) = JvmDigester.get(algorithm) != null

    override fun getDigester(algorithm: String) = JvmDigester.get(algorithm) as Digester

    override fun getHA1(digester: Digester, uuid: Uuid) = db[StoreKey(uuid, digester)]?.toPair()

    override fun putHA1(digester: Digester, uuid: Uuid, user: String, ha1: ByteArray) {
        db[StoreKey(uuid, digester)] = StoreValue(user = user, ha1 = ha1)
        store()
    }

    private class JvmDigester(private val instance: MessageDigest) : Digester {
        val algorithm: String get() = instance.algorithm
        override fun digest(input: ByteArray): ByteArray = instance.digest(input)
        companion object {
            fun get(algorithm: String) = MessageDigest.getInstance(algorithm)?.let { JvmDigester(it) }
        }
    }

    @Serializable(with = StoreKeySerializer::class)
    private data class StoreKey(val uuid: Uuid, val algorithm: String) {
        constructor(uuid: Uuid, digester: Digester) : this(uuid, (digester as JvmDigester).algorithm)
    }

    @Serializable
    private class StoreValue(val user: String, val ha1: ByteArray) {
        fun toPair() = Pair(user, ha1)
    }

    private class StoreKeySerializer : KSerializer<StoreKey> {
        override val descriptor = PrimitiveSerialDescriptor(kind = PrimitiveKind.STRING,
            serialName = this::class.qualifiedName!!)
        override fun deserialize(decoder: Decoder) = decoder.decodeString().split(':', limit=2).let { (algo, uuid) ->
            StoreKey(uuid = Uuid.parse(uuid), algorithm = algo)
        }
        override fun serialize(encoder: Encoder, value: StoreKey) {
            encoder.encodeString("${value.algorithm}:${value.uuid.toHexDashString()}")
        }
    }
}
