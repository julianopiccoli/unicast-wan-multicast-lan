package jpiccoli.uwml.local;

import java.net.InetAddress;

public class LocalNetworkHost {
    
    public static final byte CANNOT_MANAGE = 0;
    public static final byte CAN_MANAGE = 1;
    public static final byte IS_MANAGER = 2;
    
    /**
     * Endere�o IP deste host
     */
    private InetAddress address;
    
    /**
     * Flag indicando se este host est� ativo ou ocioso
     */
    private boolean active;
    
    /**
     * Par�metro Status de Gerenciamento deste host
     */
    private byte managerStatus;

    /**
     * Construtor padr�o.
     * @param address Endere�o IP deste host
     * @param active Flag indicando se este host est� ativo ou ocioso
     * @param managerStatus Par�metro Status de Gerenciamento deste host
     */
    public LocalNetworkHost(InetAddress address, boolean active, byte managerStatus) {
        this.address = address;
        this.active = active;
        this.managerStatus = managerStatus;
    }

    /**
     * Indica se o host est� ativo ou n�o. Utilizado pela thread de verifica��o
     * de HeartBeat para definir se o host deve ser removido por inatividade.
     * @return True se o host est� ativo.
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Define se o host est� ativo ou n�o.
     * @see isActive
     * @param active True se o host est� ativo.
     */
    public void setActive(boolean active) {
        this.active = active;
    }
    
    /**
     * Retorna o endere�o IP deste host
     * @return O endere�o IP deste host
     */
    public InetAddress getAddress() {
        return address;
    }
    
    /**
     * Define o endere�o IP deste host.
     * @param address O endere�o IP deste host.
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }
    
    /**
     * Retorna o par�metro Status de Gerenciamento deste host
     * @return O par�metro Status de Gerenciamento deste host
     */
    public byte getManagerStatus() {
        return managerStatus;
    }
    
    /**
     * Define o par�metro Status de Gerenciamento deste host
     * @param managerStatus O par�metro Status de Gerenciamento deste host
     */
    public void setManagerStatus(byte managerStatus) {
        this.managerStatus = managerStatus;
    }
    
}
