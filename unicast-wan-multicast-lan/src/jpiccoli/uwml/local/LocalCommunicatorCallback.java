package jpiccoli.uwml.local;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public interface LocalCommunicatorCallback {
    
	/**
	 * Chamado pelo LocalCommunicator quando um pacote da aplicação é recebido da rede
	 * @param sourceAddress Endereço do remetente do pacote
	 * @param receivedData Carga útil do pacote recebido
	 */
    public void packetReceived(InetAddress sourceAddress, ByteBuffer receivedData);
    
    /**
     * Notifica a aplicação de que o Gerenciador de Subgrupo foi perdido
     */
    public void lostManager();
    
    /**
     * Notifica a aplicação que o Gerenciador de Subgrupo foi alterado
     * @param user
     */
    public void newManager(LocalNetworkHost user);
    
    /**
     * Notifica a aplicação de que este host foi promovido a Gerenciador de Subgrupo
     */
    public void becomeManager();
    
}
