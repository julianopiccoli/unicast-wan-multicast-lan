package jpiccoli.uwml.remote;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RemoteCommunicator {
    
    public final static short INITIALIZE_MSG = 1;
    public final static short DROP_MSG       = 2;
    public final static short ENABLE_MSG     = 4;
    
    /**
     * Tamanho m�nimo dos buffers de entrada e sa�da. Corresponde
     * ao espa�o dos cabe�alhos que s�o inseridos automaticamente nas
     * mensagens.
     */
    public final static int MINIMUM_BUFFER_SIZE = 10;

    /**
     * Callback que receber� notifica��es dos eventos gerados
     * neste RemoteCommunicator
     */
    private RemoteCommunicatorCallback callback;
    
    /**
     * RemoteConnection utilizada por este RemoteCommunicator
     * para realizar a troca de mensagens com o host
     * remoto
     */
    private RemoteConnection connection;
    
    /**
     * C�digo de identifica��o do grupo
     */
    private int sessionIdentifier;
    
    /**
     * Flag que indica se este RemoteCommunicator est� ativado
     */
    private boolean enabled;
    
    /**
     * Flag que indica se este RemoteCommunicator est� aberto
     */
    private boolean open;
    
    /**
     * Flag que indica se as threads de E/S devem ser interrompidas
     * e finalizadas
     */
    private boolean stop;
    
    /**
     * Lock para sincroniza��o de envio de mensagens de controle.
     * � necess�rio realizar a sincroniza��o no envio destas mensagens
     * porque as informa��es de controle s�o enviadas atrav�s de um 
     * buffer compartilhado.
     */
    private Object ctrlSendLock;
    
    /**
     * Buffer exclusivo para o envio da mensagem de inicializa��o
     */
    private ByteBuffer initializeBuffer;
    
    /**
     * Buffer de sa�da utilizado para o envio de mensagens de controle
     * e mensagens da aplica��o
     */
    private ByteBuffer outputBuffer;

    /**
     * Retorna o valor m�nimo dos buffers de entrada e sa�da. Corresponde
     * ao espa�o reservado aos cabe�alhos das mensagens.
     * @return O valor m�nimo dos buffers de entrada e sa�da
     */
    public static int getMinimumBufferSize() {
        return MINIMUM_BUFFER_SIZE;
    }
    
    /**
     * Construtor
     * @param callback Callback que receber� notifica��es de eventos gerados por este RemoteCommunicator
     */
    public RemoteCommunicator(RemoteCommunicatorCallback callback) {
        this.callback = callback;
        ctrlSendLock = new Object();
        outputBuffer = ByteBuffer.allocateDirect(MINIMUM_BUFFER_SIZE);
        initializeBuffer = ByteBuffer.allocateDirect(MINIMUM_BUFFER_SIZE);
    }
    
    /**
     * Define a callback que receber� as notifica��es dos eventos gerados por este RemoteCommunicator
     * @param callback Callback que receber� as notifica��es dos eventos gerados por este RemoteCommunicator
     */
    public void setCommunicatorCallback(RemoteCommunicatorCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Retorna a callback que recebe as notifica��es de eventos deste RemoteCommunicator
     * @return A callback que recebe as notifica��es de eventos deste RemoteCommunicator
     */
    public RemoteCommunicatorCallback getCommunicatorCallback() {
        return callback;
    }
    
    /**
     * Define a RemoteConnection utilizada por este RemoteCommunicator
     * @param connection RemoteConnection que este RemoteCommunicator utilizar� para comunicar-se com o host remoto
     */
    public void setConnection(RemoteConnection connection) {
        this.connection = connection;
    }

    /**
     * Retorna a RemoteConnection que est� sendo utilizada por este RemoteCommunicator
     * @return A RemoteConnection que est� sendo utilizada por este RemoteCommunicator
     */
    public RemoteConnection getConnection() {
        return connection;
    }
    
    /**
     * Define o identificador do grupo de confer�ncia
     * @param sessionIdentifier Identificador do grupo de confer�ncia
     */
    public void setSessionIdentifier(int sessionIdentifier) {
        this.sessionIdentifier = sessionIdentifier;
    }
    
    /**
     * Retorna o identificador do grupo de confer�ncia
     * @return O identificador do grupo de confer�ncia
     */
    public int getSessionIdentifier() {
        return sessionIdentifier;
    }
    
    /**
     * Realiza os procedimentos de inicializa��o e prepara este RemoteCommunicator para uso
     */
    public synchronized void initialize() {
        if (!open && !stop) {
            try {
                connection.openCommunication();
                initializeBuffer.clear();
                initializeBuffer.putShort(INITIALIZE_MSG);
                initializeBuffer.putInt(sessionIdentifier);
                initializeBuffer.flip();
                connection.sendCtrlMessage(initializeBuffer);
                open = true;
                sendEnabledMessage(isEnabled());
                callback.connectionInitialized(this);
            } catch (Exception e) {
                connection.closeCommunication();
                callback.connectionLost(this);
            }
        }
    }

    /**
     * Envia uma mensagem da aplica��o ao host remoto
     * @param data Carga da mensagem a ser enviada
     * @throws IOException Caso ocorram erros de E/S na transmiss�o
     */
    public void sendAppMessage(ByteBuffer data) throws IOException {
        if (isOpen()) {
            data.mark();
            connection.sendMessage(data);
            data.reset();
        }
    }
    
    /**
     * Envia uma mensagem ao host remoto que sinaliza o encerramento da conex�o deste RemoteCommunicator
     */
    public void sendDropMessage() {
        synchronized(ctrlSendLock) {
            try {
                outputBuffer.clear();
                outputBuffer.putShort(DROP_MSG);
                outputBuffer.flip();
                connection.sendCtrlMessage(outputBuffer);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Retorna true se a conex�o deste RemoteCommunicator com o host remoto tiver sido inicializada com sucesso
     * @return True se a conex�o deste RemoteCommunicator com o host remoto tiver sido inicializada com sucesso
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Define se este RemoteCommunicator est� habilitado ou n�o a transmitir e receber mensagens da aplica��o
     * @param enabled True indica que este RemoteCommunicator est� habilitado a transmitir e receber mensagens da aplica��o
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;            
        if (isOpen()) {
            sendEnabledMessage(enabled);
        }
    }
    
    /**
     * Retorna true se este RemoteCommunicator est� habilitado a receber e/ou enviar mensagens da aplica��o ao host remoto
     * @return True se este RemoteCommunicator est� habilitado a receber e/ou enviar mensagens da aplica��o ao host remoto
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Envia uma mensagem de sinaliza��o que notifica ao host remoto que de que este RemoteCommunicator est� habilitado a receber e/ou transmitir mensagens
     * da aplica��o
     * @param enabled True indica que este RemoteCommunicator est� habilitado a transmitir e receber mensagens da aplica��o
     */
    private void sendEnabledMessage(boolean enabled) {
        synchronized(ctrlSendLock) {
            try {
                outputBuffer.clear();
                outputBuffer.putShort(ENABLE_MSG);
                outputBuffer.put(enabled ? (byte) 1 : (byte) 0);
                outputBuffer.flip();
                connection.sendCtrlMessage(outputBuffer);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Fecha este RemoteCommunicator e libera os recursos alocados por ele
     */
    public void close() {
        stop = true;
        sendDropMessage();
        connection.closeCommunication();
    }

    /**
     * Notifica a callback de que a conex�o utilizada por este RemoteCommunicator foi perdida
     * @param source RemoteConnection que perdeu sua conex�o com o servidor
     */
    public synchronized void connectionLost(RemoteConnection source) {
    	// Este m�todo recebe como par�metro o RemoteConnection que gerou o evento de notifica��o de perda
    	// de conex�o porque isso permite ao RemoteCommunicator verificar se este evento foi realmente gerado
    	// pelo RemoteConnection que ele est� utilizando atualmente.
        if (isOpen() && source == connection) {
            open = false;
            callback.connectionLost(this);
        }
    }

    /**
     * Reliza os procedimentos de inicializa��o deste RemoteCommunicator, inclusive a notifica��o
     * � callback. Este m�todo � chamado em resposta a uma mensagem de ativa��o recebida do host remoto.
     * @param sessionIdentifier Identificador do grupo de confer�ncia
     */
    private synchronized void remoteInitialized(int sessionIdentifier) {
        open = true;
        this.sessionIdentifier = sessionIdentifier;
        callback.connectionInitialized(this);
    }
    
    /**
     * Analisa e processa a mensagem de sinaliza��o e/ou controle recebida
     * @param source RemoteConnection que recebeu a mensagem de controle
     * @param data Informa��o contida na mensagem de controle
     */
    public void ctrlPacketReceived(RemoteConnection source, ByteBuffer data) {
        if (source == connection) {
            short msgType = data.getShort();
            if (msgType == DROP_MSG) {
                connectionLost(source);
            } else if (msgType == INITIALIZE_MSG) {
                if (data.remaining() == 4) {
                    int sessionIdentifier = data.getInt();
                    remoteInitialized(sessionIdentifier);
                }
            } else if (msgType == ENABLE_MSG) {
                if (data.remaining() == 1) {
                    byte enableByte = data.get();
                    boolean enabled = enableByte != 0;
                    this.enabled = enabled;
                    callback.connectionStatusChanged(this);
                }
            }
        }
    }
    
    /**
     * Notifica � callback de que uma mensagem da aplica��o foi recebida
     * @param source RemoteConnection que recebeu a mensagem
     * @param data Carga da mensagem recebida
     */
    public void packetReceived(RemoteConnection source, ByteBuffer data) {
        if (source == connection) {
            callback.packetReceived(this, data);
        }
    }
    
}
