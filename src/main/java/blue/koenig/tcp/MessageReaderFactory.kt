package blue.koenig.tcp

interface MessageReaderFactory {
    fun createMessageReader(): MessageReader
}
