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
     * Endere�o do host remoto
     */
    private SocketAddress remoteAddress;
    
    /**
     * Socket atrav�s do qual ser�o enviados e recebidos os datagramas
     */
    private DatagramChannel datagramChannel;
    
    /**
     * Vari�vel incremental que gera os c�digos de identifica��o para as mensagens de controle
     * enviadas atrav�s desta UDPRemoteConnection
     */
    private long ctrlPacketCount;
    
    /**
     * Lista de mensagens ack aguardando confirma��o
     */
    private ArrayList<CtrlMessage> waitingAcks;
    
    /**
     * Lista de mensagens de controle recebidas. Para evitar o reprocessamento de uma mensagem
     * indevidamente retransmitida, esta lista mant�m armazenadas as �ltimas trinta mensagens de controle
     * recebidas.
     */
    private LimitedList<CtrlMessage> ctrlMessages;
    
    /**
     * Buffer de sa�da
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
     * Construtor secund�rio. Define o tamanho dos buffers de entrada e sa�da em 1024 bytes.
     * @param callback Callback que receber� os eventos gerados por esta RemoteConnection.
     * @param remoteAddress Endere�o do host remoto com o qual a comunica��o ser� estabelecida.
     */
    public UDPRemoteConnection(RemoteCommunicator callback, SocketAddress remoteAddress) {
        this(callback, remoteAddress, 1024, 1024);
    }

    /**
     * Construtor principal.
     * @param callback Callback que receber� os eventos gerados por esta RemoteConnection.
     * @param remoteAddress Endere�o do host remoto com o qual a comunica��o ser� estabelecida.
     * @param inputBufferSize Tamanho do buffer de entrada utilizado
     * @param outputBufferSize Tamanho do buffer de sa�da utilizado
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
            // TODO Capturar esta exce��o
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
        // Obt�m um identificador para a mensagem de controle e a adiciona � lista
        // de mensagens que aguardam confirma��o. Esta sincroniza��o � necess�ria
        // para que a vari�vel ctrlPacketCount possa ser acessada e alterada sem
        // problemas
        synchronized(this) {
            if (!open) throw new IOException("Socket is not open");
            packetID = ctrlPacketCount++;
            ack = new CtrlMessage(packetID);
            waitingAcks.add(ack);
        }
        int tryCount;
        data.mark();
        // Tenta transmitir a mensagem por, no m�ximo, tr�s vezes, caso n�o seja recebida
        // a confirma��o de recebimento
        for (tryCount = 0; tryCount < 3 && waitingAcks.contains(ack); tryCount++) {
        	// Envia a mensagem atrav�s do socket
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
                    ack.wait(3000);	// Aguarda pela confirma��o por tr�s segundos
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        if (tryCount > 2) {
        	// Se o n�mero de reenvios foi atingido sem que uma confirma��o fosse recebida,
        	// esta UDPRemoteConnection � finalizada
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
     * Envia uma mensagem de confirma��o de recebimento ao host remoto
     * @param ackCode C�digo que indica a identidade do pacote confirmado
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
                    if (msgType == NORMAL_MSG) { // Mensagem da aplica��o
                        callback.packetReceived(this, inputBuffer);
                    } else if (msgType == CTRL_MSG) {	// Mensagem de controle
                        long msgCode = inputBuffer.getLong();
                        CtrlMessage ctrlMessage = getControlMessage(msgCode);
                        sendAck(msgCode);	// Envia confirma��o de recebimento ao host remoto
                        if (ctrlMessage != null) {
                        	// Processa a mensagem somente se ela ainda n�o foi processada anteriormente
                            if (!ctrlMessage.alreadyProcessed) {
                                ctrlMessage.alreadyProcessed = true;
                                callback.ctrlPacketReceived(this, inputBuffer);
                            }
                        } else {
                        	// Se a mensagem n�o foi localizada na lista de mensagens recebidas,
                        	// processa o seu conte�do e a adiciona na listagem
                            ctrlMessage = new CtrlMessage(msgCode);
                            ctrlMessage.alreadyProcessed = true;
                            ctrlMessages.add(ctrlMessage);
                            callback.ctrlPacketReceived(this, inputBuffer);
                        }
                    } else if (msgType == HEARTBEAT_MSG) {
                        // Ignore...
                    } else if (msgType == ACK_MSG) {
                    	// Remove a mensagem de controle da lista de espera por confirma��o
                    	// e notifica a thread que est� bloqueada no m�todo sendCtrlMessage
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
     * Obt�m a mensagem de controle armazenada na lista a partir do seu c�digo de
     * identifica��o
     * @param msgCode C�digo de identifica��o da mensagem de controle procurada 
     * @return A mensagem de controle previamente recebida representada pelo c�digo de identifica��o especificado
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
     * Obt�m a objeto que representa a mensagem ack identificada pelo c�digo <code>ackCode</code>
     * @param ackCode C�digo de identifica��o da mensagem ack desejada
     * @return A mensagem ack identificada pelo c�digo especificado
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
     * Classe utilit�ria que representa uma mensagem de controle
     * @author Juliano
     *
     */
    private class CtrlMessage {
    	/**
    	 * C�digo de identifica��o da mensagem
    	 */
        private long msgCode;
        
        /**
         * Flag que indica se esta mensagem de controle j� foi processada ou n�o
         */
        private boolean alreadyProcessed;
        
        private CtrlMessage(long msgCode) {
            this.msgCode = msgCode;
            alreadyProcessed = false;
        }
    }
    
}
