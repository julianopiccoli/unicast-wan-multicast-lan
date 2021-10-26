package jpiccoli.uwml.local;

import java.net.InetAddress;

public class LocalNetworkHost {
    
    public static final byte CANNOT_MANAGE = 0;
    public static final byte CAN_MANAGE = 1;
    public static final byte IS_MANAGER = 2;
    
    /**
     * Endereço IP deste host
     */
    private InetAddress address;
    
    /**
     * Flag indicando se este host está ativo ou ocioso
     */
    private boolean active;
    
    /**
     * Parâmetro Status de Gerenciamento deste host
     */
    private byte managerStatus;

    /**
     * Construtor padrão.
     * @param address Endereço IP deste host
     * @param active Flag indicando se este host está ativo ou ocioso
     * @param managerStatus Parâmetro Status de Gerenciamento deste host
     */
    public LocalNetworkHost(InetAddress address, boolean active, byte managerStatus) {
        this.address = address;
        this.active = active;
        this.managerStatus = managerStatus;
    }

    /**
     * Indica se o host está ativo ou não. Utilizado pela thread de verificação
     * de HeartBeat para definir se o host deve ser removido por inatividade.
     * @return True se o host está ativo.
     */
    public boolean isActive() {
        return active;
    }
    
    /**
     * Define se o host está ativo ou não.
     * @see isActive
     * @param active True se o host está ativo.
     */
    public void setActive(boolean active) {
        this.active = active;
    }
    
    /**
     * Retorna o endereço IP deste host
     * @return O endereço IP deste host
     */
    public InetAddress getAddress() {
        return address;
    }
    
    /**
     * Define o endereço IP deste host.
     * @param address O endereço IP deste host.
     */
    public void setAddress(InetAddress address) {
        this.address = address;
    }
    
    /**
     * Retorna o parâmetro Status de Gerenciamento deste host
     * @return O parâmetro Status de Gerenciamento deste host
     */
    public byte getManagerStatus() {
        return managerStatus;
    }
    
    /**
     * Define o parâmetro Status de Gerenciamento deste host
     * @param managerStatus O parâmetro Status de Gerenciamento deste host
     */
    public void setManagerStatus(byte managerStatus) {
        this.managerStatus = managerStatus;
    }
    
}
