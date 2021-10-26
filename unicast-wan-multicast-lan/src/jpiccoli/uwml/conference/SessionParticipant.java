package jpiccoli.uwml.conference;

import javax.media.Player;

public class SessionParticipant {
    
    private String cName;
    private boolean videoActive;
    private boolean audioActive;

    private long videoSSRC;
    private long audioSSRC;
    
    private Player videoPlayer;
    private Player audioPlayer;
    
    private ParticipantView view;
    
    public SessionParticipant() {
        videoSSRC = -1;
        audioSSRC = -1;
        view = new ParticipantView();
    }
    
    public void setCNAME(String cName) {
        this.cName = cName;
        view.setParticipantName(cName);
    }
    
    public String getCNAME() {
        return cName;
    }
    
    public boolean isAudioActive() {
        return audioActive;
    }

    public void setAudioActive(boolean audioActive) {
        this.audioActive = audioActive;
        if (audioActive)
            view.activateMicrophone();
        else {
            view.deactivateMicrophone();
            if (audioPlayer != null) {
                audioPlayer.stop();
                audioPlayer.close();
            }
        }
    }

    public boolean isVideoActive() {
        return videoActive;
    }

    public void setVideoActive(boolean videoActive) {
        this.videoActive = videoActive;
        if (videoActive)
            view.activateVideo(videoPlayer.getVisualComponent());
        else {
            view.deactivateVideo();
            if (videoPlayer != null) {
                videoPlayer.stop();
                videoPlayer.close();
            }
        }
    }

    public Player getAudioPlayer() {
        return audioPlayer;
    }

    public void setAudioPlayer(long ssrc, Player audioPlayer) {
        this.audioSSRC = ssrc;
        this.audioPlayer = audioPlayer;
    }

    public Player getVideoPlayer() {
        return videoPlayer;
    }

    public void setVideoPlayer(long ssrc, Player videoPlayer) {
        this.audioSSRC = ssrc;
        this.videoPlayer = videoPlayer;
    }
    
    public long getVideoSSRC() {
        return videoSSRC;
    }

    public long getAudioSSRC() {
        return audioSSRC;
    }
    
    public ParticipantView getView() {
        return view;
    }
    
}
