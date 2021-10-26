package jpiccoli.uwml.local;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;

public class MulticastInterface implements Runnable {
    
    public static final short JOIN_REQUEST_MSG_TYPE = 1;
    public static final short JOIN_RESPONSE_MSG_TYPE = 2;
    public static final short DROP_MSG_TYPE = 4;
    public static final short HEARTBEAT_MSG_TYPE = 8;
    public static final short GENERAL_MSG_TYPE = 32;
    
    /**
     * Socket multicast utilizado para receber e transmitir pacotes ao subgrupo
     */
    private MulticastSocket mSocket;
    
    /**
     * Endereço multicast utilizado na comunicação com o subgrupo
     */
    private InetAddress address;
    
    /**
     * Porta utilizada na comunicação com o subgrupo
     */
    private int port;
    
    /**
     * Código identificador do grupo
     */
    private int sessionIdentifier;

    /**
     * Buffers de saída e entrada
     */
    private ByteBuffer receiveBuffer;
    private ByteBuffer outputBuffer;
    
    /**
     * Callback que receberá as notificações dos eventos ocorridos nesta MulticastInterface
     */
    private MulticastInterfaceCallback callback;
    
    /**
     * Flag que define se este MulticastInterface foi encerrado
     */
    private boolean stop;
    
    /**
     * Construtor padrão.
     * @param callback Callback que receberá notificações dos eventos que ocorrerem neste MulticastInterface
     * @param multicastAddress Endereço de multicast que será utilizado
     * @param port Porta que será utilizada na comunicação com o grupo multicast
     * @param sessionIdentifier Identificador de grupo
     * @param receiveBufferSize Tamanho do buffer de recepção. Pacotes com tamanho superior a este valor serão descartados
     * @param outputBufferSize Tamanho do buffer de saída. Pacotes com tamanho superior a este valor serão descartados
     * @throws IOException Caso ocorram erros de E/S
     */
    public MulticastInterface(MulticastInterfaceCallback callback, InetAddress multicastAddress, int port, int sessionIdentifier, int receiveBufferSize, int outputBufferSize) throws IOException {
        if (!multicastAddress.isMulticastAddress()) throw new IllegalArgumentException("Address must be a Multicast Address");
        this.address = multicastAddress;
        this.port = port;
        this.sessionIdentifier = sessionIdentifier;
        this.callback = callback;
        createSocket();
        receiveBuffer = ByteBuffer.allocate(receiveBufferSize + 5);
        outputBuffer = ByteBuffer.allocate(outputBufferSize + 5);
    }
    
    /**
     * Construtor secundário. Chama o construtor padrão utilizando buffers de entrada e saída com 1024 bytes.
     * @param callback Callback que receberá notificações dos eventos que ocorrerem neste MulticastInterface
     * @param multicastAddress Endereço de multicast que será utilizado
     * @param port Porta que será utilizada na comunicação com o grupo multicast
     * @param sessionIdentifier Identificador de grupo
     * @throws IOException Caso ocorram erros de E/S
     */
    public MulticastInterface(MulticastInterfaceCallback callback, InetAddress multicastAddress, int port, int sessionIdentifier) throws IOException {
        this(callback, multicastAddress, port, sessionIdentifier, 1024, 1024);
    }
    
    /**
     * Envia uma mensagem do tipo JOIN_REQUEST_MSG
     * @param managerStatus Status de gerenciamento deste host
     * @throws IOException Caso ocorram erros de E/S
     */
    public synchronized void sendJoinRequest(byte managerStatus) throws IOException {
        outputBuffer.putShort(JOIN_REQUEST_MSG_TYPE);
        outputBuffer.putInt(sessionIdentifier);
        outputBuffer.put(managerStatus);
        sendMulticastMessage();
    }
    
    /**
     * Envia uma mensagem do tipo JOIN_RESPONSE_MSG
     * @param targetAddress Endereço de destino
     * @param managerStatus Status de gerenciamento deste host
     * @throws IOException Caso ocorram erros de E/S
     */
    public synchronized void sendJoinResponse(InetAddress targetAddress, byte managerStatus) throws IOException {
        outputBuffer.putShort(JOIN_RESPONSE_MSG_TYPE);
        outputBuffer.putInt(sessionIdentifier);
        outputBuffer.put(managerStatus);
        sendMessage(targetAddress);
    }
    
    /**
     * Envia uma mensagem do tipo DROP_MSG
     * @throws IOException Caso ocorram erros de E/S
     */
    public synchronized void sendDropMessage() throws IOException {
        outputBuffer.putShort(DROP_MSG_TYPE);
        outputBuffer.putInt(sessionIdentifier);
        sendMulticastMessage();
    }
    
    /**
     * Envia uma mensagem do tipo HEARTBEAT_MSG
     * @param managerStatus Status de gerenciamento deste host
     * @throws IOException Caso ocorram erros de E/S
     */
    public synchronized void sendHeartBeatMessage(byte managerStatus) throws IOException {
        outputBuffer.putShort(HEARTBEAT_MSG_TYPE);
        outputBuffer.putInt(sessionIdentifier);
        outputBuffer.put(managerStatus);
        sendMulticastMessage();
    }

    /**
     * Envia uma mensagem para o subgrupo
     * @param data Buffer contendo a mensagem a ser transmitida
     * @param offset Início da mensagem dentro do buffer
     * @param length Tamanho da mensagem
     * @throws IOException Caso ocorram erros de E/S
     */
    public synchronized void sendAppMessage(byte[] data, int offset, int length) throws IOException {
        if (length <= outputBuffer.remaining() - 6) {
            outputBuffer.putShort(GENERAL_MSG_TYPE);
            outputBuffer.putInt(sessionIdentifier);
            outputBuffer.put(data, offset, length);
            sendMulticastMessage();
        }
    }

    /**
     * Envia uma mensagem para o subgrupo
     * @param sourceBuffer Buffer contendo a mensagem a ser transmitida
     * @throws IOException Caso ocorram erros de E/S
     */
    public synchronized void sendAppMessage(ByteBuffer sourceBuffer) throws IOException {
        if (sourceBuffer.remaining() <= outputBuffer.remaining() - 6) {
            outputBuffer.putShort(GENERAL_MSG_TYPE);
            outputBuffer.putInt(sessionIdentifier);
            outputBuffer.put(sourceBuffer);
            sendMulticastMessage();
        }
    }
    
    public void close() {
        try {
            stop = true;
            sendDropMessage();
            mSocket.leaveGroup(address);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
            mSocket.close();
        }
    }
    
    /**
     * Trata a mensagem recebida e notifica a callback
     * @param sourceAddress Endereço do remetente da mensagem
     */
    private void parseMessage(InetAddress sourceAddress) {
        if (receiveBuffer.remaining() >= 1) {
            short msgType = receiveBuffer.getShort();
            if (msgType == GENERAL_MSG_TYPE) {
            	if (receiveBuffer.remaining() >= 4) {
            		int sessionCode = receiveBuffer.getInt();
            		if (sessionIdentifier > 0 && sessionCode == sessionIdentifier) {
            			callback.appMessageReceived(sourceAddress, receiveBuffer);
            		}
            	}
            } else if (msgType == JOIN_RESPONSE_MSG_TYPE) {
            	if (receiveBuffer.remaining() == 5) {
            		int sessionCode = receiveBuffer.getInt();
            		byte managerStatus = receiveBuffer.get();
            		if (sessionCode == sessionIdentifier) {
            			callback.joinResponseReceived(sourceAddress, managerStatus);
            		}
            	}
            } else if (msgType == DROP_MSG_TYPE) {
            	if (receiveBuffer.remaining() == 4) {
            		int sessionCode = receiveBuffer.getInt();
            		if (sessionCode == sessionIdentifier) {
            			callback.dropMessageReceived(sourceAddress);
            		}
            	}
            } else if (msgType == JOIN_REQUEST_MSG_TYPE) {
            	if (receiveBuffer.remaining() == 5) {
            		int sessionCode = receiveBuffer.getInt();
            		byte managerStatus = receiveBuffer.get();
            		if (sessionCode == sessionIdentifier) {
            			callback.joinRequestReceived(sourceAddress, managerStatus);
            		}
            	}
            } else if (msgType == HEARTBEAT_MSG_TYPE) {
            	if (receiveBuffer.remaining() == 5) {
            		int sessionCode = receiveBuffer.getInt();
            		byte managerStatus = receiveBuffer.get();
            		if (sessionCode == sessionIdentifier) {
            			callback.heartbeatMessageReceived(sourceAddress, managerStatus);
            		}
            	}
            }
        }
    }

    /**
     * Cria o Socket Multicast
     * @throws IOException Caso ocorram erros de E/S
     */
    private void createSocket() throws IOException {
        mSocket = new MulticastSocket(port);
        mSocket.setTimeToLive(1);
        mSocket.joinGroup(address);
        mSocket.setLoopbackMode(true);
    }
    
    /**
     * Envia efetivamente a mensagem contida no buffer de saída através do socket
     * multicast para o endereço especificado
     * @param targetAddress Endereço de destino da mensagem
     * @throws IOException Caso ocorram erros de E/S
     */
    private void sendMessage(InetAddress targetAddress) throws IOException {
        outputBuffer.flip();
        mSocket.send(new DatagramPacket(outputBuffer.array(), outputBuffer.remaining(), targetAddress, port));
        outputBuffer.clear();
    }
    
    /**
     * Envia efetivamente a mensagem contida no buffer de saída através do socket
     * multicast para todo o subgrupo
     * @throws IOException Caso ocorram erros de E/S
     */
    private void sendMulticastMessage() throws IOException {
        outputBuffer.flip();
        mSocket.send(new DatagramPacket(outputBuffer.array(), outputBuffer.remaining(), address, port));
        outputBuffer.clear();
    }
    
    public void run() {
        try {
            DatagramPacket incomingPacket;
            while(!stop) {
                receiveBuffer.clear();
                incomingPacket = new DatagramPacket(receiveBuffer.array(), receiveBuffer.remaining());
                mSocket.receive(incomingPacket);
                receiveBuffer.position(0).limit(incomingPacket.getLength());
                parseMessage(incomingPacket.getAddress());
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        callback.interfaceClosed(stop);
    }
    
}

