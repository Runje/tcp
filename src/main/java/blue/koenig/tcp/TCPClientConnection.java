package blue.koenig.tcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TCPClientConnection extends NIOSocketConnection {
    private Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());

    private final int port;
    private final String ipaddress;
    private SocketChannel socketChannel;
    private Selector selector;
    private boolean connected;
    private Thread thread;

    public TCPClientConnection(int port, String ipaddress)
    {
        this.port = port;
        this.ipaddress = ipaddress;
    }

    public boolean connect() throws IOException {
        boolean result = openSocket();
        if (result) {
            thread = new Thread(this::handleSelection);
            thread.start();
        }

        return result;
    }

    private void handleSelection() {
        LengthBasedMessageReader reader = new LengthBasedMessageReader();
        while (true) {
            try {
                // blocking until at least one event is ready
                selector.select();
                Iterator it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey selKey = (SelectionKey) it.next();
                    it.remove();

                    if (selKey.isReadable()) {
                       // logger.info("Reading");
                        ByteBuffer buffer = reader.read(socketChannel);
                        if (buffer != null) {
                            // new message is complete
                            messages.onNext(buffer);
                        }
                    } else if (selKey.isWritable()) {
                        ByteBuffer buffer = (ByteBuffer) selKey.attachment();
                        socketChannel.write(buffer);
                        if (!buffer.hasRemaining()) {

                        }
                    }
                }
            } catch (IOException e) {
                logger.info("Ex while selecting: " + e.toString());
                break;
            }
        }
    }

    private boolean openSocket() throws IOException {
        socketChannel = SocketChannel.open();
        selector = Selector.open();

        boolean connected = false;
        connected = socketChannel.connect(new InetSocketAddress(ipaddress, port));

        socketChannel.configureBlocking(false);
        socketChannel.register(selector, SelectionKey.OP_READ);
        //connectionPending = false;
        return connected;
    }

    public void sendBuffer(ByteBuffer buffer) throws IOException {
        sendBuffer(buffer, socketChannel);
    }


    public boolean isConnected() {
        return connected;
    }

    public void setConnected(boolean connected) {
        this.connected = connected;
    }

    public void close() {
        // TODO: just temporary
        try {
            socketChannel.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        thread.interrupt();
        connected = false;
    }
}
