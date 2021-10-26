package jpiccoli.uwml.remote;

import java.io.EOFException;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class TCPRemoteConnection extends RemoteConnection implements Runnable {
    
	/**
	 * Socket para comunicação com o host remoto
	 */
    private SocketChannel socket;
    
    /**
     * Endereço do host remoto
     */
    private SocketAddress remoteAddress;

    /**
     * Buffer que armazena os cabeçalhos das mensagens transmitidas
     */
    private ByteBuffer headerBuffer;
    
    /**
     * Buffer de entrada de dados
     */
    private ByteBuffer inputBuffer;
    
    private boolean stop;
    
    private Thread thread;

    /**
     * Construtor secundário. Define o tamanho do buffer de entrada em 1024 bytes.
     * @param callback Callback que receberá os eventos gerados por esta RemoteConnection.
     * @param remoteAddress Endereço do host remoto com o qual a comunicação será estabelecida.
     */
    public TCPRemoteConnection(RemoteCommunicator callback, SocketAddress remoteAddress) {
        this(callback, remoteAddress, 1024);
    }

    /**
     * Construtor principal.
     * @param callback Callback que receberá os eventos gerados por esta RemoteConnection.
     * @param remoteAddress Endereço do host remoto com o qual a comunicação será estabelecida.
     * @param inputBufferSize Tamanho do buffer de entrada utilizado
     */
    public TCPRemoteConnection(RemoteCommunicator callback, SocketAddress remoteAddress, int inputBufferSize) {
        super(callback, inputBufferSize, 0);
        this.remoteAddress = remoteAddress;
        headerBuffer = ByteBuffer.allocateDirect(6);
        inputBuffer = ByteBuffer.allocateDirect(inputBufferSize + 6);
    }
    
    @Override
    public synchronized void closeCommunication() {
        if (!stop) {
            try {
                heartBeat.removeConnection(this);
                stop = true;
                socket.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public void openCommunication() throws IOException {
        if (!stop && thread == null) {
            socket = SocketChannel.open();
            socket.connect(remoteAddress);
            thread = new Thread(this);
            thread.setDaemon(true);
            thread.start();
            heartBeat.addConnection(this);
        }
    }

    @Override
    public void sendCtrlMessage(ByteBuffer data) throws IOException {
        sendMessage(CTRL_MSG, data);
    }

    @Override
    public void sendMessage(ByteBuffer data) throws IOException {
        sendMessage(NORMAL_MSG, data);
    }

    @Override
    public synchronized void sendHeartBeat() throws IOException {
        headerBuffer.clear();
        headerBuffer.putInt(2);
        headerBuffer.putShort(HEARTBEAT_MSG);
        headerBuffer.flip();
        socket.write(headerBuffer);
    }

    /**
     * Realiza efetivamente o envio da mensagem especificada. Ambos os métodos sendCtrlMessage e sendMessage
     * redirecionam suas chamadas para este método, uma vez que todas as mensagens transmitidas através de uma
     * conexão TCP têm entrega garantida.
     * @param type Tipo da mensagem
     * @param data Conteúdo da mensagem
     * @throws IOException Caso ocorram erros de E/S
     */
    private synchronized void sendMessage(short type, ByteBuffer data) throws IOException {
        headerBuffer.clear();
        headerBuffer.putInt(data.remaining() + 2);
        headerBuffer.putShort(type);
        headerBuffer.flip();
        socket.write(headerBuffer);
        socket.write(data);
    }
    
    /**
     * Recebe as mensagens recebidas do socket TCP
     * @throws EOFException Caso o socket seja fechado pelo host remoto
     * @throws IOException Caso ocorram erros de E/S
     */
    private void readFromSocket() throws EOFException, IOException {
        while(inputBuffer.hasRemaining()) {
            if (socket.read(inputBuffer) < 0) throw new EOFException("Socket closed");
            setActive(true);
        }
    }
    
    public void run() {
        try {
            while(!stop) {
                inputBuffer.clear().limit(4);
                readFromSocket();
                inputBuffer.flip();
                int size = inputBuffer.getInt();
                if (size > inputBuffer.capacity()) throw new IOException("Packet too large");
                inputBuffer.clear().limit(size);
                readFromSocket();
                inputBuffer.flip();
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
            }
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            closeCommunication();
            callback.connectionLost(this);
        }
    }
    
}
