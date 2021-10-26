package jpiccoli.uwml.relay;

import java.io.IOException;

import jpiccoli.uwml.remote.RemoteCommunicator;
import jpiccoli.uwml.remote.RemoteConnection;

public abstract class RelayRemoteConnection extends RemoteConnection {

    protected IOThread ioThread;
    
    public RelayRemoteConnection(IOThread ioThread, RemoteCommunicator callback, int inputBufferSize, int outputBufferSize) {
        super(callback, inputBufferSize, outputBufferSize);
        this.ioThread = ioThread;
    }

    public RelayRemoteConnection(IOThread ioThread, RemoteCommunicator callback) {
        super(callback);
        this.ioThread = ioThread;
    }

    public IOThread getIoThread() {
        return ioThread;
    }

    public abstract void receiveData() throws IOException;
    
}
