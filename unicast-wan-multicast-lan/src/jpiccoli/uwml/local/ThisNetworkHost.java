package jpiccoli.uwml.local;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ThisNetworkHost extends LocalNetworkHost {

	public ThisNetworkHost(boolean active, byte managerStatus) {
		super(null, active, managerStatus);
	}

	public InetAddress getAddress() {
        try {
			return InetAddress.getLocalHost();
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
    }
	
}
