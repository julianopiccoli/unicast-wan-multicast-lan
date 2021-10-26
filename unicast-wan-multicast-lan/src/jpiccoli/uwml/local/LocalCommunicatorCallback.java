package jpiccoli.uwml.local;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public interface LocalCommunicatorCallback {
    
	/**
	 * Chamado pelo LocalCommunicator quando um pacote da aplica��o � recebido da rede
	 * @param sourceAddress Endere�o do remetente do pacote
	 * @param receivedData Carga �til do pacote recebido
	 */
    public void packetReceived(InetAddress sourceAddress, ByteBuffer receivedData);
    
    /**
     * Notifica a aplica��o de que o Gerenciador de Subgrupo foi perdido
     */
    public void lostManager();
    
    /**
     * Notifica a aplica��o que o Gerenciador de Subgrupo foi alterado
     * @param user
     */
    public void newManager(LocalNetworkHost user);
    
    /**
     * Notifica a aplica��o de que este host foi promovido a Gerenciador de Subgrupo
     */
    public void becomeManager();
    
}
