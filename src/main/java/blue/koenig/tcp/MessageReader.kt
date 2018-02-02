package blue.koenig.tcp

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

interface MessageReader {
    @Throws(IOException::class)
    fun read(channel: SocketChannel): ByteBuffer?
}
