package jpiccoli.uwml.remote;

import java.util.ArrayList;

public class HeartBeatManager {
    
	/**
	 * Conjunto de conex�es que s�o monitoradas por este HeartBeatManager
	 */
    private ArrayList<RemoteConnection> connections;
    
    /**
     * Intervalo de envio de mensagens de HeartBeat
     */
    private long heartBeatSendInterval;
    
    /**
     * Tempo m�ximo de inatividade tolerado para cada conex�o gerenciada por este HeartBeatManager
     */
    private long maximumInactiveTime;

    /**
     * Indica se as threads de envio/verifica��o de HeartBeat devem ser encerradas
     */
    private boolean stop;

    /**
     * Thread para envio de mensagens do tipo HeartBeat
     */
    private HeartBeatSenderThread senderThread;
    
    /**
     * Thread que monitora e verifica se as conex�o gerenciadas est�o ativas ou inativas
     */
    private HeartBeatVerifierThread verifierThread;

    /**
     * Construtor-padr�o
     */
    public HeartBeatManager() {
        this(10000, 30000);
    }
    
    /**
     * Cria uma inst�ncia de HeartBeatManager utilizando o intervalo de envio e tempo
     * m�ximo de inatividade especificados
     * @param heartBeatSendInterval Intervalo de tempo entre o envio de uma e outra mensagem de HeartBeat
     * @param maximumInactiveTime Tempo m�ximo tolerado para que uma conex�o permane�a inativa antes de ser finalizada 
     */
    public HeartBeatManager(int heartBeatSendInterval, int maximumInactiveTime) {
        connections = new ArrayList<RemoteConnection>();
        this.heartBeatSendInterval = heartBeatSendInterval;
        this.maximumInactiveTime = maximumInactiveTime;
        senderThread = new HeartBeatSenderThread();
        senderThread.setName("HeartBeat Sender Thread");
        senderThread.setDaemon(true);
        verifierThread = new HeartBeatVerifierThread();
        verifierThread.setName("Connection Status Verifier Thread");
        verifierThread.setDaemon(true);
        senderThread.start();
        verifierThread.start();
    }

    /**
     * Encerra as thread de envio de mensagens HeartBeat e de verifica��o
     * de inatividade
     */
    public void stop() {
        stop = true;
    }

    /**
     * Adiciona uma conex�o ao conjunto de conex�es gerenciadas por este HeartBeatManager
     * @param connection Conex�o que ser� inclu�da no monitoramento 
     */
    public void addConnection(RemoteConnection connection) {
        connections.add(connection);
    }

    /**
     * Remove a conex�o especificada do conjunto de conex�es gerenciadas por este HeartBeatManager
     * @param connection Conex�o que ser� removida do monitoramento 
     */
    public void removeConnection(RemoteConnection connection) {
        connections.remove(connection);
    }

    /**
     * Thread que envia, periodicamente, mensagens de HeartBeat
     * �s conex�es gerenciadas pelo HeartBeatManager
     * @author Juliano
     *
     */
    private class HeartBeatSenderThread extends Thread {
        public void run() {
            while(!stop) {
                try {
                    Thread.sleep(heartBeatSendInterval);
                    for (int i = 0; i < connections.size(); i++) {
                        RemoteConnection connection = connections.get(i);
                        connection.sendHeartBeat();
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Thread que verifica, periodicamente, se as conex�es gerenciadas
     * pelo HeartBeatManager est�o ou n�o inativas
     * @author Juliano
     *
     */
    private class HeartBeatVerifierThread extends Thread {
        public void run() {
            while(!stop) {
                try {
                    Thread.sleep(maximumInactiveTime);
                    for (int i = 0; i < connections.size(); i++) {
                        RemoteConnection connection = connections.get(i);
                        if (!connection.isActive()) {
                            connection.closeCommunication();
                            connections.remove(connection);
                        } else {
                            connection.setActive(false);
                        }
                    }
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }
    
}
