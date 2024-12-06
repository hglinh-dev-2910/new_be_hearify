import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val method: String,
    val username: String? = null,
    val password: String? = null,
    val email: String? = null,
    val oauthID: String? = null
)