package com.rainyseason.coc.backend

import com.google.firebase.auth.FirebaseAuth
import com.rainyseason.coc.backend.util.asVertxFuture
import com.rainyseason.coc.backend.util.firebaseUid
import com.rainyseason.coc.backend.util.getLogger
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.Vertx
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.User
import io.vertx.ext.auth.jwt.JWTAuth
import javax.inject.Inject

class FirebaseAuthProvider @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
    private val config: BuildConfig,
) : JWTAuth {
    private val log = getLogger<FirebaseAuthProvider>()
    override fun authenticate(
        credentials: JsonObject,
        resultHandler: Handler<AsyncResult<User>>,
    ) {
        val context = Vertx.currentContext()
        try {
            log.debug("authenticate")
            val token = credentials.getString("token")
            require(!token.isNullOrEmpty()) { "Invalid token" }
            val userFuture = if (config.isDebug && token == "valid_token") {
                Future.succeededFuture(
                    User.fromToken("valid_token").apply {
                        firebaseUid = "000_test_uid"
                    }
                )
            } else {
                firebaseAuth.verifyIdTokenAsync(token).asVertxFuture()
                    .map { firebaseToken ->
                        val user = User.fromToken(token)
                        user.firebaseUid = firebaseToken.uid
                        require(!user.firebaseUid.isNullOrBlank()) { "Invalid uid" }
                        user
                    }
            }

            userFuture.onComplete { result ->
                context.runOnContext {
                    log.debug("authenticate id: ${result.result()?.firebaseUid}")
                    resultHandler.handle(result)
                }
            }
        } catch (ex: RuntimeException) {
            resultHandler.handle(Future.failedFuture(ex))
        }
    }

    override fun generateToken(claims: JsonObject?, options: JWTOptions?): String {
        error("Not support")
    }

    override fun generateToken(claims: JsonObject?): String {
        error("Not support")
    }
}
