package blue.koenig.tcp

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.channels.*
import java.util.*

class MultiClientServer(val port: Int) : NIOSocketConnection() {
    private var messageReaderFactory: MessageReaderFactory = LengthBasedMessageReaderFactory()
    private val messageReaderMap: MutableMap<SocketChannel, MessageReader> = HashMap()
    val clients: Subject<SocketChannel> = PublishSubject.create()
    var isRunning: Boolean = false
        private set
    private var selector: Selector? = null
    private var socketChannel: ServerSocketChannel? = null
    private var receiveThread: Thread? = null
    val connectedClients
        get() = messageReaderMap.size


    fun setMessageReaderFactory(messageReaderFactory: MessageReaderFactory) {
        this.messageReaderFactory = messageReaderFactory
    }

    @Throws(IOException::class)
    fun start() {
        openSocket()
        isRunning = true
        handleSelector()
    }

    @Throws(IOException::class)
    private fun openSocket() {
        selector = Selector.open()
        socketChannel = ServerSocketChannel.open()
        socketChannel!!.configureBlocking(false)
        socketChannel!!.bind(InetSocketAddress(port), 0)
        socketChannel!!.register(selector, SelectionKey.OP_ACCEPT)
    }

    @Throws(IOException::class)
    fun startAsync() {
        openSocket()
        isRunning = true
        receiveThread = Thread(Runnable { this.handleSelector() })
        receiveThread!!.start()
    }

    private fun handleSelector() {
        while (isRunning) {
            try {
                // blocking until at least one event is ready
                val selected = selector!!.select()
                //logger.info("Selected: " + selected);
                val it = selector!!.selectedKeys().iterator()
                while (it.hasNext()) {
                    val selKey = it.next() as SelectionKey
                    it.remove()

                    if (selKey.isAcceptable) {
                        val ssChannel = selKey.channel() as ServerSocketChannel
                        val sc = ssChannel.accept()
                        sc.configureBlocking(false)
                        messageReaderMap[sc] = messageReaderFactory.createMessageReader()
                        if (messageReaderMap.size > 1000) {
                            logger.warn("New client not accepted because there are already 5000 clients")
                        } else {
                            logger.info("Accepted Client: " + messageReaderMap.size)
                            clients.onNext(sc)
                            sc.register(selector, SelectionKey.OP_READ)
                        }
                    }

                    if (selKey.isReadable) {
                        logger.info("Reading")
                        val channel = selKey.channel() as SocketChannel
                        val messageReader = messageReaderMap[channel]
                        if (messageReader == null) messageReaderMap[channel] = messageReaderFactory.createMessageReader()
                        try {
                            val buffer = messageReader!!.read(channel)
                            if (buffer != null) {
                                // new message is complete
                                messages.onNext(buffer)
                            }
                        } catch (e: IOException) {
                            logger.info("Ex while reading: " + e.toString())

                            channel.close()
                            messageReaderMap.remove(channel)
                            logger.info("Dismissed client, clients: ${messageReaderMap.size}")
                        }

                    }
                }
            } catch (cSE: ClosedSelectorException) {
                // closed
                isRunning = false
            } catch (e: IOException) {
                logger.trace("Ex while selecting: " + e.toString())
                // close client
            }

        }
    }

    fun close() {
        isRunning = false
        try {
            selector!!.close()
            logger.info("Closed Selector")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        try {
            socketChannel!!.close()
            logger.info("Closed SocketChannel")
        } catch (e: IOException) {
            e.printStackTrace()
        }

        try {
            receiveThread!!.join()
            logger.info("Receive Thread joined")
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

    }

    fun getClients(): Observable<SocketChannel> {
        return clients
    }
}
