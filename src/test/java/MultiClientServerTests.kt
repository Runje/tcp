import blue.koenig.tcp.MultiClientServer
import blue.koenig.tcp.TCPClientConnection
import io.reactivex.functions.Consumer
import io.reactivex.observers.TestObserver
import io.reactivex.schedulers.Schedulers
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import java.util.*
import java.util.concurrent.atomic.AtomicInteger


class MultiClientServerTests {
    private val logger = LoggerFactory.getLogger(javaClass.simpleName)

    @Before
    @Throws(IOException::class)
    fun before() {
        acceptObserver = TestObserver()
        receiveObserver = TestObserver()

        server = MultiClientServer(port)
        server.startAsync()
        server.clients.subscribe(acceptObserver!!)
        server.messages.subscribe(receiveObserver!!)
        Assert.assertTrue(server.isRunning)
        acceptObserver!!.assertSubscribed()
    }

    @After
    @Throws(IOException::class)
    fun after() {
        server.close()
    }


    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun connectClientWithServer() {
        // create client and connect
        val client = TCPClientConnection(port, "localhost")
        client.connect()

        acceptObserver!!.awaitCount(1)

        acceptObserver!!.assertNoErrors()
        acceptObserver!!.assertValueCount(1)

        client.close()
    }

    @Test
    @Throws(IOException::class)
    fun connectManyClients() {
        // connect 100 000 clients at the same time, but how(only one per IP-Adress/Port allowed)
        val n = 500

        val clients = ArrayList<TCPClientConnection>(n)

        for (i in 0 until n) {
            val client = TCPClientConnection(23456, "localhost")
            clients.add(client)
            client.connect()
        }
        // create client and connect


        acceptObserver!!.awaitCount(n)
        acceptObserver!!.assertSubscribed()
        acceptObserver!!.assertNoErrors()
        acceptObserver!!.assertValueCount(n)

        for (client in clients) {
            client.close()
        }
    }

    @Test
    @Throws(IOException::class)
    fun sendMessage() {
        // create client and connect
        val client = TCPClientConnection(port, "localhost")
        val clientMessages = TestObserver<ByteBuffer>()
        client.messages.subscribe(clientMessages)
        client.connect()
        acceptObserver!!.awaitCount(1)
        val testMessage = "TEST1234"
        val bytes = testMessage.toByteArray()
        val buffer = ByteBuffer.allocate(bytes.size + 4)
        buffer.putInt(bytes.size + 4)
        buffer.put(bytes)
        buffer.flip()
        client.sendBuffer(buffer)
        buffer.flip()
        client.sendBuffer(buffer)

        receiveObserver!!.awaitCount(2)
        receiveObserver!!.assertValueCount(2)
        Assert.assertEquals(testMessage, String(receiveObserver!!.values()[0].array()))

        buffer.flip()
        server.sendBuffer(buffer, acceptObserver!!.values()[0])
        clientMessages.awaitCount(1)
        clientMessages.assertValueCount(1)
        Assert.assertEquals(testMessage, String(clientMessages.values()[0].array()))
    }


    @Test
    @Throws(IOException::class, InterruptedException::class)
    fun sendManyMessagesFromServer() {
        // send a lot of big messages at the same time to try to trigger that not all bytes are read at once
        val clients = 10
        val messages = 50
        val messageSize = 505600
        val clientCount = AtomicInteger(-1)
        server.clients.observeOn(Schedulers.io()).subscribe { channel ->
            clientCount.getAndIncrement()
            val bigBytes = ByteArray(messageSize)
            for (i in 0 until messageSize) {
                bigBytes[i] = i.toByte()
            }

            bigBytes[0] = clientCount.get().toByte()
            var buffer = ByteBuffer.allocate(bigBytes.size + 4)
            buffer.putInt(bigBytes.size + 4)
            buffer.put(bigBytes)
            for (j in 0 until messages) {
                buffer = ByteBuffer.wrap(buffer.array())
                server.sendBuffer(buffer, channel)
                logger.info("Wrote message: $j, $clientCount")
            }
        }

        val observers = ArrayList<MessageConsumer>(clients)
        for (i in 0 until clients) {
            val client = TCPClientConnection(23456, "localhost")
            val observer = MessageConsumer(i)
            client.messages.observeOn(Schedulers.computation()).subscribe(observer)
            observers.add(observer)
            client.connect()
        }

        var c = 0
        for (observer in observers) {
            //observer.awaitCount(messages);
            logger.info("" + c++)
            //observer.assertValueCount(messages);
            var waitedMillis = 0
            while (observer.count < messages) {
                if (waitedMillis > 5000) Assert.fail()
                val millis = 10
                waitedMillis += millis
                Thread.sleep(millis.toLong())
            }

            for (i in 0 until messages) {
                val bytes = observer.values()[i].array()
                Assert.assertEquals(messageSize.toLong(), bytes.size.toLong())
                for (j in 1 until messageSize) {
                    Assert.assertEquals(j.toByte().toLong(), bytes[j].toLong())
                }
            }
        }


    }

    @Test
    fun reconnect() {
        // create client and connect
        val client = TCPClientConnection(port, "localhost")
        client.connect()

        acceptObserver!!.awaitCount(1)

        acceptObserver!!.assertNoErrors()
        acceptObserver!!.assertValueCount(1)
        Assert.assertEquals(1, server.connectedClients)
        client.close()

        Thread.sleep(1000)
        Assert.assertEquals(0, server.connectedClients)

        client.connect()

        acceptObserver!!.awaitCount(2)

        acceptObserver!!.assertNoErrors()
        acceptObserver!!.assertValueCount(2)
        Assert.assertEquals(1, server.connectedClients)
        client.close()

        Thread.sleep(1000)
        Assert.assertEquals(0, server.connectedClients)
    }

    @Test
    fun sendManyMessagesFromOneClient() {
        // send a lot of big messages at the same time to try to trigger that not all bytes are read at once
    }

    @Test
    fun sendManyMessagesFromManyClients() {
        // send a lot of big messages at the same time to try to trigger that not all bytes are read at once
    }

    private class MessageConsumer(val id: Int) : Consumer<ByteBuffer> {
        private val logger = LoggerFactory.getLogger(javaClass.simpleName)

        internal var byteBuffers: MutableList<ByteBuffer> = ArrayList()

        val count: Int
            get() = byteBuffers.size

        @Throws(Exception::class)
        override fun accept(buffer: ByteBuffer) {
            logger.info("receive buffer: " + byteBuffers.size + ", " + id)
            byteBuffers.add(buffer)
        }

        fun values(): List<ByteBuffer> {
            return byteBuffers
        }
    }

    companion object {
        private var receiveObserver: TestObserver<ByteBuffer>? = null
        private var acceptObserver: TestObserver<SocketChannel>? = null
        private lateinit var server: MultiClientServer
        private val port = 23456
    }
}
