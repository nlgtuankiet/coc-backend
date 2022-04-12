package com.rainyseason.coc.backend.data.ws

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.channels.actor
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Based on
 * [io.ktor.client.engine.okhttp.OkHttpWebsocketSession]
 * But support only text message
 */
@OptIn(ExperimentalCoroutinesApi::class)
open class OkHttpTextWebSocketSession constructor(
    val webSocketFactory: WebSocket.Factory,
    val request: Request,
    val dispatcher: CoroutineDispatcher,
) : WebSocketListener() {
    internal val originResponse: CompletableDeferred<Response> = CompletableDeferred()
    internal val scope = CoroutineScope(
        dispatcher.limitedParallelism(1) + SupervisorJob()
    )
    private val selfListener = CompletableDeferred<WebSocketListener>()
    private val selfWebSocket = CompletableDeferred<WebSocket>()
    private val _incoming = Channel<String>(Channel.UNLIMITED)
    private val _closeReason = CompletableDeferred<CloseReason>()

    val incoming: ReceiveChannel<String>
        get() = _incoming

    val closeReason: Deferred<CloseReason>
        get() = _closeReason

    @OptIn(ObsoleteCoroutinesApi::class)
    open val outgoing: SendChannel<String> = scope.actor(capacity = Channel.UNLIMITED) {
        val websocket: WebSocket = webSocketFactory.newWebSocket(request, selfListener.await())
        selfWebSocket.complete(websocket)
        for (message in channel) {
            websocket.send(message)
        }
    }

    suspend fun close(closeReason: CloseReason? = null) {
        selfWebSocket.await().close(
            closeReason?.code ?: CloseReason.Codes.GOING_AWAY.code,
            closeReason?.message
        )
    }

    open fun closeSelf(code: Int, reason: String) {
        _closeReason.complete(CloseReason(code, reason))
        _incoming.close()
        outgoing.close(
            CancellationException(
                "WebSocket session closed with code " +
                    "${CloseReason.Codes.byCode(code)?.toString() ?: code}."
            )
        )
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        originResponse.complete(response)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        webSocket.close(CloseReason.Codes.CANNOT_ACCEPT.code, "Not supported")
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        _incoming.trySend(text)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        closeSelf(code, reason)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        closeSelf(code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        _closeReason.completeExceptionally(t)
        originResponse.completeExceptionally(t)
        _incoming.close(t)
        outgoing.close(t)
    }

    /**
     * Creates a new web socket and starts the session.
     */
    open fun start() {
        selfListener.complete(this)
    }
}
