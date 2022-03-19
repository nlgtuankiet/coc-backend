package com.rainyseason.coc.backend.handler

import com.google.cloud.firestore.Firestore
import com.google.cloud.firestore.SetOptions
import com.rainyseason.coc.backend.core.RoutingContextHandler
import com.rainyseason.coc.backend.util.await
import com.rainyseason.coc.backend.util.firebaseUid
import io.vertx.ext.web.RoutingContext
import io.vertx.ext.web.validation.ValidationHandler
import io.vertx.ext.web.validation.builder.Parameters
import io.vertx.json.schema.SchemaParser
import io.vertx.json.schema.common.dsl.Keywords.pattern
import io.vertx.json.schema.common.dsl.Schemas.stringSchema
import javax.inject.Inject

class BackupWatchlistHandler @Inject constructor(
    private val firestore: Firestore,
    schemaParser: SchemaParser,
) : RoutingContextHandler {
    private val versionParamName = "schema_version"
    private val versionParam = Parameters.param(
        versionParamName,
        stringSchema().with(pattern("""1""".toRegex().toPattern()))
    )

    val validationHandler: ValidationHandler = ValidationHandler.builder(schemaParser)
        .queryParameter(versionParam)
        .build()

    override suspend fun handle(context: RoutingContext) {
        val uid = context.user()?.firebaseUid
        require(!uid.isNullOrBlank()) { "Invalid firebase uid" }

        val version = context.queryParam(versionParamName).firstOrNull()
        require(!version.isNullOrBlank()) { "Invalid version" }

        val body = requireNotNull(context.body) { "Missing body" }.toString()
        firestore.collection("backup_watchlist").document(uid)
            .set(
                mapOf(
                    version to body
                ),
                SetOptions.merge()
            )
            .await()
        context.response().end()
    }
}
