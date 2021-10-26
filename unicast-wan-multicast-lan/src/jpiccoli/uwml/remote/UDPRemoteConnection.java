package jpiccoli.uwml.remote;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;

import jpiccoli.uwml.util.LimitedList;

public class UDPRemoteConnection extends RemoteConnection implements Runnable {

    public static final short ACK_MSG    = 3;
    
    /**
     * Endereço do host remoto
     */
    private SocketAddress remoteAddress;
    
    /**
     * Socket através do qual serão enviados e recebidos os datagramas
     */
    private DatagramChannel datagramChannel;
    
    /**
     * Variável incremental que gera os códigos de identificação para as mensagens de controle
     * enviadas através desta UDPRemoteConnection
     */
    private long ctrlPacketCount;
    
    /**
     * Lista de mensagens ack aguardando confirmação
     */
    private ArrayList<CtrlMessage> waitingAcks;
    
    /**
     * Lista de mensagens de controle recebidas. Para evitar o reprocessamento de uma mensagem
     * indevidamente retransmitida, esta lista mantém armazenadas as últimas trinta mensagens de controle
     * recebidas.
     */
    private LimitedList<CtrlMessage> ctrlMessages;
    
    /**
     * Buffer de saída
     */
    private ByteBuffer outputBuffer;
    
    /**
     * Buffer de entrada
     */
    private ByteBuffer inputBuffer;
    
    private boolean stop;
    private boolean open;
    
    private Thread thread;

    /**
     * Construtor secundário. Define o tamanho dos buffers de entrada e saída em 1024 bytes.
     * @param callback Callback que receberá os eventos gerados por esta RemoteConnection.
     * @param remoteAddress Endereço do host remoto com o qual a comunicação será estabelecida.
     */
    public UDPRemoteConnection(RemoteCommunicator callback, SocketAddress remoteAddress) {
        this(callback, remoteAddress, 1024, 1024);
    }

    /**
     * Construtor principal.
     * @param callback Callback que receberá os eventos gerados por esta RemoteConnection.
     * @param remoteAddress Endereço do host remoto com o qual a comunicação será estabelecida.
     * @param inputBufferSize Tamanho do buffer de entrada utilizado
     * @param outputBufferSize Tamanho do buffer de saída utilizado
     */
    public UDPRemoteConnection(RemoteCommunicator callback, SocketAddress remoteAddress, int inputBufferSize, int outputBufferSize) {
        super(callback, inputBufferSize, outputBufferSize);
        this.remoteAddress = remoteAddress;
        waitingAcks = new ArrayList<CtrlMessage>();
        ctrlMessages = new LimitedList<CtrlMessage>(30);
        outputBuffer = ByteBuffer.allocateDirect(outputBufferSize + 10);
        inputBuffer = ByteBuffer.allocateDirect(inputBufferSize + 10);
    }
    
    @Override
    public synchronized void closeCommunication() {
        if (!stop) {
            try {
                heartBeat.removeConnection(this);
                open = false;
                stop = true;
                datagramChannel.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @Override
    public synchronized void openCommunication() throws IOException {
        if (!stop && thread == null) {
            // TODO Capturar esta exceção
            datagramChannel = DatagramChannel.open();
            open = true;
            stop = false;
            thread = new Thread(this);
            thread.setName("UDP Remote Connection Thread");
            thread.setDaemon(true);
            thread.start();
            heartBeat.addConnection(this);
        }
    }

    @Override
    public void sendCtrlMessage(ByteBuffer data) throws IOException {
        CtrlMessage ack = null;
        long packetID = 0;
        // Obtém um identificador para a mensagem de controle e a adiciona à lista
        // de mensagens que aguardam confirmação. Esta sincronização é necessária
        // para que a variável ctrlPacketCount possa ser acessada e alterada sem
        // problemas
        synchronized(this) {
            if (!open) throw new IOException("Socket is not open");
            packetID = ctrlPacketCount++;
            ack = new CtrlMessage(packetID);
            waitingAcks.add(ack);
        }
        int tryCount;
        data.mark();
        // Tenta transmitir a mensagem por, no máximo, três vezes, caso não seja recebida
        // a confirmação de recebimento
        for (tryCount = 0; tryCount < 3 && waitingAcks.contains(ack); tryCount++) {
        	// Envia a mensagem através do socket
            synchronized(this) {
                data.reset();
                outputBuffer.clear();
                outputBuffer.putShort(CTRL_MSG);
                outputBuffer.putLong(packetID);
                outputBuffer.put(data);
                outputBuffer.flip();
                datagramChannel.send(outputBuffer, remoteAddress);
            }
            try {
                synchronized(ack) {
                    ack.wait(3000);	// Aguarda pela confirmação por três segundos
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (tryCount > 2) {
        	// Se o número de reenvios foi atingido sem que uma confirmação fosse recebida,
        	// esta UDPRemoteConnection é finalizada
            closeCommunication();
            throw new IOException("Ack not received after 3 tries");
        }
    }

    @Override
    public synchronized void sendHeartBeat() throws IOException {
        if (!open) throw new IOException("Socket is not open");
        outputBuffer.clear();
        outputBuffer.putShort(HEARTBEAT_MSG);
        outputBuffer.flip();
        datagramChannel.send(outputBuffer, remoteAddress);
    }

    @Override
    public synchronized void sendMessage(ByteBuffer data) throws IOException {
        if (!open) throw new IOException("Socket is not open");
        outputBuffer.clear();
        outputBuffer.putShort(NORMAL_MSG);
        outputBuffer.put(data);
        outputBuffer.flip();
        datagramChannel.send(outputBuffer, remoteAddress);
    }
    
    /**
     * Envia uma mensagem de confirmação de recebimento ao host remoto
     * @param ackCode Código que indica a identidade do pacote confirmado
     */
    private synchronized void sendAck(long ackCode) {
        try {
            if (!open) {
                outputBuffer.clear();
                outputBuffer.putShort(ACK_MSG);
                outputBuffer.putLong(ackCode);
                outputBuffer.flip();
                datagramChannel.send(outputBuffer, remoteAddress);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    public void run() {
        try {
            while(!stop) {
                inputBuffer.clear();
                datagramChannel.receive(inputBuffer);
                setActive(true);
                inputBuffer.flip();
                if (inputBuffer.remaining() >= 2) {
                    short msgType = inputBuffer.getShort();
                    if (msgType == NORMAL_MSG) { // Mensagem da aplicação
                        callback.packetReceived(this, inputBuffer);
                    } else if (msgType == CTRL_MSG) {	// Mensagem de controle
                        long msgCode = inputBuffer.getLong();
                        CtrlMessage ctrlMessage = getControlMessage(msgCode);
                        sendAck(msgCode);	// Envia confirmação de recebimento ao host remoto
                        if (ctrlMessage != null) {
                        	// Processa a mensagem somente se ela ainda não foi processada anteriormente
                            if (!ctrlMessage.alreadyProcessed) {
                                ctrlMessage.alreadyProcessed = true;
                                callback.ctrlPacketReceived(this, inputBuffer);
                            }
                        } else {
                        	// Se a mensagem não foi localizada na lista de mensagens recebidas,
                        	// processa o seu conteúdo e a adiciona na listagem
                            ctrlMessage = new CtrlMessage(msgCode);
                            ctrlMessage.alreadyProcessed = true;
                            ctrlMessages.add(ctrlMessage);
                            callback.ctrlPacketReceived(this, inputBuffer);
                        }
                    } else if (msgType == HEARTBEAT_MSG) {
                        // Ignore...
                    } else if (msgType == ACK_MSG) {
                    	// Remove a mensagem de controle da lista de espera por confirmação
                    	// e notifica a thread que está bloqueada no método sendCtrlMessage
                        if (inputBuffer.remaining() == 8) {
                            CtrlMessage ack = getAck(inputBuffer.getLong());
                            if (ack != null) {
                                synchronized(ack) {
                                    waitingAcks.remove(ack);
                                    ack.notify();
                                }
                            }
                        }
                    } else {
                        throw new IOException("Unknown message received");
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
    
    /**
     * Obtém a mensagem de controle armazenada na lista a partir do seu código de
     * identificação
     * @param msgCode Código de identificação da mensagem de controle procurada 
     * @return A mensagem de controle previamente recebida representada pelo código de identificação especificado
     */
    private CtrlMessage getControlMessage(long msgCode) {
        for (int i = 0; i < ctrlMessages.size(); i++) {
            CtrlMessage ctrlMessage = ctrlMessages.get(i);
            if (ctrlMessage.msgCode == msgCode) {
                return ctrlMessage;
            }
        }
        return null;
    }
    
    /**
     * Obtém a objeto que representa a mensagem ack identificada pelo código <code>ackCode</code>
     * @param ackCode Código de identificação da mensagem ack desejada
     * @return A mensagem ack identificada pelo código especificado
     */
    private synchronized CtrlMessage getAck(long ackCode) {
        for (CtrlMessage ack : waitingAcks) {
            if (ack.msgCode == ackCode) {
                return ack;
            }
        }
        return null;
    }
    
    /**
     * Classe utilitária que representa uma mensagem de controle
     * @author Juliano
     *
     */
    private class CtrlMessage {
    	/**
    	 * Código de identificação da mensagem
    	 */
        private long msgCode;
        
        /**
         * Flag que indica se esta mensagem de controle já foi processada ou não
         */
        private boolean alreadyProcessed;
        
        private CtrlMessage(long msgCode) {
            this.msgCode = msgCode;
            alreadyProcessed = false;
        }
    }
    
}
