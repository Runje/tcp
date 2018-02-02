package blue.koenig.tcp

import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

abstract class NIOSocketConnection {
    protected var logger = LoggerFactory.getLogger(javaClass.simpleName)
    val messages: Subject<ByteBuffer> = PublishSubject.create()

    @Synchronized
    @Throws(IOException::class)
    fun sendBuffer(buffer: ByteBuffer, socketChannel: SocketChannel?): Int {
        if (socketChannel == null || !socketChannel.isConnected) {
            logger.error("Cannot send message: Connected: " + socketChannel?.isConnected)
            return 0
        }

        var bytes = 0
        while (buffer.hasRemaining()) {
            try {
                while (buffer.hasRemaining()) {
                    bytes += socketChannel.write(buffer)
                    logger.info("Wrote bytes " + bytes + "/" + buffer.limit())
                }

                // Sleep, because it could write 0 a lot of times!
                Thread.sleep(10)

            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

        }

        return bytes
    }


}
