package jpiccoli.uwml.media;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Hashtable;

import jpiccoli.uwml.client.NetworkClient;
import jpiccoli.uwml.util.ConnectionDescriptor;

public class RTPDemux extends NetworkClient {

    private Hashtable<Byte, RTPInput> inputs;
    
    public RTPDemux(ConnectionDescriptor descriptor, int sessionIdentifier) throws IOException {
        super(descriptor, sessionIdentifier);
        inputs = new Hashtable<Byte, RTPInput>();
    }

    protected void addInput(byte key, RTPInput input) { 
        inputs.put(key, input);
    }
    
    protected void removeInput(byte key) {
        inputs.remove(key);
    }
    
    @Override
    protected void processReceivedData(ByteBuffer data) {
        if (inputs != null) {
            byte key = data.get();
            RTPInput input = inputs.get(key);
            if (input != null) input.transfer(data);
        }
    }
    
}
