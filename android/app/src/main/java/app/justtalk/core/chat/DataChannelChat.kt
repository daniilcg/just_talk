package app.justtalk.core.chat

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.webrtc.DataChannel
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

data class ChatMessage(
    val direction: Direction,
    val text: String
) {
    enum class Direction { In, Out }
}

class DataChannelChat(private val dc: DataChannel) {
    private val _messages = MutableSharedFlow<ChatMessage>(
        replay = 0,
        extraBufferCapacity = 128,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<ChatMessage> = _messages

    private val observer = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) = Unit
        override fun onStateChange() = Unit

        override fun onMessage(buffer: DataChannel.Buffer) {
            if (buffer.binary) return
            val bytes = ByteArray(buffer.data.remaining())
            buffer.data.get(bytes)
            val text = String(bytes, StandardCharsets.UTF_8)
            _messages.tryEmit(ChatMessage(ChatMessage.Direction.In, text))
        }
    }

    fun start() {
        dc.registerObserver(observer)
    }

    fun stop() {
        runCatching { dc.unregisterObserver() }
    }

    fun sendText(text: String): Boolean {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        val ok = dc.send(DataChannel.Buffer(ByteBuffer.wrap(bytes), false))
        if (ok) _messages.tryEmit(ChatMessage(ChatMessage.Direction.Out, text))
        return ok
    }
}

