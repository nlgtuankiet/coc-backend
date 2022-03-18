package com.rainyseason.coc.backend.di

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.rainyseason.coc.backend.FirebaseAuthProvider
import dagger.Module
import dagger.Provides
import io.vertx.core.Vertx
import io.vertx.ext.web.handler.AuthenticationHandler
import io.vertx.ext.web.handler.JWTAuthHandler
import java.io.File
import java.io.FileInputStream
import javax.inject.Singleton

@Module
object AppModule {

    @Provides
    @Singleton
    fun vertx(): Vertx {
        return Vertx.vertx()
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
