package jpiccoli.uwml.remote;

import java.io.IOException;
import java.nio.ByteBuffer;

public abstract class RemoteConnection {
    
    public static final short CTRL_MSG   = 0;
    public static final short NORMAL_MSG = 1;
    public static final short HEARTBEAT_MSG = 2;
    
    protected static HeartBeatManager heartBeat;
    
    /**
     * Callback que recebe notificações para os eventos que ocorrem nesta RemoteConnection
     */
    protected RemoteCommunicator callback;
    
    /**
     * Tamanho do buffer de entrada
     */
    protected int inputBufferSize;
    
    /**
     * Tamanho do buffer de saída
     */
    protected int outputBufferSize;
    
    /**
     * Flag que indica se esta RemoteConnection está ativa ou ociosa
     */
    private boolean active;

    static {
        heartBeat = new HeartBeatManager();
    }
    
    /**
     * Construtor secundário. Inicializa este RemoteConnection utilizando buffers de entrada e saída
     * de 1024 bytes cada um.
     * @param callback Callback que receberá os eventos gerados por esta RemoteConnection.
     */
    public RemoteConnection(RemoteCommunicator callback) {
        this(callback, 1024, 1024);
    }
    
    /**
     * Construtor principal.
     * @param callback Callback que receberá os eventos gerados por esta RemoteConnection.
     * @param inputBufferSize Tamanho do buffer de entrada utilizado
     * @param outputBufferSize Tamanho do buffer de saída utilizado
     */
    public RemoteConnection(RemoteCommunicator callback, int inputBufferSize, int outputBufferSize) {
        this.callback = callback;
        this.inputBufferSize = inputBufferSize;
        this.outputBufferSize = outputBufferSize;
        active = true;
    }
    
    /**
     * Retorna a callback definida nesta RemoteConnection
     * @return A callback definida nesta RemoteConnection
     */
    public RemoteCommunicator getCallback() {
        return callback;
    }
    
    /**
     * Retorna o tamanho do buffer de entrada
     * @return O tamanho do buffer de entrada
     */
    public int getInputBufferSize() {
        return inputBufferSize;
    }

    /**
     * Retorna o tamanho do buffer de saída
     * @return O tamanho do buffer de saída
     */
    public int getOutputBufferSize() {
        return outputBufferSize;
    }

    /**
     * Define se esta RemoteConnection está ativa ou ociosa
     * @param active True se esta RemoteConnection deve ser considerada ativa.
     * False caso deva ser considerada ociosa.
     */
    protected void setActive(boolean active) {
        this.active = active;
    }
    
    /**
     * Indica se esta RemoteConnection é considerada ativa ou ociosa.
     * @return True se esta RemoteConnection é considerada ativa.
     * False caso seja considerada ociosa.
     */
    protected boolean isActive() {
        return active;
    }
    
    /**
     * Abre a inicializa a comunicação com o host remoto
     * @throws IOException Caso ocorram erros de E/S
     */
    public abstract void openCommunication() throws IOException;
    
    /**
     * Encerra a comunicação com o host remoto e libera os recursos alocados
     */
    public abstract void closeCommunication();
    
    /**
     * Envia a mensagem especificada sem garantias de entrega
     * @param data Conteúdo da mensagem a ser transmitida
     * @throws IOException Caso ocorram erros de E/S
     */
    public abstract void sendMessage(ByteBuffer data) throws IOException;
    
    /**
     * Envia a mensagem especificada de forma a garantir a entrega da mesma
     * @param data Conteúdo da mensagem a ser transmitida
     * @throws IOException Caso ocorram erros de E/S
     */
    public abstract void sendCtrlMessage(ByteBuffer data) throws IOException;
    
    /**
     * Envia uma mensagem do tipo HeartBeat
     * @throws IOException Caso ocorram erros de E/S
     */
    public abstract void sendHeartBeat() throws IOException;
    
}

