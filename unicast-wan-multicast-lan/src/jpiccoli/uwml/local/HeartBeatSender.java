package jpiccoli.uwml.local;

public class HeartBeatSender implements Runnable {

	/**
	 * Intervalo padrão de envio de mensagens HeartBeat
	 */
	private static long defaultSendInterval = 10000;
	
	/**
	 * LocalCommunicator através do qual serão enviadas as mensagens de HeartBeat
	 */
    private LocalCommunicator localCommunicator;
    
    /**
     * Intervalo de envio de mensagens HeartBeat
     */
	private long sendInterval;
	
    private Thread thread;
    private boolean stop;

    /**
     * Define o intervalo padrão de envio de mensagens HeartBeat
     * @param newDefaultSendInterval Tempo, em milissegundos, de intervalo entre o envio de mensagens HeartBeat
     */
    public static void setDefaultSendInterval(long newDefaultSendInterval) {
    	defaultSendInterval = newDefaultSendInterval;
    }
    
    public HeartBeatSender(LocalCommunicator communicator) {
        this.localCommunicator = communicator;
        sendInterval = defaultSendInterval;
    }
    
    /**
     * Define o intervalo de envio de mensagens HeartBeat
     * @param checkInterval Tempo, em milissegundos, de intervalo entre o envio de mensagens HeartBeat
     */
    public void setSendInterval(long sendInterval) {
    	this.sendInterval = sendInterval;
    }
    
    /**
     * Retorna o intervalo de envio de mensagens HeartBeat, em milissegundos
     * @return O intervalo de envio de mensagens HeartBeat, em milissegundos
     */
    public long getSendInterval() {
    	return sendInterval;
    }
    
    /**
     * Inicializa a thread de envio de HeartBeat
     */
    public void start() {
        thread = new Thread(this);
        thread.setDaemon(true);
        thread.setName("Local HeartBeat Sender Thread");
        thread.start();
    }
    
    /**
     * Encerra a thread de envio de HeartBeat
     */
    public void stop() {
        stop = true;
    }
    
    public void run() {
        while(!stop) {
            try {
                Thread.sleep(sendInterval);
                localCommunicator.sendHeartBeatMessage();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }
    
}
