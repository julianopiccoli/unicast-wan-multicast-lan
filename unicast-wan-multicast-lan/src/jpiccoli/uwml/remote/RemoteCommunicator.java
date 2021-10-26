package jpiccoli.uwml.remote;

import java.io.IOException;
import java.nio.ByteBuffer;

public class RemoteCommunicator {
    
    public final static short INITIALIZE_MSG = 1;
    public final static short DROP_MSG       = 2;
    public final static short ENABLE_MSG     = 4;
    
    /**
     * Tamanho mínimo dos buffers de entrada e saída. Corresponde
     * ao espaço dos cabeçalhos que são inseridos automaticamente nas
     * mensagens.
     */
    public final static int MINIMUM_BUFFER_SIZE = 10;

    /**
     * Callback que receberá notificações dos eventos gerados
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
     * Código de identificação do grupo
     */
    private int sessionIdentifier;
    
    /**
     * Flag que indica se este RemoteCommunicator está ativado
     */
    private boolean enabled;
    
    /**
     * Flag que indica se este RemoteCommunicator está aberto
     */
    private boolean open;
    
    /**
     * Flag que indica se as threads de E/S devem ser interrompidas
     * e finalizadas
     */
    private boolean stop;
    
    /**
     * Lock para sincronização de envio de mensagens de controle.
     * É necessário realizar a sincronização no envio destas mensagens
     * porque as informações de controle são enviadas através de um 
     * buffer compartilhado.
     */
    private Object ctrlSendLock;
    
    /**
     * Buffer exclusivo para o envio da mensagem de inicialização
     */
    private ByteBuffer initializeBuffer;
    
    /**
     * Buffer de saída utilizado para o envio de mensagens de controle
     * e mensagens da aplicação
     */
    private ByteBuffer outputBuffer;

    /**
     * Retorna o valor mínimo dos buffers de entrada e saída. Corresponde
     * ao espaço reservado aos cabeçalhos das mensagens.
     * @return O valor mínimo dos buffers de entrada e saída
     */
    public static int getMinimumBufferSize() {
        return MINIMUM_BUFFER_SIZE;
    }
    
    /**
     * Construtor
     * @param callback Callback que receberá notificações de eventos gerados por este RemoteCommunicator
     */
    public RemoteCommunicator(RemoteCommunicatorCallback callback) {
        this.callback = callback;
        ctrlSendLock = new Object();
        outputBuffer = ByteBuffer.allocateDirect(MINIMUM_BUFFER_SIZE);
        initializeBuffer = ByteBuffer.allocateDirect(MINIMUM_BUFFER_SIZE);
    }
    
    /**
     * Define a callback que receberá as notificações dos eventos gerados por este RemoteCommunicator
     * @param callback Callback que receberá as notificações dos eventos gerados por este RemoteCommunicator
     */
    public void setCommunicatorCallback(RemoteCommunicatorCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Retorna a callback que recebe as notificações de eventos deste RemoteCommunicator
     * @return A callback que recebe as notificações de eventos deste RemoteCommunicator
     */
    public RemoteCommunicatorCallback getCommunicatorCallback() {
        return callback;
    }
    
    /**
     * Define a RemoteConnection utilizada por este RemoteCommunicator
     * @param connection RemoteConnection que este RemoteCommunicator utilizará para comunicar-se com o host remoto
     */
    public void setConnection(RemoteConnection connection) {
        this.connection = connection;
    }

    /**
     * Retorna a RemoteConnection que está sendo utilizada por este RemoteCommunicator
     * @return A RemoteConnection que está sendo utilizada por este RemoteCommunicator
     */
    public RemoteConnection getConnection() {
        return connection;
    }
    
    /**
     * Define o identificador do grupo de conferência
     * @param sessionIdentifier Identificador do grupo de conferência
     */
    public void setSessionIdentifier(int sessionIdentifier) {
        this.sessionIdentifier = sessionIdentifier;
    }
    
    /**
     * Retorna o identificador do grupo de conferência
     * @return O identificador do grupo de conferência
     */
    public int getSessionIdentifier() {
        return sessionIdentifier;
    }
    
    /**
     * Realiza os procedimentos de inicialização e prepara este RemoteCommunicator para uso
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
     * Envia uma mensagem da aplicação ao host remoto
     * @param data Carga da mensagem a ser enviada
     * @throws IOException Caso ocorram erros de E/S na transmissão
     */
    public void sendAppMessage(ByteBuffer data) throws IOException {
        if (isOpen()) {
            data.mark();
            connection.sendMessage(data);
            data.reset();
        }
    }
    
    /**
     * Envia uma mensagem ao host remoto que sinaliza o encerramento da conexão deste RemoteCommunicator
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
     * Retorna true se a conexão deste RemoteCommunicator com o host remoto tiver sido inicializada com sucesso
     * @return True se a conexão deste RemoteCommunicator com o host remoto tiver sido inicializada com sucesso
     */
    public boolean isOpen() {
        return open;
    }

    /**
     * Define se este RemoteCommunicator está habilitado ou não a transmitir e receber mensagens da aplicação
     * @param enabled True indica que este RemoteCommunicator está habilitado a transmitir e receber mensagens da aplicação
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;            
        if (isOpen()) {
            sendEnabledMessage(enabled);
        }
    }
    
    /**
     * Retorna true se este RemoteCommunicator está habilitado a receber e/ou enviar mensagens da aplicação ao host remoto
     * @return True se este RemoteCommunicator está habilitado a receber e/ou enviar mensagens da aplicação ao host remoto
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Envia uma mensagem de sinalização que notifica ao host remoto que de que este RemoteCommunicator está habilitado a receber e/ou transmitir mensagens
     * da aplicação
     * @param enabled True indica que este RemoteCommunicator está habilitado a transmitir e receber mensagens da aplicação
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
     * Notifica a callback de que a conexão utilizada por este RemoteCommunicator foi perdida
     * @param source RemoteConnection que perdeu sua conexão com o servidor
     */
    public synchronized void connectionLost(RemoteConnection source) {
    	// Este método recebe como parâmetro o RemoteConnection que gerou o evento de notificação de perda
    	// de conexão porque isso permite ao RemoteCommunicator verificar se este evento foi realmente gerado
    	// pelo RemoteConnection que ele está utilizando atualmente.
        if (isOpen() && source == connection) {
            open = false;
            callback.connectionLost(this);
        }
    }

    /**
     * Reliza os procedimentos de inicialização deste RemoteCommunicator, inclusive a notificação
     * à callback. Este método é chamado em resposta a uma mensagem de ativação recebida do host remoto.
     * @param sessionIdentifier Identificador do grupo de conferência
     */
    private synchronized void remoteInitialized(int sessionIdentifier) {
        open = true;
        this.sessionIdentifier = sessionIdentifier;
        callback.connectionInitialized(this);
    }
    
    /**
     * Analisa e processa a mensagem de sinalização e/ou controle recebida
     * @param source RemoteConnection que recebeu a mensagem de controle
     * @param data Informação contida na mensagem de controle
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
     * Notifica à callback de que uma mensagem da aplicação foi recebida
     * @param source RemoteConnection que recebeu a mensagem
     * @param data Carga da mensagem recebida
     */
    public void packetReceived(RemoteConnection source, ByteBuffer data) {
        if (source == connection) {
            callback.packetReceived(this, data);
        }
    }
    
}
