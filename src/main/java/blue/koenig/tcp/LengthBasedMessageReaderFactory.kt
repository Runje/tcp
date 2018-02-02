package blue.koenig.tcp

class LengthBasedMessageReaderFactory : MessageReaderFactory {
    override fun createMessageReader(): MessageReader {
        return LengthBasedMessageReader()
    }
}
