package com.example.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

fun Application.configureSecurity() {
    // Load configuration values from `application.conf`
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtDomain = environment.config.property("jwt.domain").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()

    val oauthConfig = environment.config.config("oauth.gmail")
    val clientId = oauthConfig.property("clientId").getString()
    val clientSecret = oauthConfig.property("clientSecret").getString()
    val authorizeUrl = oauthConfig.property("authorizeUrl").getString()
    val accessTokenUrl = oauthConfig.property("accessTokenUrl").getString()
    val callbackUrl = oauthConfig.property("callbackUrl").getString()

    authentication {
        // JWT Authentication
        jwt("jwt-auth") {
            realm = jwtRealm
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withAudience(jwtAudience)
                    .withIssuer(jwtDomain)
                    .build()
            )
            validate { credentials: JWTCredential ->
                if (credentials.payload.audience.contains(jwtAudience)) {
                    JWTPrincipal(credentials.payload)
                } else {
                    null
                }
            }
            // Correctly handle the challenge with two parameters: `defaultScheme` and `realm`
            challenge { defaultScheme: String?, realm: String ->
                call.respondText("Invalid or missing JWT token.", status = HttpStatusCode.Unauthorized)
            }
        }

        // Gmail OAuth2 Authentication
        oauth("oauth-gmail") {
            urlProvider = { callbackUrl }
            providerLookup = {
                OAuthServerSettings.OAuth2ServerSettings(
                    name = "gmail",
                    authorizeUrl = authorizeUrl,
                    accessTokenUrl = accessTokenUrl,
                    requestMethod = HttpMethod.Post,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    defaultScopes = listOf("email", "profile")
                )
            }
            client = HttpClient(Apache)
        }
    }

    // StatusPages for all exceptions
    install(StatusPages) {
        // Generic handling of any exception
        exception<Throwable> { call: ApplicationCall, cause: Throwable ->
            call.respond(
                HttpStatusCode.InternalServerError,
                "Something went wrong: ${cause.localizedMessage}"
            )
        }
    }
}