package jpiccoli.uwml.local;

import java.net.InetAddress;
import java.nio.ByteBuffer;

public interface MulticastInterfaceCallback {
    
	/**
	 * Chamado quando uma mensagem JOIN_REQUEST foi recebida
	 * @param sourceAddress Endereço do remetente da mensagem
	 * @param managerStatus Status de Gerenciamento do remetente
	 */
    public void joinRequestReceived(InetAddress sourceAddress, byte managerStatus);
    
    /**
     * Chamado quando uma mensagem JOIN_RESPONSE foi recebida
	 * @param sourceAddress Endereço do remetente da mensagem
	 * @param managerStatus Status de Gerenciamento do remetente
     */
    public void joinResponseReceived(InetAddress sourceAddress, byte managerStatus);
    
    /**
     * Chamado quando uma mensagem DROP_MESSAGE foi recebida 
	 * @param sourceAddress Endereço do remetente da mensagem
     */
    public void dropMessageReceived(InetAddress sourceAddress);
    
    /**
     * Chamado quando uma mensagem HEART_BEAT_MESSAGE foi recebida
	 * @param sourceAddress Endereço do remetente da mensagem
	 * @param managerStatus Status de Gerenciamento do remetente
     */
    public void heartbeatMessageReceived(InetAddress sourceAddress, byte managerStatus);
    
    /**
     * Chamado quando uma mensagem com carga da aplicação foi recebida
	 * @param sourceAddress Endereço do remetente da mensagem
     * @param receivedData Carga útil da mensagem recebida
     */
    public void appMessageReceived(InetAddress sourceAddress, ByteBuffer receivedData);
    
    /**
     * Chamado quando a interface multicast foi finalizada
     * @param byApp True se a finalização foi realizada em resposta a uma ordem da aplicação
     */
    public void interfaceClosed(boolean byApp);
    
}
