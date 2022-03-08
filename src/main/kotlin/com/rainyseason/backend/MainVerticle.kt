package com.rainyseason.backend

import io.vertx.core.AbstractVerticle
import io.vertx.core.Promise

class MainVerticle : AbstractVerticle() {

    override fun start(startPromise: Promise<Void>) {
        val port = System.getenv("PORT").orEmpty().toIntOrNull() ?: 80
        vertx
            .createHttpServer()
            .requestHandler { req ->
                req.response()
                    .putHeader("content-type", "text/plain")
                    .end(buildString {
                        appendLine("Version 3")
                        appendLine("isSSL: ${req.isSSL}")
                        appendLine("params: ${req.params().entries().toList()}")
                        appendLine("absoluteURI: ${req.absoluteURI()}")
                        appendLine("query: ${req.query()}")
                        appendLine("scheme: ${req.scheme()}")
                        appendLine("header:")
                        req.headers().forEach { k, v: String ->
                            appendLine("  ${k}: $v")
                        }
                    })
            }
            .listen(port) { http ->
                if (http.succeeded()) {
                    startPromise.complete()
                    println("HTTP server started on port $port")
                } else {
                    startPromise.fail(http.cause());
                }
            }
    }
}
