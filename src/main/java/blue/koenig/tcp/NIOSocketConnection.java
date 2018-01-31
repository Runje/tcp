package blue.koenig.tcp;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class NIOSocketConnection {
    protected Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
    protected Subject<ByteBuffer> messages;

    public NIOSocketConnection() {
        messages = PublishSubject.create();
    }

    public Observable<ByteBuffer> getMessages() {
        return messages;
    }

    public synchronized int sendBuffer(ByteBuffer buffer, SocketChannel socketChannel) throws IOException {
        if (socketChannel == null || !socketChannel.isConnected()) {
            logger.error("Cannot send message: Connected: " + socketChannel.isConnected() + ", null: " + (socketChannel == null));
            return 0;
        }

        int bytes = 0;
        while (buffer.hasRemaining()) {
            try {
                while (buffer.hasRemaining()) {
                    bytes += socketChannel.write(buffer);
                    logger.info("Wrote bytes " + bytes + "/" + buffer.limit());
                }

                // Sleep, because it could write 0 a lot of times!
                Thread.sleep(10);

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return bytes;
    }


}
