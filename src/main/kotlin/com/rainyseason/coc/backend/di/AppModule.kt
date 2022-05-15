package com.rainyseason.coc.backend.di

import com.google.auth.oauth2.GoogleCredentials
import com.google.cloud.firestore.Firestore
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.cloud.FirestoreClient
import com.rainyseason.coc.backend.BuildConfig
import com.rainyseason.coc.backend.FirebaseAuthProvider
import com.rainyseason.coc.backend.core.ConfigKeys
import com.rainyseason.coc.backend.core.getValue
import com.rainyseason.coc.backend.data.RawJsonAdapter
import com.rainyseason.coc.backend.data.coingecko.CoinGeckoService
import com.rainyseason.coc.backend.data.coingecko.model.ComplexMessageJsonAdapter
import com.rainyseason.coc.backend.data.telegram.TelegramService
import com.rainyseason.coc.backend.util.Env
import com.rainyseason.coc.backend.util.getLogger
import com.squareup.moshi.Moshi
import dagger.Module
import dagger.Provides
import io.vertx.core.Vertx
import io.vertx.ext.web.handler.AuthenticationHandler
import io.vertx.ext.web.handler.JWTAuthHandler
import io.vertx.json.schema.SchemaParser
import io.vertx.json.schema.SchemaRouter
import io.vertx.json.schema.SchemaRouterOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.apache.commons.configuration2.ConfigurationUtils
import org.apache.commons.configuration2.ImmutableConfiguration
import org.apache.commons.configuration2.builder.fluent.Configurations
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.io.FileInputStream
import javax.inject.Singleton

@Module
object AppModule {

    private val log = getLogger<AppModule>()

    @Provides
    @Singleton
    fun vertx(): Vertx {
        return Vertx.vertx()
    }

    @Provides
    @Singleton
    fun moshi(): Moshi {
        return Moshi.Builder()
            .add(RawJsonAdapter)
            .add(ComplexMessageJsonAdapter)
            .build()
    }

    @Provides
    @Singleton
    fun baseClient(
        buildConfig: BuildConfig,
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .apply {
                val logLevel = when (buildConfig.httpLog) {
                    "BASIC" -> HttpLoggingInterceptor.Level.BASIC
                    "HEADERS" -> HttpLoggingInterceptor.Level.HEADERS
                    "BODY" -> HttpLoggingInterceptor.Level.BODY
                    else -> null
                }
                if (logLevel != null) {
                    val logger = getLogger<HttpLoggingInterceptor>()
                    val interceptor = HttpLoggingInterceptor {
                        logger.debug(it)
                    }
                    interceptor.level = logLevel

                    addInterceptor(interceptor)
                }
            }
            .build()
    }

    @Provides
    @Singleton
    fun coingeckoService(
        moshi: Moshi,
        okHttpClient: OkHttpClient,
    ): CoinGeckoService {
        return Retrofit.Builder()
            .baseUrl(CoinGeckoService.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi).asLenient())
            .callFactory(okHttpClient)
            .build()
            .create(CoinGeckoService::class.java)
    }

    @Provides
    @Singleton
    fun telegramService(
        moshi: Moshi,
        okHttpClient: OkHttpClient,
        config: ImmutableConfiguration,
    ): TelegramService {
        val token = config.getValue(ConfigKeys.TelegramBotToken)
        return Retrofit.Builder()
            .baseUrl("https://api.telegram.org/bot$token/")
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .callFactory(okHttpClient)
            .build()
            .create(TelegramService::class.java)
    }

    @Provides
    @Singleton
    fun appScope(): CoroutineScope {
        return CoroutineScope(SupervisorJob())
    }

    @Provides
    @Singleton
    fun schemaParser(vertx: Vertx): SchemaParser {
        val schemaRouter = SchemaRouter.create(vertx, SchemaRouterOptions())
        return SchemaParser.createDraft201909SchemaParser(schemaRouter)
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

    @Provides
    @Singleton
    fun config(): ImmutableConfiguration {
        val path = Env.CONFIG_FILE_PATH
        val config = Configurations().properties(File(path))
        return ConfigurationUtils.unmodifiableConfiguration(config)
    }
}
