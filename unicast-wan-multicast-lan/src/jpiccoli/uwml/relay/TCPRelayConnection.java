package jpiccoli.uwml.relay;

import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

import jpiccoli.uwml.remote.RemoteCommunicator;

public class TCPRelayConnection extends RelayRemoteConnection {
    
    private SocketChannel socket;
    private ByteBuffer headerBuffer;
    
    private ByteBuffer inputBuffer;
    
    private boolean readingSize;

    public TCPRelayConnection(IOThread ioThread, RemoteCommunicator callback, SocketChannel socket) {
        this(ioThread, callback, socket, 1024, 1024);
    }
    
    public TCPRelayConnection(IOThread ioThread, RemoteCommunicator callback, SocketChannel socket, int inputBufferSize, int outputBufferSize) {
        super(ioThread, callback, inputBufferSize, outputBufferSize);
        this.socket = socket;
        headerBuffer = ByteBuffer.allocateDirect(6);
        inputBuffer = ByteBuffer.allocateDirect(inputBufferSize);
        inputBuffer.clear().limit(4);
        heartBeat.addConnection(this);
        readingSize = true;
    }
    
    public SocketChannel getChannel() {
        return socket;
    }
    
    @Override
    public void openCommunication() {
        // Não implementar
    }
    
    @Override
    public void closeCommunication() {
        if (socket.isOpen()) {
            try {
                socket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } finally {
                heartBeat.removeConnection(this);
                ioThread.tcpConnectionClosed(callback, this);
            }
        }
    }

    @Override
    public synchronized void sendCtrlMessage(ByteBuffer data) throws IOException {
        headerBuffer.clear();
        int msgSize = data.remaining();
        headerBuffer.putInt(msgSize);
        headerBuffer.putShort(CTRL_MSG);
        headerBuffer.flip();
        socket.write(headerBuffer);
        if (socket.write(data) < msgSize) throw new IOException("Could not send Control Message");
    }

    @Override
    public synchronized void sendMessage(ByteBuffer data) throws IOException {
        headerBuffer.clear();
        headerBuffer.putInt(data.remaining());
        headerBuffer.putShort(NORMAL_MSG);
        headerBuffer.flip();
        socket.write(headerBuffer);
        socket.write(data);
    }

    @Override
    public synchronized void sendHeartBeat() throws IOException {
        headerBuffer.clear();
        headerBuffer.putInt(2);
        headerBuffer.putShort(HEARTBEAT_MSG);
        headerBuffer.flip();
        socket.write(headerBuffer);
    }
    
    @Override
    public void receiveData() throws IOException {
        setActive(true);
        if (socket.read(inputBuffer) < 0) throw new EOFException("Socket closed");
        if (!inputBuffer.hasRemaining()) {
            inputBuffer.flip();
            if (readingSize) {
                readingSize = false;
                int size = inputBuffer.getInt();
                if (size > inputBuffer.capacity()) throw new IOException("Packet size too large");
                inputBuffer.clear().limit(size);
            } else {
                // TODO Processar o pacote recebido
                if (inputBuffer.remaining() >= 2) {
                    short msgType = inputBuffer.getShort();
                    if (msgType == NORMAL_MSG) {
                        callback.packetReceived(this, inputBuffer);                        
                    } else if (msgType == CTRL_MSG) {
                        callback.ctrlPacketReceived(this, inputBuffer);
                    } else if (msgType == HEARTBEAT_MSG) {
                        // Ignore...
                    } else {
                        throw new IOException("Unknown message type: " + msgType);
                    }
                }
                inputBuffer.clear().limit(4);
                readingSize = true;
            }
        }
    }
    
}
