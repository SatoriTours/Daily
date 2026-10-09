package com.dailysatori.service.security

import kotlinx.serialization.json.*

class DatabaseSecurityException(message: String = "数据库密钥不可用，请从备份恢复") : IllegalStateException(message)

/** Never implicitly exposes key material through logs, equality or data-class rendering. */
class DatabaseKey private constructor(private val hex: String) {
    fun sqlCipherPassword(): ByteArray = "x'$hex'".encodeToByteArray()
    internal fun wrappingValue(): ByteArray = hex.encodeToByteArray()
    fun portableJson(): String = buildJsonObject {
        put("version", 1)
        put("encoding", "raw-256-hex")
        put("keyHex", hex)
        put("cipherCompatibility", 4)
    }.toString()
    override fun toString(): String = "DatabaseKey(***)"

    companion object {
        fun fromHex(value: String): DatabaseKey {
            require(value.length == 64 && value.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }) { "无效数据库密钥" }
            return DatabaseKey(value.lowercase())
        }
        fun generate(): DatabaseKey = fromHex(secureDatabaseKeyBytes().joinToString("") { byte ->
            val n = byte.toInt() and 255
            "0123456789abcdef"[n / 16].toString() + "0123456789abcdef"[n % 16]
        })
        fun fromPortableJson(value: String): DatabaseKey {
            require(value.length <= 1024) { "无效数据库密钥描述" }
            val json = Json.parseToJsonElement(value).jsonObject
            require(json.keys == setOf("version", "encoding", "keyHex", "cipherCompatibility"))
            require(json.getValue("version").jsonPrimitive.int == 1)
            require(json.getValue("encoding").jsonPrimitive.content == "raw-256-hex")
            require(json.getValue("cipherCompatibility").jsonPrimitive.int == 4)
            return fromHex(json.getValue("keyHex").jsonPrimitive.content)
        }
    }
}

internal expect fun secureDatabaseKeyBytes(): ByteArray
