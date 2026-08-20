package dev.servd.core.chat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A device/person connected to the hub. */
@Serializable
data class Peer(
    val id: String,
    val name: String,
    val address: String,
)

/**
 * WebSocket protocol between the dashboard and the hub. Sealed hierarchies serialize with a
 * `"type"` discriminator (e.g. {"type":"hello","name":"pixel-7"}).
 */
@Serializable
sealed interface ClientMessage

@Serializable
@SerialName("hello")
data class Hello(val name: String) : ClientMessage

@Serializable
@SerialName("chat")
data class ChatSend(val text: String) : ClientMessage

@Serializable
sealed interface ServerMessage

@Serializable
@SerialName("welcome")
data class Welcome(val selfId: String, val serverName: String) : ServerMessage

@Serializable
@SerialName("roster")
data class Roster(val peers: List<Peer>) : ServerMessage

@Serializable
@SerialName("chat")
data class ChatEvent(
    val fromId: String,
    val fromName: String,
    val text: String,
    val ts: Long,
) : ServerMessage

@Serializable
@SerialName("presence")
data class PresenceEvent(
    /** "join" or "leave". */
    val event: String,
    val peer: Peer,
) : ServerMessage

/** A file that has been shared to the hub. */
@Serializable
data class FileMeta(
    val id: String,
    val name: String,
    val size: Long,
    val contentType: String? = null,
    val fromName: String,
    val ts: Long,
)

/** Broadcast when someone uploads a file, so every dashboard updates live. */
@Serializable
@SerialName("file")
data class FileShared(val file: FileMeta) : ServerMessage
