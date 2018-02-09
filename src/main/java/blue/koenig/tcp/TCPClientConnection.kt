package blue.koenig.tcp

import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ClosedSelectorException
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel

class TCPClientConnection(private val port: Int, private val ipaddress: String) : NIOSocketConnection() {
    private var socketChannel: SocketChannel? = null
    private var selector: Selector? = null
    var isConnected: Boolean = false
        private set(value) {
            field = value
            connectedObservable.onNext(isConnected)
        }
    private val connectedObservable: Subject<Boolean> = BehaviorSubject.createDefault(false)
    private var thread: Thread? = null

    @Throws(IOException::class)
    fun connect(): Boolean {
        if (isConnected) {
            logger.info("Already connected")
            return true
        }

        val result = openSocket()
        if (result) {
            thread = Thread(Runnable { this.handleSelection() })
            thread!!.start()
        }

        return result
    }

    private fun handleSelection() {
        val reader = LengthBasedMessageReader()
        while (true) {
            try {
                // blocking until at least one event is ready
                selector!!.select()
                val it = selector!!.selectedKeys().iterator()
                while (it.hasNext()) {
                    val selKey = it.next() as SelectionKey
                    it.remove()

                    if (selKey.isReadable) {
                        // logger.info("Reading");
                        val buffer = reader.read(socketChannel!!)
                        if (buffer != null) {
                            // new message is complete
                            messages.onNext(buffer)
                        }
                    } else if (selKey.isWritable) {
                        val buffer = selKey.attachment() as ByteBuffer
                        socketChannel!!.write(buffer)
                    }
                }
            } catch (e: IOException) {
                logger.info("Ex while selecting: " + e.toString())
                close()
                break
            } catch (e: ClosedSelectorException) {
                logger.info("Closed selector")
                break
            }

        }
    }

    @Throws(IOException::class)
    private fun openSocket(): Boolean {
        socketChannel = SocketChannel.open()
        selector = Selector.open()
        try {
            val connected = socketChannel?.connect(InetSocketAddress(ipaddress, port)) ?: false
            socketChannel?.configureBlocking(false)
            socketChannel?.register(selector, SelectionKey.OP_READ)
            return connected
        } catch (exception: IOException) {
            // close socket and selector and rethrow exception
            socketChannel?.close()
            selector?.close()
            throw exception
        }
    }

    @Throws(IOException::class)
    fun sendBuffer(buffer: ByteBuffer) {
        sendBuffer(buffer, socketChannel)
    }

    fun close() {
        try {
            selector?.close()
            socketChannel?.close()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        try {
            thread?.join(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        thread?.interrupt()
        isConnected = false
    }
}
