package jpiccoli.uwml.relay;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import jpiccoli.uwml.remote.RemoteCommunicator;
import jpiccoli.uwml.remote.RemoteCommunicatorCallback;

public class RelaySession implements RemoteCommunicatorCallback {

    private int sessionIdentifier;
    private ArrayList<RemoteCommunicator> communicators;
    
    public RelaySession(int sessionIdentifier) {
        this.sessionIdentifier = sessionIdentifier;
        communicators = new ArrayList<RemoteCommunicator>();
    }
    
    public int getSessionIdentifier() {
        return sessionIdentifier;
    }
    
    public void addCommunicator(RemoteCommunicator communicator) {
        communicator.setCommunicatorCallback(this);
        communicators.add(communicator);
    }
    
    public void removeCommunicator(RemoteCommunicator communicator) {
        communicators.remove(communicator);
    }
    
    public void connectionInitialized(RemoteCommunicator source) {}

    public void connectionLost(RemoteCommunicator source) {
        communicators.remove(source);
    }

    public void connectionStatusChanged(RemoteCommunicator source) {}

    public void packetReceived(RemoteCommunicator source, ByteBuffer data) {
        for (int i = 0; i < communicators.size(); i++) {
            try {
                RemoteCommunicator communicator = communicators.get(i);
                if (communicator != source && communicator.isEnabled()) {
                    communicator.sendAppMessage(data);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
}
