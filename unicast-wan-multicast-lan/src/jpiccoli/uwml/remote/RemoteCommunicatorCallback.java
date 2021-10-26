package jpiccoli.uwml.remote;

import java.nio.ByteBuffer;

public interface RemoteCommunicatorCallback {
    
	/**
	 * Notifica a callback de que um pacote foi recebido
	 * @param source RemoteCommunicator através do qual o pacote foi recebido
	 * @param data Carga do pacote recebido
	 */
    public void packetReceived(RemoteCommunicator source, ByteBuffer data);
    
    /**
     * Notifica a callback de que a conexão representada por source foi perdida
     * @param source RemoteCommunicator que perdeu sua conexão
     */
    public void connectionLost(RemoteCommunicator source);
    
    /**
     * Notifica a callback de que a conexão representada por source foi inicializada com sucesso
     * @param source RemoteCommunicator que inicializou sua conexão
     */
    public void connectionInitialized(RemoteCommunicator source);
    
    /**
     * Notifica a callback de que a conexão representada por source foi ativada/desativada
     * @param source RemoteCommunicator cujo estado de ativação foi alterado
     */
    public void connectionStatusChanged(RemoteCommunicator source);
    
}
