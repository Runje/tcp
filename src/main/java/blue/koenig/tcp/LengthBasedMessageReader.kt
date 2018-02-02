package blue.koenig.tcp

import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel

class LengthBasedMessageReader : MessageReader {
    private val logger = LoggerFactory.getLogger(javaClass.simpleName)
    private val lengthSize = 4
    private val lengthBuffer = ByteBuffer.allocate(lengthSize)
    private var contentBuffer: ByteBuffer? = null
    private var lengthRead: Boolean = false
    private var length: Int = 0
    private var bytesRead = 0

    @Throws(IOException::class)
    override fun read(channel: SocketChannel): ByteBuffer? {
        if (!lengthRead) {
            //logger.info("Read length");
            bytesRead += channel.read(lengthBuffer)
            if (bytesRead < 0) {
                throw IOException("Read -1 bytes")

            } else if (bytesRead < 4) {
                logger.warn("Read not all bytes from length: $bytesRead/4")
            } else {
                lengthBuffer.flip()
                length = lengthBuffer.int
                lengthRead = true
                lengthBuffer.clear()
                bytesRead = 0
            }
        } else {
            //logger.info("Read content");
            val contentLength = length - lengthSize
            if (contentBuffer == null) {
                // new read
                contentBuffer = ByteBuffer.allocate(contentLength)
            }

            bytesRead += channel.read(contentBuffer)
            if (bytesRead < 0) {
                throw IOException("Read -1 bytes")
            } else if (bytesRead < contentLength) {
                logger.warn("Read not all bytes: $bytesRead/$contentLength")
            } else {
                lengthRead = false
                //logger.info("Bytes read: " + contentLength);
                length = 0
                // copy buffer as result
                val result = ByteBuffer.wrap(contentBuffer!!.array())
                contentBuffer = null
                bytesRead = 0
                // TODO: work with direct byte buffers???? http://www.evanjones.ca/java-bytebuffer-leak.html
                return result
            }
        }

        return null
    }
}
