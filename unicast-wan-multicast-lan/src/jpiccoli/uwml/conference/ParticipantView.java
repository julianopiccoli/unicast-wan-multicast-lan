package jpiccoli.uwml.conference;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLabel;

public class ParticipantView extends JComponent {

    private JLabel participantName;
    private JLabel participantImage;
    private JLabel microphoneLabel;
    
    private Icon participantIcon;
    private Icon activateMicrophoneIcon;
    private Icon deactivatedMicrophoneIcon;
    
    private Component videoComponent;
    
    public ParticipantView() {
        participantName = new JLabel("Unknown participant");
        participantImage = new JLabel("X");
        microphoneLabel = new JLabel("X");
        initialize();
    }
    
    private void initialize() {
        setLayout(new BorderLayout());
        setOpaque(false);
        add(participantName, BorderLayout.SOUTH);
        add(participantImage, BorderLayout.CENTER);
        add(microphoneLabel, BorderLayout.WEST);
        participantName.setOpaque(true);
        participantName.setBackground(new Color(114,121,184));
        participantName.setForeground(Color.WHITE);
        setPreferredSize(new Dimension(150,150));
    }
    
    public void activateVideo(Component videoComponent) {
        this.videoComponent = videoComponent;
        videoComponent.setSize(130, 130);
        videoComponent.setPreferredSize(new Dimension(130, 130));
        remove(participantImage);
        add(videoComponent, BorderLayout.CENTER);
    }
    
    public void deactivateVideo() {
        if (videoComponent != null) remove(videoComponent);
        add(participantImage, BorderLayout.CENTER);
    }
    
    public void activateMicrophone() {
        microphoneLabel.setText("O");
    }
    
    public void deactivateMicrophone() {
        microphoneLabel.setText("X");
    }
    
    public void setParticipantName(String name) {
        participantName.setText(name);
    }
    
}
