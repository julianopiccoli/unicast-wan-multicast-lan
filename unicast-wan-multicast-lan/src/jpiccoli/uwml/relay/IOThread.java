package jpiccoli.uwml.relay;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;

import jpiccoli.uwml.remote.RemoteCommunicator;
import jpiccoli.uwml.remote.RemoteConnection;
import jpiccoli.uwml.util.ConnectionDescriptor;
import jpiccoli.uwml.util.ProtocolType;

public class IOThread implements Runnable {
    
    private RelayEngine engine;
    private Selector selector;
    private ByteBuffer inputBuffer;
    private int inputBufferSize;
    private int outputBufferSize;
    private Hashtable<SocketAddress, UDPRelayConnection> udpConnections;
    private Hashtable<RemoteConnection, RemoteCommunicator> closedConnections;
    private boolean stop;
    
    private Thread thread;
    
    public IOThread(RelayEngine engine, ConnectionDescriptor[] descriptors, int inputBufferSize, int outputBufferSize) throws IOException {
        this.engine = engine;
        this.inputBufferSize = inputBufferSize;
        this.outputBufferSize = outputBufferSize;
        selector = Selector.open();
        inputBuffer = ByteBuffer.allocateDirect(inputBufferSize + 10);
        udpConnections = new Hashtable<SocketAddress, UDPRelayConnection>();
        closedConnections = new Hashtable<RemoteConnection, RemoteCommunicator>();
        stop = false;
        openSockets(descriptors);
    }

    public IOThread(RelayEngine engine, ConnectionDescriptor[] descriptors) throws IOException {
        this(engine, descriptors, 1024, 1024);
    }
    
    public synchronized void start() {
        if (thread == null && !stop) {
            thread = new Thread(this);
            thread.setName("Relay IOThread");
            thread.start();
        }
    }
    
    public synchronized void stop() {
        stop = true;
        try {
            selector.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public void run() {
        // TODO Substituir por uma variável boolena
        while(!stop) {
            try {
                if (selector.select() > 0) {
                    Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();
                    while(iterator.hasNext()) {
                        SelectionKey key = iterator.next();
                        RelayRemoteConnection remoteConnection = null;
                        try {
                            if (key.isReadable()) {
                                remoteConnection = null;
                                if (key.channel() instanceof DatagramChannel) {
                                    remoteConnection = handleUDPConnection(key);
                                } else {
                                    remoteConnection = handleTCPConnection(key);
                                }
                                if (remoteConnection != null) remoteConnection.receiveData();
                            } else if (key.isAcceptable()) {
                                ServerSocketChannel serverSocket = (ServerSocketChannel) key.channel();
                                SocketChannel socket = serverSocket.accept();
                                socket.configureBlocking(false);
                                socket.register(selector, SelectionKey.OP_READ, createTCPConnection(socket));
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                            if (remoteConnection != null) {
                                remoteConnection.closeCommunication();
                            }
                            key.cancel();
                        } finally {
                            iterator.remove();
                        }
                    }
                }
                notifyClosedConnections();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        stop();
    }
    
    private void notifyClosedConnections() {
        if (closedConnections.size() > 0) {
            Enumeration<RemoteConnection> enumeration = closedConnections.keys();
            while (enumeration.hasMoreElements()) {
                RemoteConnection connection = enumeration.nextElement();
                if (connection != null) {
                    RemoteCommunicator communicator = closedConnections.remove(connection);
                    if (communicator != null) {
                        communicator.connectionLost(connection);
                    }
                }
            }
        }
    }
    
    protected ByteBuffer getUDPInputBuffer() {
        return inputBuffer;
    }
    
    protected void changeUDPInputBufferSize(int bufferSize) {
        if (bufferSize > inputBuffer.capacity()) {
            inputBuffer = ByteBuffer.allocateDirect(bufferSize);
        }
    }

    private UDPRelayConnection handleUDPConnection(SelectionKey key) throws IOException {
        DatagramChannel datagramChannel = (DatagramChannel) key.channel();
        inputBuffer.clear();
        SocketAddress remoteAddress = datagramChannel.receive(inputBuffer);
        inputBuffer.flip();
        UDPRelayConnection udpConnection = null;
        if (remoteAddress != null) {
            udpConnection = udpConnections.get(remoteAddress);
            // TODO Criar a conexão...
            if (udpConnection == null) {
                RemoteCommunicator communicator = new RemoteCommunicator(engine);
                udpConnection = new UDPRelayConnection(this, communicator, datagramChannel, remoteAddress, inputBufferSize, outputBufferSize);
                communicator.setConnection(udpConnection);
                udpConnections.put(remoteAddress, (UDPRelayConnection) udpConnection);
            }
        }
        return udpConnection;
    }

    private TCPRelayConnection handleTCPConnection(SelectionKey key) {
        return (TCPRelayConnection) key.attachment();
    }

    private TCPRelayConnection createTCPConnection(SocketChannel socket) {
        RemoteCommunicator communicator = new RemoteCommunicator(engine);
        TCPRelayConnection tcpConnection = new TCPRelayConnection(this, communicator, socket, inputBufferSize, outputBufferSize);
        communicator.setConnection(tcpConnection);
        return tcpConnection;
    }
    
    protected void udpConnectionClosed(RemoteCommunicator communicator, UDPRelayConnection connection) {
        udpConnections.remove(connection.getRemoteAddress());
        closedConnections.put(connection, communicator);
        selector.wakeup();
    }
    
    protected void tcpConnectionClosed(RemoteCommunicator communicator, TCPRelayConnection connection) {
        if (connection.getChannel().keyFor(selector) != null) {
            connection.getChannel().keyFor(selector).cancel();
            closedConnections.put(connection, communicator);
            selector.wakeup();
        }
    }

    private void openSockets(ConnectionDescriptor[] descriptors) throws IOException {
        for (int i = 0; i < descriptors.length; i++) {
            if (descriptors[i].getProtocol() != null) {
                if (descriptors[i].getProtocol() == ProtocolType.TCP) {
                    if (descriptors[i].getLocalAddress() != null) {
                        ServerSocketChannel serverSocket = ServerSocketChannel.open();
                        serverSocket.configureBlocking(false);
                        serverSocket.socket().bind(descriptors[i].getLocalAddress());
                        serverSocket.register(selector, SelectionKey.OP_ACCEPT);
                    }
                } else if (descriptors[i].getProtocol() == ProtocolType.UDP) {
                    if (descriptors[i].getLocalAddress() != null) {
                        DatagramChannel datagramChannel = DatagramChannel.open();
                        datagramChannel.socket().bind(descriptors[i].getLocalAddress());
                        datagramChannel.configureBlocking(false);
                        datagramChannel.register(selector, SelectionKey.OP_READ);
                    }
                }
            }
        }
    }
    
}
