package jpiccoli.uwml.local;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Hashtable;

public class LocalCommunicator implements MulticastInterfaceCallback {

    private Thread mInterfaceThread;
    private MulticastInterface mInterface;

    private InitTimeoutThread initTimeout;
    private HeartBeatSender heartBeatSender;
    private LocalUserStatusVerifier verifier;
    
    /**
     * Lista de hosts existentes no subgrupo
     */
    private Hashtable<InetAddress, LocalNetworkHost> localParticipants;

    /**
     * Objeto que representa o host local
     */
    private LocalNetworkHost thisUser;
    
    /**
     * Gerenciador do subgrupo
     */
    private LocalNetworkHost currentManager;
    
    /**
     * Código identificador do grupo
     */
    private int sessionIdentifier;
    
    /**
     * Indica se o comunicador foi inicializado através do método <code>initialize</code>
     */
    private boolean initialized;
    
    /**
     * Indica se o processo de inicialização foi corretamente finalizado
     * de forma que este LocalCommunicator possa ser utilizado
     */
    private boolean ready;

    /**
     * Callback que receberá os eventos gerados por este LocalCommunicator
     */
    private LocalCommunicatorCallback callback;
    
    /**
     * Construtor padrão
     * @param callback Callback que será notificada dos eventos gerados por este LocalCommunicator
     * @param sessionIdentifier Código identificador de grupo
     */
    public LocalCommunicator(LocalCommunicatorCallback callback, int sessionIdentifier) {
        this.callback = callback;
        this.sessionIdentifier = sessionIdentifier;
        localParticipants = new Hashtable<InetAddress, LocalNetworkHost>();
        thisUser = new ThisNetworkHost(true, LocalNetworkHost.CAN_MANAGE);
        localParticipants.put(thisUser.getAddress(), thisUser);
    }

    /**
     * Define a callback que receberá as notificações dos eventos ocorridos neste LocalCommunicator
     * @param callback Callback que receberá notificações dos eventos ocorridos
     */
    public void setCommunicatorCallback(LocalCommunicatorCallback callback) {
        this.callback = callback;
    }
    
    /**
     * Retorna a tabela de participantes existentes na rede local
     * @return A tabela de participantes existentes na rede local
     */
    public Hashtable<InetAddress, LocalNetworkHost> getLocalParticipants() {
        return localParticipants;
    }

    /**
     * Envia a mensagem especificada através deste LocalCommunicator
     * @param data Buffer contendo a mensagem a ser transmitida
     * @param offset Início da mensagem dentro do buffer
     * @param length Tamanho da mensagem, em bytes
     * @throws IOException Caso ocorra algum erro de E/S ao transmitir a mensagem
     */
    public void send(byte data[], int offset, int length) throws IOException {
        if (ready) {
            mInterface.sendAppMessage(data, offset, length);
        }
    }

    /**
     * Envia a mensagem especificada através deste LocalCommunicator
     * @param sourceBuffer Buffer contendo a mensagem a ser transmitida
     * @throws IOException Caso ocorra algum erro de E/S ao transmitir a mensagem
     */
    public void send(ByteBuffer sourceBuffer) throws IOException {
        if (ready) {
            sourceBuffer.mark();
            mInterface.sendAppMessage(sourceBuffer);
            sourceBuffer.reset();
        }
    }
    
    /**
     * Define se este host pode atuar como Gerenciador de Subgrupo
     * @param canManage True se este host pode atuar como Gerenciador de Subgrupo. Falso caso contrário.
     */
    public synchronized void setCanManage(boolean canManage) {
        try {
            if (ready) {
                if (canManage) {	// Habilita este host a tornar-se um gerenciador de subgrupo
                	// Caso o status de gerenciamento anterior deste host fosse CANNOT_MANAGE,
                	// é necessário realizar alguns procedimentos que o habilitem a tornar-se
                	// um gerenciador de subgrupo
                    if (thisUser.getManagerStatus() == LocalNetworkHost.CANNOT_MANAGE) {
                    	// Define o novo status
                        thisUser.setManagerStatus(LocalNetworkHost.CAN_MANAGE);
                        // Realiza os procedimentos de verificação do novo gerenciador de subgrupo
                        if (!checkSessionManagement()) {
                        	// Se a alteração do status de gerenciamento não promoveu este
                        	// host a gerenciador de subgrupo, notifica a alteração de status
                        	// ao restante do subgrupo
                            mInterface.sendHeartBeatMessage(LocalNetworkHost.CAN_MANAGE);
                        }
                    }
                } else if (thisUser.getManagerStatus() != LocalNetworkHost.CANNOT_MANAGE) { // Impede que este host torne-se um gerenciador de subgrupo
                	// Define o novo status de gerenciamento, caso o status anterior
                	// permitisse a este host tornar-se um gerenciador de subgrupo
                    thisUser.setManagerStatus(LocalNetworkHost.CANNOT_MANAGE);
                    if (!checkSessionManagement()) {
                    	// Se a alteração do status de gerenciamento não promoveu outro
                    	// host a gerenciador de subgrupo, notifica a alteração de status
                    	// ao restante do subgrupo
                        mInterface.sendHeartBeatMessage(LocalNetworkHost.CANNOT_MANAGE);
                    }
                }
            } else {
            	// Simplesmente define o novo status, caso este LocalCommunicator ainda não esteja preparado
                thisUser.setManagerStatus(canManage ? LocalNetworkHost.CAN_MANAGE : LocalNetworkHost.CANNOT_MANAGE);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    /**
     * Desativa definitivamente este LocalCommunicator.
     * Chamadas posteriores a qualquer método neste objeto serão ignoradas.
     */
    public void deactivate() {
        initialized = false;
        if (ready) {
            ready = false;
            mInterface.close();
        }
    }

    /**
     * Inicializa este LocalCommunicator. Este método deve ser chamado antes que mensagens
     * possam ser enviadas para os demais participantes do subgrupo.
     */
    public synchronized void initialize() {
        while (!initialized) {
            reset();	// Limpa todo o estado deste LocalCommunicator
            initialized = true;	// Define a flag de inicialização
            try {
            	// Pára as threads de HeartBeat
                if (heartBeatSender != null) heartBeatSender.stop();
                if (verifier != null) verifier.stop();
                // Cria a interface de comunicação multicast
                createMulticastInterface();
                // Notifica os demais integrantes do subgrupo da entrada deste host
                mInterface.sendJoinRequest(thisUser.getManagerStatus());
                // Inicializa a thread de timeout para o procedimento de inicialização
                initTimeout = new InitTimeoutThread();
                initTimeout.start();
                while(!ready) {
                    try {
                        wait();	// Aguarda o fim da inicialização ou o timeout
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
                // Reinicia as threads de HeartBeat
                heartBeatSender = new HeartBeatSender(this);
                verifier = new LocalUserStatusVerifier(this);
                heartBeatSender.start();
                verifier.start();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                try {
                    wait(5000);	// Em caso de erros de E/S na inicialização, aguarda-se 5 segundos antes da nova tentativa
                } catch (InterruptedException exc) {
                    // TODO Auto-generated catch block
                    exc.printStackTrace();
                }
            }
        }
    }
    
    public void appMessageReceived(InetAddress sourceAddress, ByteBuffer receivedData) {
        if (ready) {
            LocalNetworkHost user = localParticipants.get(sourceAddress);
            if (user != null) user.setActive(true);
            if (callback != null) callback.packetReceived(sourceAddress, receivedData);
        }
    }

    public void dropMessageReceived(InetAddress sourceAddress) {
        userDropped(sourceAddress);
    }

    public synchronized void heartbeatMessageReceived(InetAddress sourceAddress, byte managerStatus) {
        LocalNetworkHost user = localParticipants.get(sourceAddress);
        if (user == null) {
        	// Cria e adiciona o usuário na lista, se ele ainda não estiver presente
            user = new LocalNetworkHost(sourceAddress, true, managerStatus);
            localParticipants.put(sourceAddress, user);
        }
        // Define o status de gerenciamento do usuário e o marca como ativo para a thread de HeartBeat
        user.setActive(true);
        user.setManagerStatus(managerStatus);
        if (ready) {
        	// Realiza os procedimentos de verificação do gerenciador de subgrupo
        	checkSessionManagement();
        } else if (initialized && !ready) {
        	// O procedimento de inicialização ainda está em andamento...
            if (managerStatus == LocalNetworkHost.IS_MANAGER) {
            	// Finaliza o procedimento de inicialização se um gerenciador de subgrupo foi localizado
                finishInitialize();
            }
        }
    }

    public synchronized void interfaceClosed(boolean byApp) {
        initialized = false;
        ready = false;
        if (!byApp) initialize();
    }

    public synchronized void joinRequestReceived(InetAddress sourceAddress, byte managerStatus) {
        try {
            LocalNetworkHost user = localParticipants.get(sourceAddress);
            if (user == null) {
            	// Cria e adiciona o usuário na lista, se ele ainda não estiver presente
                user = new LocalNetworkHost(sourceAddress, true, managerStatus);
                localParticipants.put(sourceAddress, user);
            } else {
            	// Define o status de gerenciamento do usuário e o marca como ativo para a thread de HeartBeat
                user.setActive(true);
                user.setManagerStatus(managerStatus);
            }
            // Realiza os procedimentos de verificação do gerenciador de subgrupo
            checkSessionManagement();
            // Envia a resposta JOIN_RESPONSE ao host que originou a mensagem JOIN_REQUEST
            mInterface.sendJoinResponse(sourceAddress, thisUser.getManagerStatus());
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public synchronized void joinResponseReceived(InetAddress sourceAddress, byte managerStatus) {
        LocalNetworkHost user = localParticipants.get(sourceAddress);
        if (user == null) {
        	// Cria e adiciona o usuário na lista, se ele ainda não estiver presente
            user = new LocalNetworkHost(sourceAddress, true, managerStatus);
            localParticipants.put(sourceAddress, user);
        } else {
        	// Define o status de gerenciamento do usuário e o marca como ativo para a thread de HeartBeat
            user.setActive(true);
            user.setManagerStatus(managerStatus);
        }
        if (ready) {
        	// Realiza os procedimentos de verificação do gerenciador de subgrupo
            checkSessionManagement();
        } else if (initialized && !ready) {
        	// O procedimento de inicialização ainda está em andamento...
            if (managerStatus == LocalNetworkHost.IS_MANAGER) {
            	// Finaliza o procedimento de inicialização se um gerenciador de subgrupo foi localizado
                finishInitialize();
            }
        }
    }

    /**
     * Realiza todos os procedimentos que devem ser executados quando um participante
     * deixa o subgrupo
     * @param sourceAddress Endereço do participante que deixou o subgrupo
     */
    protected synchronized void userDropped(InetAddress sourceAddress) {
        if (localParticipants.remove(sourceAddress) != null) {
        	// Caso o host removido seja o gerenciador de subgrupo, procura
        	// pelo novo gerenciador
            checkSessionManagement();
        }
    }
    
    /**
     * Envia uma mensagem do tipo HEARTBEAT_MSG
     * @throws IOException Caso ocorram erros de E/S ao enviar a mensagem
     */
    protected synchronized void sendHeartBeatMessage() throws IOException {
        thisUser.setActive(true);
        if (ready) {
            mInterface.sendHeartBeatMessage(thisUser.getManagerStatus());
        }
    }
    
    /**
     * Reseta o estado deste LocalCommunicator, eliminando todo o conteúdo da tabela de participantes
     * e a referência ao Gerenciador de Subgrupo
     *
     */
    private void reset() {
        currentManager = null;
        if (thisUser.getManagerStatus() == LocalNetworkHost.IS_MANAGER || thisUser.getManagerStatus() == LocalNetworkHost.CAN_MANAGE) {
            thisUser.setManagerStatus(LocalNetworkHost.CAN_MANAGE);
        } else {
            thisUser.setManagerStatus(LocalNetworkHost.CANNOT_MANAGE);
        }
        localParticipants.clear();
        localParticipants.put(thisUser.getAddress(), thisUser);
    }
    
    /**
     * Cria o objeto MulticastInterface que será utilizado para trocar mensagens
     * com o restante do subgrupo
     * @throws IOException Caso ocorram erros de E/S ao instanciar o objeto MulticastInterface
     */
    private void createMulticastInterface() throws IOException {
    	// Define o endereço da sessão multicast baseado no código identificador de grupo
        byte thirdOctet = (byte) (sessionIdentifier & 0xFF00);
        byte fourthOctet = (byte) (sessionIdentifier & 0xFF);
        if (thirdOctet <= 0) thirdOctet = 1;
        if (fourthOctet <= 0) fourthOctet = 1;
        String multicastAddress = "224.1." + thirdOctet + "." + fourthOctet;
        // Cria a interface de comunicação multicast e inicializa a thread que realiza o procedimento
        // de recepção e processamento de pacotes
        mInterface = new MulticastInterface(this, InetAddress.getByName(multicastAddress), 5555, sessionIdentifier);
        mInterfaceThread = new Thread(mInterface);
        mInterfaceThread.setDaemon(true);
        mInterfaceThread.setName("Multicast Interface Thread");
        mInterfaceThread.start();
    }
    
    /**
     * Finaliza o processo de inicialização
     */
    private synchronized void finishInitialize() {
        if (!ready) {
            if (initialized) {
            	// Define este LocalCommunicator como preparado
                ready = true;
                // Realiza os procedimentos de verificação do gerenciador de subgrupo
                checkSessionManagement();
                // Encerra a thread de timeout
                if (initTimeout != null) initTimeout.finish();
                // Notifica a thread que está bloqueada no método initialize
                notify();
            }
        }
    }

    /**
     * Realiza todas as verificações necessárias para determinar se o Gerenciador de
     * Subgrupo foi alterado e quem deve ser selecionado como o novo Gerenciador. Caso
     * este host seja eleito o novo Gerenciador de Subgrupo, este método realiza as notificações
     * necessárias para as camadas superiores da aplicação.
     * @return True se este host tornou-se ou deixou de ser o gerenciador de subgrupo
     */
    private boolean checkSessionManagement() {
        if (ready) {
            LocalNetworkHost manager = null;
            Enumeration<InetAddress> keys = localParticipants.keys();
            // Verifica se existe mais de um host marcado como Gerenciador de Subgrupo e resolve o conflito
            while(keys.hasMoreElements()) {
                InetAddress key = keys.nextElement();
                LocalNetworkHost user = localParticipants.get(key);
                if (user.getManagerStatus() == LocalNetworkHost.IS_MANAGER) {
                    if (manager == null) {
                        manager = user;
                    } else if (manager.getAddress().getHostAddress().compareTo(user.getAddress().getHostAddress()) < 0) {
                        manager.setManagerStatus(LocalNetworkHost.CAN_MANAGE);
                        manager = user;
                    } else {
                        user.setManagerStatus(LocalNetworkHost.CAN_MANAGE);
                    }
                }
            }
            // Se nenhum dos hosts verificados foi definido como o gerenciador de subgrupo...
            if (manager == null) {
                keys = localParticipants.keys();
                // Procura os participantes marcados como possíveis gerenciadores de subgrupo e define
                // quem dentre eles será o novo gerenciador aplicando o critério de seleção
                while(keys.hasMoreElements()) {
                    InetAddress key = keys.nextElement();
                    LocalNetworkHost user = localParticipants.get(key);
                    if (user.getManagerStatus() == LocalNetworkHost.CAN_MANAGE) {
                        if (manager == null || manager.getAddress().getHostAddress().compareTo(user.getAddress().getHostAddress()) < 0) {
                            manager = user;
                        }
                    }
                }
            }
            // Realiza os procedimentos necessários para proceder com a mudança de gerenciador de subgrupo
            return processSessionManager(manager);
        }
        return false;
    }
    
    /**
     * Analisa o Host selecionado como Gerenciador de Subgrupo e realiza os procedimentos
     * necessários 
     * @param manager Host selecionado como Gerenciador de Subgrupo segundos os critérios
     * estabelecidos e implementados no método <code>checkSessionManagement</code>
     * @return True se este host tornou-se ou deixou de ser o gerenciador de subgrupo
     */
    private boolean processSessionManager(LocalNetworkHost manager) {
        try {
            if (manager != currentManager) {	// O gerenciador foi modificado ?
                if (manager != null) {	// Existe um gerenciador para este subgrupo ?
                    manager.setManagerStatus(LocalNetworkHost.IS_MANAGER);
                    if (currentManager == thisUser) {	// Este host está deixando de ser o gerenciador de subgrupo
                    	// Define um novo status de gerenciamento, caso ainda esteja definido como IS_MANAGER
                    	if (thisUser.getManagerStatus() == LocalNetworkHost.IS_MANAGER)
                    		thisUser.setManagerStatus(LocalNetworkHost.CAN_MANAGE);
                    	// Notifica a alteração ao restante do subgrupo
                        mInterface.sendHeartBeatMessage(thisUser.getManagerStatus());
                        // Notifica a aplicação
                        callback.newManager(manager);
                        return true;
                    } else if (manager == thisUser) {	// Este host é o novo gerenciador de subgrupo
                    	// Define o novo status
                        thisUser.setManagerStatus(LocalNetworkHost.IS_MANAGER);
                        // Notifica a alteração ao restante do subgrupo
                        mInterface.sendHeartBeatMessage(LocalNetworkHost.IS_MANAGER);
                        // Notifica a aplicação
                        callback.becomeManager();
                        return true;
                    } else {	// A alteração do gerenciador de subgrupo não envolve este host
                    	// Notifica a aplicação
                        callback.newManager(manager);
                    }
                } else {	// Não há gerenciador de subgrupo
                	// Notifica a aplicação
                    callback.lostManager();
                }
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } finally {
        	// Define o novo gerenciador de subgrupo
            currentManager = manager;
        }
        return false;
    }
    
    /**
     * Thread que força a finalização do processo de inicialização
     * deste LocalCommunicator dentro de um determinado timeout
     * @author Juliano
     *
     */
    private class InitTimeoutThread extends Thread {
        private boolean stop;
        private synchronized void finish() {
            stop = true;
            notify();
        }
        public void run() {
            synchronized(this) {
                try {
                    wait(10000);	// Aguarda dez segundos para forçar a finalização do procedimento de inicialização
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            synchronized(LocalCommunicator.this) {
                if (!stop) {
                    if (thisUser.getManagerStatus() != LocalNetworkHost.CANNOT_MANAGE) {
                    	// Se este host não estiver impedido de se tornar o gerenciador de subgrupo,
                    	// promove-o a gerenciador
                        thisUser.setManagerStatus(LocalNetworkHost.IS_MANAGER);
                    }
                }
                // Finaliza o procedimento de inicialização
                finishInitialize();
            }
        } 
    }
    
}
