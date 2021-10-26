package jpiccoli.uwml.media;

import javax.media.Player;

public interface RTPCallback {
    
    public void streamMapped(RTPMediaSession source, long ssrc, String cName);
    public void streamReceived(RTPMediaSession source, long ssrc, Player player);
    public void streamLost(RTPMediaSession source, long ssrc);
    
}
