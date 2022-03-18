package com.rainyseason.coc.backend

import com.google.firebase.auth.FirebaseAuth
import com.rainyseason.coc.backend.util.asVertxFuture
import com.rainyseason.coc.backend.util.firebaseUid
import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Handler
import io.vertx.core.json.JsonObject
import io.vertx.ext.auth.JWTOptions
import io.vertx.ext.auth.User
import io.vertx.ext.auth.jwt.JWTAuth
import javax.inject.Inject

class FirebaseAuthProvider @Inject constructor(
    private val firebaseAuth: FirebaseAuth,
) : JWTAuth {
    override fun authenticate(
        credentials: JsonObject,
        resultHandler: Handler<AsyncResult<User>>,
    ) {
        println("FirebaseAuthProvider authenticate $credentials")
        try {
            val token = credentials.getString("token")
            require(!token.isNullOrEmpty()) { "Invalid token" }
            firebaseAuth.verifyIdTokenAsync(token).asVertxFuture()
                .map { firebaseToken ->
                    val user = User.fromToken(token)
                    println("FirebaseAuthProvider user id: ${firebaseToken.uid}")
                    user.firebaseUid = firebaseToken.uid
                    require(!user.firebaseUid.isNullOrBlank()) { "Invalid uid" }
                    user
                }
                .onComplete { result ->
                    resultHandler.handle(result)
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
