package jpiccoli.uwml.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.PushSourceStream;
import javax.media.protocol.SourceTransferHandler;

public class RTPInput implements PushSourceStream {

    private ByteBuffer transferBuffer;
    private SourceTransferHandler sth;
    
    private ContentDescriptor descriptor;
    
    public RTPInput() {
        descriptor = new ContentDescriptor(ContentDescriptor.RAW_RTP);
    }
    
    public int getMinimumTransferSize() {
        return 1024;
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        int received = length;
        if (transferBuffer.remaining() < length) {
            received = transferBuffer.remaining();
        }
        transferBuffer.get(buffer, offset, received);
        return received;
    }

    public void setTransferHandler(SourceTransferHandler sth) {
        this.sth = sth;
    }

    public boolean endOfStream() {
        return false;
    }

    public ContentDescriptor getContentDescriptor() {
        return descriptor;
    }

    public long getContentLength() {
        return LENGTH_UNKNOWN;
    }

    public Object getControl(String control) {
        return null;
    }

    public Object[] getControls() {
        return new Object[0];
    }
    
    protected void transfer(ByteBuffer received) {
        transferBuffer = received;
        if (sth != null)
            sth.transferData(this);
    }
    
}
