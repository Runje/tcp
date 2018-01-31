package blue.koenig.tcp;

import io.reactivex.Observable;
import io.reactivex.subjects.PublishSubject;
import io.reactivex.subjects.Subject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;

public class MultiClientServer extends NIOSocketConnection{
    private MessageReaderFactory messageReaderFactory;
    private final Map<SocketChannel, MessageReader> messageReaderMap;
    private Subject<SocketChannel> clients;
    private final int port;
    private boolean running;
    private Selector selector;
    private ServerSocketChannel socketChannel;
    private Thread receiveThread;

    public MultiClientServer(int port) {
        messageReaderMap = new HashMap<SocketChannel, MessageReader>();
        this.port = port;
        clients = PublishSubject.create();
        messageReaderFactory = new LengthBasedMessageReaderFactory();
    }


    public void setMessageReaderFactory(MessageReaderFactory messageReaderFactory) {
        this.messageReaderFactory = messageReaderFactory;
    }

    public void start() throws IOException {
        openSocket();
        running = true;
        handleSelector();
    }

    private void openSocket() throws IOException {
        selector = Selector.open();
        socketChannel = ServerSocketChannel.open();
        socketChannel.configureBlocking(false);
        socketChannel.bind(new InetSocketAddress(port), 0);
        socketChannel.register(selector, SelectionKey.OP_ACCEPT);
    }

    public void startAsync() throws IOException {
        openSocket();
        running = true;
        receiveThread = new Thread(this::handleSelector);
        receiveThread.start();
    }

    private void handleSelector() {
        while (running) {
            try {
                // blocking until at least one event is ready
                int selected = selector.select();
                //logger.info("Selected: " + selected);
                Iterator it = selector.selectedKeys().iterator();
                while (it.hasNext()) {
                    SelectionKey selKey = (SelectionKey) it.next();
                    it.remove();

                    if (selKey.isAcceptable()) {
                        ServerSocketChannel ssChannel = (ServerSocketChannel) selKey.channel();
                        SocketChannel sc = ssChannel.accept();
                        sc.configureBlocking(false);
                        messageReaderMap.put(sc, messageReaderFactory.createMessageReader());
                        if (messageReaderMap.size() > 1000) {
                            logger.warn("New client not accepted because there are already 5000 clients");
                        } else {
                            logger.info("Accepted Client: " + messageReaderMap.size());
                            clients.onNext(sc);
                            sc.register(selector, SelectionKey.OP_READ);
                        }
                    }

                    if (selKey.isReadable()) {
                        logger.info("Reading");
                        SocketChannel channel = (SocketChannel) selKey.channel();
                        MessageReader messageReader = messageReaderMap.get(channel);
                        if (messageReader == null) messageReaderMap.put(channel, messageReaderFactory.createMessageReader());
                        try {
                            ByteBuffer buffer = messageReader.read(channel);
                            if (buffer != null) {
                                // new message is complete
                                messages.onNext(buffer);
                            }
                        } catch (IOException e) {
                            logger.info("Ex while reading: " + e.toString());
                            logger.info("Dismiss client");
                            channel.close();
                            //dismissClient(channel);
                        }
                    }
                }
            } catch (ClosedSelectorException cSE) {
                // closed
                running = false;
            } catch (IOException e) {
                logger.trace("Ex while selecting: " + e.toString());
                // close client
            }
        }
    }


    public boolean isRunning() {
        return running;
    }

    public void close() {
        running = false;
        try {
            selector.close();
            logger.info("Closed Selector");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            socketChannel.close();
            logger.info("Closed SocketChannel");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            receiveThread.join();
            logger.info("Receive Thread joined");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public int getPort() {
        return port;
    }

    public Observable<SocketChannel> getClients() {
        return clients;
    }
}
