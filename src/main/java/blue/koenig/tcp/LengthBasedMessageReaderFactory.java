package blue.koenig.tcp;

public class LengthBasedMessageReaderFactory implements MessageReaderFactory {
    @Override
    public MessageReader createMessageReader() {
        return new LengthBasedMessageReader();
    }
}
