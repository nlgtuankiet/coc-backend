package com.rainyseason.coc.backend.data.ws

import com.rainyseason.coc.backend.awaitExceptionOrNull
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocketListener
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OkHttpTextWebSocketSessionTest {

    @Test
    fun `connect - onFailure`() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://google.com").build()
        val coroutineContext = Job()
        val session = OkHttpTextWebSocketSession(
            webSocketFactory = client,
            request = request,
            coroutineText = coroutineContext
        )

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {})
        val exception = RuntimeException()
        session.onFailure(webSocket, exception, null)

        runBlocking {
            assertEquals(exception, session.closeReason.awaitExceptionOrNull())
        }
    }

    @Test
    fun `connect - onOpen`() {
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://google.com")
            .build()
        val coroutineContext = Job()
        val session = OkHttpTextWebSocketSession(
            webSocketFactory = client,
            request = request,
            coroutineText = coroutineContext
        )
        val webSocket = client.newWebSocket(request, object : WebSocketListener() {})
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .code(101)
            .build()

        session.onOpen(
            webSocket,
            response,
        )

        runBlocking {
            assertEquals(response, session.originResponse.await())
        }
    }

    @Test
    fun `connect - onOpen - onFailure`() {
        // opened but failed due to incorrect extension header
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://google.com")
            .build()
        val coroutineContext = Job()
        val session = OkHttpTextWebSocketSession(
            webSocketFactory = client,
            request = request,
            coroutineText = coroutineContext
        )

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {})
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .code(101)
            .build()
        val exception = RuntimeException()

        session.onOpen(
            webSocket,
            response,
        )

        session.onFailure(webSocket, exception, response)
        runBlocking {
            // session should complete after failed
            assertEquals(response, session.originResponse.await())
            assertEquals(exception, session.closeReason.awaitExceptionOrNull())
        }
    }

    @Test
    fun `connect - onOpen - onMessage - onClosing - onClose`() {
        // normal flow - connect success - server sent close frame
        val client = OkHttpClient()
        val request = Request.Builder().url("ws://google.com")
            .build()
        val coroutineContext = Job()
        val session = OkHttpTextWebSocketSession(
            webSocketFactory = client,
            request = request,
            coroutineText = coroutineContext
        )

        val webSocket = client.newWebSocket(request, object : WebSocketListener() {})
        val response = Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .message("OK")
            .code(101)
            .build()

        session.onOpen(webSocket, response)
        session.onMessage(webSocket, "a")
        session.onMessage(webSocket, "b")
        val receivedMessage = runBlocking {
            session.incoming.consumeAsFlow().take(2).toList()
        }

        session.onClosing(webSocket, CloseReason.Codes.GOING_AWAY.code, "bye")
        session.onClosed(webSocket, CloseReason.Codes.GOING_AWAY.code, "bye byee")

        runBlocking {
            assertEquals(receivedMessage, listOf("a", "b"))
            assertEquals(
                CloseReason(CloseReason.Codes.GOING_AWAY, "bye"),
                session.closeReason.await()
            )
        }
    }
}
