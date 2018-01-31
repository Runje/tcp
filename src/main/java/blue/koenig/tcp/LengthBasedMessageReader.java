package blue.koenig.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class LengthBasedMessageReader implements MessageReader{
    private Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
    private final int lengthSize = 4;
    private final ByteBuffer lengthBuffer = ByteBuffer.allocate(lengthSize);
    private ByteBuffer contentBuffer;
    private boolean lengthRead;
    private int length;
    private int bytesRead = 0;

    public ByteBuffer read(SocketChannel channel) throws IOException {
        if (!lengthRead)
        {
            //logger.info("Read length");
            bytesRead += channel.read(lengthBuffer);
            if (bytesRead < 0)
            {
                throw new IOException("Read -1 bytes");

            } else if (bytesRead < 4) {
                logger.warn("Read not all bytes from length: " + bytesRead + "/" + 4);
            } else {
                lengthBuffer.flip();
                length = lengthBuffer.getInt();
                lengthRead = true;
                lengthBuffer.clear();
                bytesRead = 0;
            }
        } else
        {
            //logger.info("Read content");
            int contentLength = length - lengthSize;
            if (contentBuffer == null) {
                // new read
                contentBuffer = ByteBuffer.allocate(contentLength);
            }

            bytesRead += channel.read(contentBuffer);
            if (bytesRead < 0)
            {
                throw new IOException("Read -1 bytes");
            } else if (bytesRead < contentLength) {
                logger.warn("Read not all bytes: " + bytesRead + "/" + contentLength);
            } else {
                lengthRead = false;
                //logger.info("Bytes read: " + contentLength);
                length = 0;
                // TODO: endofstreamreached
                // copy buffer as result
                ByteBuffer result = ByteBuffer.wrap(contentBuffer.array());
                contentBuffer = null;
                bytesRead = 0;
                // TODO: work with direct byte buffers???? http://www.evanjones.ca/java-bytebuffer-leak.html
                return result;
            }
        }

        return null;
    }
}
