package de.clopcounter.data.remote

data class Account(val userId: String, val username: String, val userNumber: Long)

enum class FriendStatus { FRIEND, INCOMING, OUTGOING }

data class Friend(val userId: String, val username: String, val userNumber: Long, val status: FriendStatus)

data class RemoteLink(val url: String, val title: String?, val previewUrl: String?)

data class RemoteEntry(
    val uuid: String,
    val timestamp: Long,
    val epochDay: Long,
    val tags: List<String>,
    val links: List<RemoteLink>,
    val updatedAt: Long,
    val note: String = "",
)

/** Error from the server with a message that can be shown to the user. */
class SupabaseException(val status: Int, message: String) : Exception(message)

class NotSignedInException : Exception("Nicht angemeldet")
