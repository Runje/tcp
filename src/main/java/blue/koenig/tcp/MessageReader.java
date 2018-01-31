package blue.koenig.tcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public interface MessageReader {
    ByteBuffer read(SocketChannel channel) throws IOException;
}
