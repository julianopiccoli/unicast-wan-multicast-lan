package jpiccoli.uwml.util;

import java.net.SocketAddress;

public class ConnectionDescriptor {
    
    private ProtocolType protocol;
    private SocketAddress remoteAddress;
    private SocketAddress localAddress;
    
    public ConnectionDescriptor() {}
    
    public ConnectionDescriptor(ProtocolType protocol, SocketAddress remoteAddress, SocketAddress localAddress) {
        this.protocol = protocol;
        this.remoteAddress = remoteAddress;
        this.localAddress = localAddress;
    }

    public SocketAddress getLocalAddress() {
        return localAddress;
    }

    public void setLocalAddress(SocketAddress localAddress) {
        this.localAddress = localAddress;
    }

    public ProtocolType getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolType protocol) {
        this.protocol = protocol;
    }

    public SocketAddress getRemoteAddress() {
        return remoteAddress;
    }

    public void setRemoteAddress(SocketAddress remoteAddress) {
        this.remoteAddress = remoteAddress;
    }

}
