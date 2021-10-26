package jpiccoli.uwml.client;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.ArrayList;

import jpiccoli.uwml.local.LocalCommunicator;
import jpiccoli.uwml.local.LocalCommunicatorCallback;
import jpiccoli.uwml.local.LocalNetworkHost;
import jpiccoli.uwml.remote.RemoteCommunicator;
import jpiccoli.uwml.remote.RemoteCommunicatorCallback;
import jpiccoli.uwml.remote.RemoteConnection;
import jpiccoli.uwml.remote.TCPRemoteConnection;
import jpiccoli.uwml.remote.UDPRemoteConnection;
import jpiccoli.uwml.util.ConnectionDescriptor;
import jpiccoli.uwml.util.ProtocolType;

public abstract class NetworkClient implements LocalCommunicatorCallback, RemoteCommunicatorCallback, Runnable {

    private ArrayList<ClientTask> tasks;
    private Thread thread;
    
    private ConnectionDescriptor descriptor;
    
    private RemoteCommunicator remoteCommunicator;
    private RemoteConnection remoteConnection;
    
    private LocalCommunicator localCommunicator;
    
    private NewManagerTimeoutThread timeoutThread;
    
    private boolean isManager = false;
    
    public NetworkClient(ConnectionDescriptor descriptor, int sessionIdentifier) throws IOException {
        this.descriptor = descriptor;
        tasks = new ArrayList<ClientTask>();
        thread = new Thread(this);
        thread.setName("Client task dispatcher");
        thread.setDaemon(true);
        thread.start();
        remoteCommunicator = new RemoteCommunicator(this);
        remoteCommunicator.setSessionIdentifier(sessionIdentifier);
        remoteCommunicator.setEnabled(true);
        ConnectionThread ct = new ConnectionThread();
        ct.start();
        // TODO Mudar isso aqui embaixo...
        localCommunicator = new LocalCommunicator(this, sessionIdentifier);
        localCommunicator.setCanManage(true);
        LocalCommunicatorInitializerThread lct = new LocalCommunicatorInitializerThread();
        lct.setName("Local Communicator Initializer Thread");
        lct.setDaemon(true);
        lct.start();
    }
    
    public void shutdown() {
        localCommunicator.deactivate();
        remoteCommunicator.close();
    }
    
    public synchronized void becomeManager() {
        tasks.add(ClientTask.BECOME_MANAGER);
        notify();
    }

    public synchronized void lostManager() {
        tasks.add(ClientTask.LOST_MANAGER);
        notify();
    }

    public synchronized void newManager(LocalNetworkHost user) {
        tasks.add(ClientTask.NEW_MANAGER);
        notify();
    }

    public void packetReceived(InetAddress sourceAddress, ByteBuffer receivedData) {
        if (isManager) {
            try {
                remoteCommunicator.sendAppMessage(receivedData);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        processReceivedData(receivedData);
    }

    public synchronized void connectionInitialized(RemoteCommunicator source) {
        tasks.add(ClientTask.CONNECTION_INITIALIZED);
        notify();
    }
    
    public synchronized void connectionLost(RemoteCommunicator source) {
        tasks.add(ClientTask.CONNECTION_LOST);
        notify();
    }

    public void connectionStatusChanged(RemoteCommunicator source) {
        // Não será chamado
    }

    public void packetReceived(RemoteCommunicator source, ByteBuffer data) {
        if (isManager) {
            try {
                localCommunicator.send(data);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        processReceivedData(data);
    }
    
    public void sendData(ByteBuffer data) {
        try {
            if (isManager) {
                remoteCommunicator.sendAppMessage(data);
                localCommunicator.send(data);
            } else {
                if (remoteCommunicator.isOpen() && remoteCommunicator.isEnabled()) {
                    remoteCommunicator.sendAppMessage(data);
                } else {
                    localCommunicator.send(data);
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public void run() {
        ClientTask task = null;
        while(true) {
            synchronized(this) {
                while(tasks.isEmpty()) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                task = tasks.remove(0);
            }
            if (task == ClientTask.BECOME_MANAGER) {
                if (!remoteCommunicator.isOpen()) {
                    isManager = false;
                    remoteCommunicator.setEnabled(false);
                    localCommunicator.setCanManage(false);
                } else {
                    isManager = true;
                    remoteCommunicator.setEnabled(true);
                }
            } else if (task == ClientTask.LOST_MANAGER) {
                isManager = false;
                timeoutThread = new NewManagerTimeoutThread();
                timeoutThread.start();
            } else if (task == ClientTask.NEW_MANAGER) {
                isManager = false;
                if (timeoutThread != null) {
                    timeoutThread.finish();
                }
                remoteCommunicator.setEnabled(false);
            } else if (task == ClientTask.CONNECTION_INITIALIZED) {
                localCommunicator.setCanManage(true);
            } else if (task == ClientTask.CONNECTION_LOST) {
                localCommunicator.setCanManage(false);
                new ConnectionThread().start();
            }
        }
    }
    
    protected abstract void processReceivedData(ByteBuffer data);
    
    private class ConnectionThread extends Thread {
        private ConnectionThread() {
            setName("Remote Connector Thread");
            setDaemon(true);
        }
        public void run() {
            synchronized(NetworkClient.this) {
                if (remoteConnection == null || remoteCommunicator.getConnection() == remoteConnection) {
                    if (descriptor.getProtocol() == ProtocolType.TCP) {
                        remoteConnection = new TCPRemoteConnection(remoteCommunicator, descriptor.getRemoteAddress());
                    } else {
                        remoteConnection = new UDPRemoteConnection(remoteCommunicator, descriptor.getRemoteAddress());
                    }
                    remoteCommunicator.setConnection(remoteConnection);
                    remoteCommunicator.initialize();
                }
            }
        }
    }
    
    private class LocalCommunicatorInitializerThread extends Thread {
        public void run() {
            localCommunicator.initialize();
        }
    }
    
    private class NewManagerTimeoutThread extends Thread {
        private boolean stop;
        private synchronized void finish() {
            stop = true;
            notify();
        }
        public void run() {
            synchronized(this) {
                if (!stop) {
                    System.out.println("Waiting for new manager");
                    try {
                        wait(3000);
                    } catch (InterruptedException e) {}
                    if (!stop) {
                        System.out.println("Activating remote connection");
                        remoteCommunicator.setEnabled(true);
                    }
                }
            }
        }
    }
    
}
