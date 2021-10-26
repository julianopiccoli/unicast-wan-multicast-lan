package jpiccoli.uwml.local;

import java.net.InetAddress;
import java.util.Enumeration;
import java.util.Hashtable;

public class LocalUserStatusVerifier implements Runnable {

	/**
	 * Intervalo padrão de verificação
	 */
	private static long defaultCheckInterval = 30000; 
	
	/**
	 * LocalCommunicator onde será realizada a verificação
	 */
    private LocalCommunicator localCommunicator;
    
    /**
     * Intervalo entre uma verificação e outra
     */
    private long checkInterval;
    private Thread thread;
    private boolean stop;
    
    /**
     * Define o intervalo padrão de verificação
     * @param newDefaultCheckInterval Tempo, em milissegundos, de intervalo entre uma verificação e outra
     */
    public static void setDefaultCheckInterval(long newDefaultCheckInterval) {
    	defaultCheckInterval = newDefaultCheckInterval;
    }
    
    /**
     * Construtor padrão
     * @param communicator LocalCommunicator onde será realizada a verificação
     */
    public LocalUserStatusVerifier(LocalCommunicator communicator) {
        this.localCommunicator = communicator;
        checkInterval = defaultCheckInterval;
    }
    
    /**
     * Define o intervalo de verificação
     * @param checkInterval Tempo, em milissegundos, de intervalo entre uma verificação e outra
     */
    public void setCheckInterval(long checkInterval) {
    	this.checkInterval = checkInterval;
    }
    
    /**
     * Retorna o intervalo de verificação, em milissegundos
     * @return O intervalo de verificação, em milissegundos
     */
    public long getCheckInterval() {
    	return checkInterval;
    }
    
    /**
     * Inicializa a thread de verificação
     */
    public void start() {
        thread = new Thread(this);
        thread.setDaemon(true);
        thread.setName("Local HeartBeat Sender Thread");
        thread.start();
    }

    /**
     * Encerra a thread de verificação
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
