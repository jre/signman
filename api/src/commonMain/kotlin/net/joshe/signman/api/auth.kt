package net.joshe.signman.api

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@ExperimentalUuidApi
fun Uuid.toHttpAuthenticationRealm() = toHexDashString().lowercase()

@ExperimentalUuidApi
fun getDigestHA1Input(user: String, pass: String, uuid: Uuid): ByteArray
        = "$user:${uuid.toHttpAuthenticationRealm()}:$pass".toByteArray(Charsets.UTF_8)
