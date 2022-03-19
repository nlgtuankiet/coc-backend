package com.rainyseason.coc.backend.di

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.rainyseason.coc.backend.BuildConfig
import com.rainyseason.coc.backend.FirebaseAuthProvider
import dagger.Module
import dagger.Provides
import io.vertx.core.Vertx
import io.vertx.ext.web.handler.AuthenticationHandler
import io.vertx.ext.web.handler.JWTAuthHandler
import org.apache.logging.log4j.LogManager
import java.io.File
import java.io.FileInputStream
import javax.inject.Singleton

@Module
object AppModule {

    private val log = LogManager.getLogger(AppModule::class.java)

    @Provides
    @Singleton
    fun vertx(): Vertx {
        return Vertx.vertx()
    }

    @Provides
    @Singleton
    fun buildConfig(): BuildConfig {
        return BuildConfig(
            isDebug = System.getenv("VERTX_DEBUG") == "true"
        ).also {
            log.debug("config: $it")
        }
    }

    @Provides
    @Singleton
    fun firebaseAuthHandler(
        firebaseAuthProvider: FirebaseAuthProvider,
    ): AuthenticationHandler {
        return JWTAuthHandler.create(firebaseAuthProvider)
    }

    @Provides
    @Singleton
    fun firebaseFirestore(app: FirebaseApp): Firestore {
        return FirestoreClient.getFirestore(app)
    }

    @Provides
    @Singleton
    fun firebaseAuth(app: FirebaseApp): FirebaseAuth {
        return FirebaseAuth.getInstance(app)
    }

    @Provides
    @Singleton
    fun firebaseApp(): FirebaseApp {
        val jsonPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS")
        require(!jsonPath.isNullOrEmpty()) { "GOOGLE_APPLICATION_CREDENTIALS not specify" }
        val jsonFile = File(jsonPath)
        require(jsonFile.exists() && jsonFile.isFile) { "Invalid file" }
        val jsonFileStream = FileInputStream(jsonFile)
        val options = FirebaseOptions.builder()
            .setCredentials(GoogleCredentials.fromStream(jsonFileStream))
            .build()
        return FirebaseApp.initializeApp(options)
    }
}
