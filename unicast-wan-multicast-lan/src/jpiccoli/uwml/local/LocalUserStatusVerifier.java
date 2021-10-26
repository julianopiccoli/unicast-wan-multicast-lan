package jpiccoli.uwml.local;

import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Hashtable;

public class LocalUserStatusVerifier implements Runnable {

	/**
	 * Intervalo padr�o de verifica��o
	 */
	private static long defaultCheckInterval = 30000; 
	
	/**
	 * LocalCommunicator onde ser� realizada a verifica��o
	 */
    private LocalCommunicator localCommunicator;
    
    /**
     * Intervalo entre uma verifica��o e outra
     */
    private long checkInterval;
    private Thread thread;
    private boolean stop;
    
    /**
     * Define o intervalo padr�o de verifica��o
     * @param newDefaultCheckInterval Tempo, em milissegundos, de intervalo entre uma verifica��o e outra
     */
    public static void setDefaultCheckInterval(long newDefaultCheckInterval) {
    	defaultCheckInterval = newDefaultCheckInterval;
    }
    
    /**
     * Construtor padr�o
     * @param communicator LocalCommunicator onde ser� realizada a verifica��o
     */
    public LocalUserStatusVerifier(LocalCommunicator communicator) {
        this.localCommunicator = communicator;
        checkInterval = defaultCheckInterval;
    }
    
    /**
     * Define o intervalo de verifica��o
     * @param checkInterval Tempo, em milissegundos, de intervalo entre uma verifica��o e outra
     */
    public void setCheckInterval(long checkInterval) {
    	this.checkInterval = checkInterval;
    }
    
    /**
     * Retorna o intervalo de verifica��o, em milissegundos
     * @return O intervalo de verifica��o, em milissegundos
     */
    public long getCheckInterval() {
    	return checkInterval;
    }
    
    /**
     * Inicializa a thread de verifica��o
     */
    public void start() {
        thread = new Thread(this);
        thread.setDaemon(true);
        thread.setName("Local HeartBeat Sender Thread");
        thread.start();
    }

    /**
     * Encerra a thread de verifica��o
     */
    public void stop() {
        stop = true;
    }
    
    public void run() {
        while(!stop) {
            try {
                Thread.sleep(checkInterval);
                Hashtable<InetAddress, LocalNetworkHost> localParticipants = localCommunicator.getLocalParticipants();
                Enumeration<LocalNetworkHost> localUsersEnum = localParticipants.elements();
                while(localUsersEnum.hasMoreElements()) {
                    LocalNetworkHost user = localUsersEnum.nextElement();
                    if (!user.isActive()) {
                        localCommunicator.userDropped(user.getAddress());
                    } else {
                        user.setActive(false);
                    }
                }
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
}
