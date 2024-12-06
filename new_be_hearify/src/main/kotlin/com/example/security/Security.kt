package com.example.security

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.oauth
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.request.*
import io.ktor.server.response.*
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

import io.ktor.server.http.content.*
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation

import kotlinx.serialization.json.Json
import io.ktor.serialization.kotlinx.json.*
import javax.naming.AuthenticationException

fun Application.configureSecurity() {
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtDomain = environment.config.property("jwt.domain").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()

    val oauthConfig = environment.config.config("oauth.gmail")
    val clientId = oauthConfig.property("clientId").getString()
    val authorizeUrl = oauthConfig.property("authorizeUrl").getString()
    val accessTokenUrl = oauthConfig.property("accessTokenUrl").getString()
    val callbackUrl = oauthConfig.property("callbackUrl").getString()

    //jwt authentication
    authentication {
        jwt("jwt-auth") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtDomain)
                    .build()
            )
            validate { credentials ->
                if (credentials.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credentials.payload)
                } else {
                    null
                }
            }
        }

        //gmail authentication
        oauth("oauth-gmail") {
            urlProvider = { callbackUrl }
//            providerLookup = {
//                OAuthServerSettings.OAuth2ServerSettings(
//                    name = "gmail",
//                    authorizeUrl = authorizeUrl,
//                    accessTokenUrl = accessTokenUrl,
//                    clientId = clientId,
//                    defaultScopes = listOf("email", "profile")
//                )
//            }
            client = HttpClient(Apache)
        }
    }

    //error
//    install(StatusPages) {
//        exception<AuthenticationException> { cause ->
//            call.respondUnauthorized("Authentication failed: ${cause.message}")
//        }
//        exception<Throwable> { cause ->
//            call.respondInternalServerError("Something went wrong: ${cause.localizedMessage}")
//        }
//    }

    // Cấu hình cho Content Negotiation (JSON response)
    install(ContentNegotiation) {
        json(Json { prettyPrint = true; isLenient = true })
    }
}

