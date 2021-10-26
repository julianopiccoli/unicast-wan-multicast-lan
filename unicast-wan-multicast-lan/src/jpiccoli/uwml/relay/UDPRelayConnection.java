package jpiccoli.uwml.relay;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import jpiccoli.uwml.remote.RemoteCommunicator;
import jpiccoli.uwml.util.LimitedList;

public class UDPRelayConnection extends RelayRemoteConnection {

    public static final short ACK_MSG    = 3;
    
    private static Timer timer;
    
    private SocketAddress remoteAddress;
    private DatagramChannel datagramChannel;
    private long ctrlPacketCount;
    
    private ArrayList<AckTimeoutTimer> ackTimers;
    private LimitedList<CtrlMessage> ctrlMessages;
    
    private ByteBuffer outputBuffer;

    static {
        timer = new Timer("UDP Relay Connection Ack Timeout Timer");
    }
    
    public UDPRelayConnection(IOThread ioThread, RemoteCommunicator callback, DatagramChannel channel, SocketAddress remoteAdress) {
        this(ioThread, callback, channel, remoteAdress, 1024, 1024);
    }
    
    public UDPRelayConnection(IOThread ioThread, RemoteCommunicator callback, DatagramChannel channel, SocketAddress remoteAdress, int inputBufferSize, int outputBufferSize) {
        super(ioThread, callback, inputBufferSize, outputBufferSize);
        this.remoteAddress = remoteAdress;
        this.datagramChannel = channel;
        ackTimers = new ArrayList<AckTimeoutTimer>();
        ctrlMessages = new LimitedList<CtrlMessage>();
        outputBuffer = ByteBuffer.allocateDirect(outputBufferSize + 10);
        ioThread.changeUDPInputBufferSize(outputBufferSize + 10);
        heartBeat.addConnection(this);
    }
    
    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }
    
    @Override
    public void openCommunication() {
        // Não implementar...
    }
    
    @Override
    public void closeCommunication() {
        heartBeat.removeConnection(this);
        ioThread.udpConnectionClosed(callback, this);
    }

    @Override
    public synchronized void sendCtrlMessage(ByteBuffer data) throws IOException {
        long packetID = 0;
        packetID = ctrlPacketCount++;
        AckTimeoutTimer ack = new AckTimeoutTimer();
        timer.schedule(ack, 5000);
        ack.packetID = packetID;
        ackTimers.add(ack);
        outputBuffer.clear();
        outputBuffer.putShort(CTRL_MSG);
        outputBuffer.putLong(packetID);
        outputBuffer.put(data);
        outputBuffer.flip();
        datagramChannel.send(outputBuffer, remoteAddress);
    }
    
    @Override
    public synchronized void sendMessage(ByteBuffer data) throws IOException {
        outputBuffer.clear();
        outputBuffer.putShort(NORMAL_MSG);
        outputBuffer.put(data);
        outputBuffer.flip();
        datagramChannel.send(outputBuffer, remoteAddress);
    }

    @Override
    public synchronized void sendHeartBeat() throws IOException {
        outputBuffer.clear();
        outputBuffer.putShort(HEARTBEAT_MSG);
        outputBuffer.flip();
        datagramChannel.send(outputBuffer, remoteAddress);
    }
    
    @Override    
    public void receiveData() throws IOException {
        setActive(true);
        ByteBuffer inputBuffer = ioThread.getUDPInputBuffer();
        short msgType = inputBuffer.getShort();
        if (msgType == NORMAL_MSG) {
            callback.packetReceived(this, inputBuffer);
        } else if (msgType == CTRL_MSG) {
            long msgCode = inputBuffer.getLong();
            CtrlMessage ctrlMessage = getControlMessage(msgCode);
            sendAck(msgCode);
            if (ctrlMessage != null) {
                if (!ctrlMessage.alreadyProcessed) {
                    ctrlMessage.alreadyProcessed = true;
                    callback.ctrlPacketReceived(this, inputBuffer);
                }
            } else {
                ctrlMessage = new CtrlMessage(msgCode);
                ctrlMessage.alreadyProcessed = true;
                ctrlMessages.add(ctrlMessage);
                callback.ctrlPacketReceived(this, inputBuffer);
            }
        } else if (msgType == HEARTBEAT_MSG) {
            // Ignore...
        } else if (msgType == ACK_MSG) {
            if (inputBuffer.remaining() == 8) {
                AckTimeoutTimer ack = getAck(inputBuffer.getLong());
                if (ack != null) {
                    ack.cancel();
                    ackTimers.remove(ack);
                }
            }
        } else {
            System.out.println("Unknown msg");
        }
    }
    
    private synchronized void sendAck(long ackCode) {
        try {
            outputBuffer.clear();
            outputBuffer.putShort(ACK_MSG);
            outputBuffer.putLong(ackCode);
            outputBuffer.flip();
            datagramChannel.send(outputBuffer, remoteAddress);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    private CtrlMessage getControlMessage(long msgCode) {
        for (int i = 0; i < ctrlMessages.size(); i++) {
            CtrlMessage ctrlMessage = ctrlMessages.get(i);
            if (ctrlMessage.msgCode == msgCode) {
                return ctrlMessage;
            }
        }
        return null;
    }
    
    private synchronized AckTimeoutTimer getAck(long ackCode) {
        for (AckTimeoutTimer ack : ackTimers) {
            if (ack.packetID == ackCode) {
                return ack;
            }
        }
        return null;
    }
    
    private class CtrlMessage {
        private long msgCode;
        private boolean alreadyProcessed;
        private CtrlMessage(long msgCode) {
            this.msgCode = msgCode;
            alreadyProcessed = false;
        }
    }
    
    private class AckTimeoutTimer extends TimerTask {
        private long packetID;
        public void run() {
            callback.close();
        }
    }
    
}
