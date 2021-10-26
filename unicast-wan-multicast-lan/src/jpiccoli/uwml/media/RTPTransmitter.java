package jpiccoli.uwml.media;

import java.io.IOException;
import javax.media.protocol.PushSourceStream;
import javax.media.rtp.OutputDataStream;
import javax.media.rtp.RTPConnector;

public class RTPTransmitter implements RTPConnector {

    private byte dataKey;
    private byte ctrlKey;
    
    private RTPInput dataInput;
    private RTPInput ctrlInput;
    
    private RTPOutput dataOutput;
    private RTPOutput ctrlOutput;
    
    private RTPDemux demux;
    
    public RTPTransmitter(RTPDemux demux, byte dataKey, byte ctrlKey) {
        this.demux = demux;
        this.dataKey = dataKey;
        this.ctrlKey = ctrlKey;
        dataInput = new RTPInput();
        ctrlInput = new RTPInput();
        dataOutput = new RTPOutput(demux, dataKey);
        ctrlOutput = new RTPOutput(demux, ctrlKey);
        demux.addInput(dataKey, dataInput);
        demux.addInput(ctrlKey, ctrlInput);
    }
    
    public void close() {
        demux.removeInput(dataKey);
        demux.removeInput(ctrlKey);
    }

    public PushSourceStream getControlInputStream() throws IOException {
        return ctrlInput;
    }

    public OutputDataStream getControlOutputStream() throws IOException {
        return ctrlOutput;
    }

    public PushSourceStream getDataInputStream() throws IOException {
        return dataInput;
    }

    public OutputDataStream getDataOutputStream() throws IOException {
        return dataOutput;
    }

    public double getRTCPBandwidthFraction() {
        return -1;
    }

    public double getRTCPSenderBandwidthFraction() {
        return -1;
    }

    public int getReceiveBufferSize() {
        return -1;
    }

    public int getSendBufferSize() {
        return -1;
    }

    public void setReceiveBufferSize(int size) throws IOException {
        //
    }

    public void setSendBufferSize(int size) throws IOException {
        //
    }

}
