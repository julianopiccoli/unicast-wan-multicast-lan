package jpiccoli.uwml.media;

import java.nio.ByteBuffer;
import javax.media.rtp.OutputDataStream;

import jpiccoli.uwml.client.NetworkClient;

public class RTPOutput implements OutputDataStream {

    private ByteBuffer outputBuffer;
    private NetworkClient client;
    
    protected RTPOutput(NetworkClient client, byte key) {
        this(client, key, 1024);
    }
    
    protected RTPOutput(NetworkClient client, byte key, int bufferSize) {
        this.client = client;
        outputBuffer = ByteBuffer.allocateDirect(bufferSize);
        outputBuffer.put(key);
        
    }
    
    public int write(byte[] buffer, int offset, int length) {
        if (outputBuffer.capacity() >= length) {
            outputBuffer.clear().position(1);
            outputBuffer.put(buffer, offset, length);
            outputBuffer.flip();
            client.sendData(outputBuffer);
            return length;
        }
        return 0;
    }
    
}
