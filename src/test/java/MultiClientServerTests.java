import blue.koenig.tcp.MultiClientServer;
import blue.koenig.tcp.TCPClientConnection;
import io.reactivex.Observable;
import io.reactivex.Observer;
import io.reactivex.functions.Consumer;
import io.reactivex.observers.TestObserver;
import io.reactivex.schedulers.Schedulers;
import org.junit.*;
import org.reactivestreams.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;


public class MultiClientServerTests {
    private static TestObserver<ByteBuffer> receiveObserver;
    private Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
    private static TestObserver<SocketChannel> acceptObserver;
    private static MultiClientServer server;
    private static int port = 23456;

    @Before
    public void before() throws IOException {
        acceptObserver = new TestObserver();
        receiveObserver = new TestObserver<ByteBuffer>();

        server = new MultiClientServer(port);
        server.startAsync();
        server.getClients().subscribe(acceptObserver);
        server.getMessages().subscribe(receiveObserver);
        Assert.assertTrue(server.isRunning());
        acceptObserver.assertSubscribed();
    }

    @After
    public void after() throws IOException {
        server.close();
    }


    @Test
    public void connectClientWithServer() throws IOException, InterruptedException {
        // create client and connect
        TCPClientConnection client = new TCPClientConnection(port, "localhost");
        client.connect();

        acceptObserver.awaitCount(1);

        acceptObserver.assertNoErrors();
        acceptObserver.assertValueCount(1);

        client.close();
    }

    @Test
    public void connectManyClients() throws IOException {
        // connect 100 000 clients at the same time, but how(only one per IP-Adress/Port allowed)
        int n = 500;

        List<TCPClientConnection> clients = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            TCPClientConnection client = new TCPClientConnection(23456, "localhost");
            clients.add(client);
            client.connect();
        }
        // create client and connect


        acceptObserver.awaitCount(n);
        acceptObserver.assertSubscribed();
        acceptObserver.assertNoErrors();
        acceptObserver.assertValueCount(n);

        for (TCPClientConnection client : clients) {
            client.close();
        }
    }

    @Test
    public void sendMessage() throws IOException {
        // create client and connect
        TCPClientConnection client = new TCPClientConnection(port, "localhost");
        TestObserver<ByteBuffer> clientMessages = new TestObserver<>();
        client.getMessages().subscribe(clientMessages);
        client.connect();
        acceptObserver.awaitCount(1);
        String testMessage = "TEST1234";
        byte[] bytes = testMessage.getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(bytes.length + 4);
        buffer.putInt(bytes.length + 4);
        buffer.put(bytes);
        buffer.flip();
        client.sendBuffer(buffer);
        buffer.flip();
        client.sendBuffer(buffer);

        receiveObserver.awaitCount(2);
        receiveObserver.assertValueCount(2);
        Assert.assertEquals(testMessage, new String(receiveObserver.values().get(0).array()));

        buffer.flip();
        server.sendBuffer(buffer, acceptObserver.values().get(0));
        clientMessages.awaitCount(1);
        clientMessages.assertValueCount(1);
        Assert.assertEquals(testMessage, new String(clientMessages.values().get(0).array()));
    }




    @Test
    public void sendManyMessagesFromServer() throws IOException, InterruptedException {
        // send a lot of big messages at the same time to try to trigger that not all bytes are read at once
        int clients = 10;
        int messages = 50;
        int messageSize = 505600;
        AtomicInteger clientCount = new AtomicInteger(-1);
        server.getClients().observeOn(Schedulers.io()).subscribe((channel -> {
            clientCount.getAndIncrement();
            byte[] bigBytes = new byte[messageSize];
            for (int i = 0; i < messageSize; i++) {
                bigBytes[i] = (byte) i;
            }

            bigBytes[0] = (byte) clientCount.get();
            ByteBuffer buffer = ByteBuffer.allocate(bigBytes.length + 4);
            buffer.putInt(bigBytes.length + 4);
            buffer.put(bigBytes);
                for (int j = 0; j < messages; j++) {
                    buffer = ByteBuffer.wrap(buffer.array());
                    server.sendBuffer(buffer, channel);
                    logger.info("Wrote message: " + j + ", " + clientCount);
                }
        }));

        List<MessageConsumer> observers = new ArrayList<>(clients);
        for (int i = 0; i < clients; i++) {
            TCPClientConnection client = new TCPClientConnection(23456, "localhost");
            MessageConsumer observer = new MessageConsumer(i);
            client.getMessages().observeOn(Schedulers.computation()).subscribe(observer);
            observers.add(observer);
            client.connect();
        }

        int c = 0;
        for (MessageConsumer observer : observers) {
            //observer.awaitCount(messages);
            logger.info("" + c++);
            //observer.assertValueCount(messages);
            int waitedMillis = 0;
            while (observer.getCount() < messages) {
                if (waitedMillis > 5000) Assert.fail();
                int millis = 10;
                waitedMillis += millis;
                Thread.sleep(millis);
            }

            for (int i = 0; i < messages; i++) {
                byte[] bytes = observer.values().get(i).array();
                Assert.assertEquals(messageSize, bytes.length);
                for (int j = 1; j < messageSize; j++) {
                    Assert.assertEquals((byte) j, bytes[j]);
                }
            }
        }


    }

    @Test
    public void sendManyMessagesFromOneClient() {
        // send a lot of big messages at the same time to try to trigger that not all bytes are read at once
    }

    @Test
    public void sendManyMessagesFromManyClients() {
        // send a lot of big messages at the same time to try to trigger that not all bytes are read at once
    }

    private static class MessageConsumer implements Consumer<ByteBuffer> {
        private Logger logger = LoggerFactory.getLogger(getClass().getSimpleName());
        private int id;

        public int getId() {
            return id;
        }

        public MessageConsumer(int id) {
            this.id = id;
        }

        List<ByteBuffer> byteBuffers = new ArrayList<>();
        @Override
        public void accept(ByteBuffer buffer) throws Exception {
            logger.info("receive buffer: " + byteBuffers.size() + ", " + id);
            byteBuffers.add(buffer);
        }

        public int getCount() {
            return byteBuffers.size();
        }

        public List<ByteBuffer> values() { return  byteBuffers;}
    }
}
