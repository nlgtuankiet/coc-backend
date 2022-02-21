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
                    .end("Version 1\n")
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
