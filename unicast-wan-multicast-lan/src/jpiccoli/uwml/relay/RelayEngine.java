package jpiccoli.uwml.relay;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import jpiccoli.uwml.remote.RemoteCommunicator;
import jpiccoli.uwml.remote.RemoteCommunicatorCallback;
import jpiccoli.uwml.util.ConnectionDescriptor;
import jpiccoli.uwml.util.ProtocolType;

public class RelayEngine implements RemoteCommunicatorCallback {

    private ArrayList<IOThread> ioThreads;
    private ArrayList<RelaySession> sessions;
    
    public RelayEngine() {
        ioThreads = new ArrayList<IOThread>();
        sessions = new ArrayList<RelaySession>();
    }
    
    private RelaySession createRelaySession(int sessionIdentifier) {
        RelaySession session = new RelaySession(sessionIdentifier);
        sessions.add(session);
        ioThreads = new ArrayList<IOThread>();
        return session;
    }

    public IOThread newIOThread(ConnectionDescriptor descriptors[]) {
        return newIOThread(descriptors, 1024, 1024);
    }
    
    public IOThread newIOThread(ConnectionDescriptor descriptors[], int inputBufferSize, int outputBufferSize) {
        try {
            IOThread ioThread = new IOThread(this, descriptors, inputBufferSize, outputBufferSize);
            ioThreads.add(ioThread);
            ioThread.start();
            return ioThread;
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
    
    public void shutdownIOThread(IOThread ioThread) {
        ioThreads.remove(ioThread);
        ioThread.stop();
    }
    
    private RelaySession getRelaySession(int sessionIdentifier) {
        for (int i = 0; i < sessions.size(); i++) {
            RelaySession session = sessions.get(i);
            if (session.getSessionIdentifier() == sessionIdentifier) {
                return session;
            }
        }
        return createRelaySession(sessionIdentifier);
    }
    
    public void connectionInitialized(RemoteCommunicator source) {
        if (source.getSessionIdentifier() > 0) {
            RelaySession session = getRelaySession(source.getSessionIdentifier());
            if (session != null) {
                session.addCommunicator(source);
            }
        }
    }

    public void connectionLost(RemoteCommunicator source) {
        source.close();
    }

    public void connectionStatusChanged(RemoteCommunicator source) {
        source.close();
    }

    public void packetReceived(RemoteCommunicator source, ByteBuffer data) {
        source.close();
    }
    
    public static void main(String args[]) throws UnknownHostException {
        ConnectionDescriptor[] descriptors = new ConnectionDescriptor[2];
        descriptors[0] = new ConnectionDescriptor(ProtocolType.UDP, null, new InetSocketAddress((InetAddress) null, 3333));
        descriptors[1] = new ConnectionDescriptor(ProtocolType.TCP, null, new InetSocketAddress((InetAddress) null, 3333));
        RelayEngine engine = new RelayEngine();
        engine.newIOThread(descriptors);
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
}
