package jpiccoli.uwml.conference;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import javax.media.Manager;
import javax.media.Player;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JToggleButton;
import javax.swing.JToolBar;

import jpiccoli.uwml.media.RTPCallback;
import jpiccoli.uwml.media.RTPDemux;
import jpiccoli.uwml.media.RTPMediaSession;
import jpiccoli.uwml.util.ConnectionDescriptor;
import jpiccoli.uwml.util.ProtocolType;

public class Conference extends JFrame implements RTPCallback, ActionListener {
    
    private RTPDemux demux;
    
    private RTPMediaSession rtpAudioSession;
    private RTPMediaSession rtpVideoSession;

    private ArrayList<SessionParticipant> participants;
    
    private JToolBar toolBar;
    
    private JToggleButton activateAudioButton;
    private JToggleButton activateVideoButton;
    
    private JScrollPane scrollPane;
    private JPanel participantsPanel;
    
    public Conference() {
        Manager.setHint(Manager.LIGHTWEIGHT_RENDERER, Boolean.TRUE);
        participants = new ArrayList<SessionParticipant>();
        activateAudioButton = new JToggleButton("A");
        activateVideoButton = new JToggleButton("V");
        activateAudioButton.addActionListener(this);
        activateVideoButton.addActionListener(this);
        toolBar = new JToolBar();
        scrollPane = new JScrollPane();
        participantsPanel = new JPanel();
        addWindowListener(new ConferenceWindowListener());
    }

    public void start(ConnectionDescriptor descriptor, int sessionIdentifier) throws IOException {
        demux = new RTPDemux(descriptor, sessionIdentifier);
        rtpAudioSession = new RTPMediaSession(this, "javasound://", demux, (byte) 0, (byte) 1);
        rtpVideoSession = new RTPMediaSession(this, "vfw://0", demux, (byte) 2, (byte) 3);
        rtpAudioSession.initialize();
        rtpVideoSession.initialize();
        initialize();
    }
    
    private void initialize() {
        setLayout(new BorderLayout());
        toolBar.add(activateAudioButton);
        toolBar.add(activateVideoButton);
        getContentPane().add(toolBar, BorderLayout.NORTH);
        scrollPane.setViewportView(participantsPanel);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        participantsPanel.setLayout(new BoxLayout(participantsPanel, BoxLayout.Y_AXIS));
        getContentPane().add(scrollPane, BorderLayout.CENTER);
        setSize(180, 350);
        setResizable(false);
        setTitle("Conference");
        setVisible(true);
    }

    private SessionParticipant locateParticipant(long ssrc) {
        for (int i = 0; i < participants.size(); i++) {
            SessionParticipant partic = participants.get(i);
            if (partic.getVideoSSRC() == ssrc || partic.getAudioSSRC() == ssrc) {
                return partic;
            }
        }
        return null;
    }

    private SessionParticipant locateParticipant(String cName) {
        for (int i = 0; i < participants.size(); i++) {
            SessionParticipant partic = participants.get(i);
            if (partic.getCNAME() != null && partic.getCNAME().equals(cName)) {
                return partic;
            }
        }
        return null;
    }
    
    public synchronized void streamMapped(RTPMediaSession source, long ssrc, String cName) {
        SessionParticipant p1 = locateParticipant(cName);
        SessionParticipant p2 = locateParticipant(ssrc);
        if (p1 == null) {
            if (p2 != null) {
                p2.setCNAME(cName);
                participantsPanel.add(p2.getView());
            }
        } else if (p2 == null) {
            p1.setCNAME(cName);
        } else {
            if (p1 != p2) {
                if (p2.getVideoSSRC() == ssrc) {
                    p1.setVideoPlayer(ssrc, p2.getVideoPlayer());
                    p1.setVideoActive(true);
                    participants.remove(p2);
                } else if (p2.getAudioSSRC() == ssrc) {
                    p1.setAudioPlayer(ssrc, p2.getAudioPlayer());
                    p1.setAudioActive(true);
                    participants.remove(p2);
                }
            }
        }
    }
    
    private synchronized void audioStreamReceived(long ssrc, Player player) {
        SessionParticipant participant = locateParticipant(ssrc);
        if (participant == null) {
            participant = new SessionParticipant();
            participants.add(participant);
        }
        participant.setAudioPlayer(ssrc, player);
        participant.setAudioActive(true);
    }
    
    private synchronized void videoStreamReceived(long ssrc, Player player) {
        SessionParticipant participant = locateParticipant(ssrc);
        if (participant == null) {
            participant = new SessionParticipant();
            participants.add(participant);
        }
        participant.setVideoPlayer(ssrc, player);
        participant.setVideoActive(true);
    }
    
    public void streamReceived(RTPMediaSession source, long ssrc, Player p) {
        if (source == rtpAudioSession) {
            audioStreamReceived(ssrc, p);
        } else if (source == rtpVideoSession) {
            videoStreamReceived(ssrc, p);
        }
    }
    
    public void streamLost(RTPMediaSession source, long ssrc) {
        SessionParticipant participant = locateParticipant(ssrc);
        if (participant != null) {
            if (participant.getVideoSSRC() == ssrc) {
                participant.setVideoActive(false);
            } else if (participant.getAudioSSRC() == ssrc) {
                participant.setAudioActive(false);
            }
        }
    }

    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == activateAudioButton) {
            if (activateAudioButton.isSelected()) {
                rtpAudioSession.start();
            } else {
                rtpAudioSession.stop();
            }
        } else if (e.getSource() == activateVideoButton) {
            if (activateVideoButton.isSelected()) {
                rtpVideoSession.start();
            } else {
                rtpVideoSession.stop();
            }
        }

    }
    
    private class ConferenceWindowListener extends WindowAdapter {
        public void windowClosing(WindowEvent we) {
            if (demux != null) {
                demux.shutdown();
            }
            System.exit(0);
        } 
    }

    public static void main(String args[]) throws IOException {
        ConnectionDescriptor descriptor = new ConnectionDescriptor(ProtocolType.UDP, new InetSocketAddress(args[0], 3333), null);
        Conference conf = new Conference();
        conf.start(descriptor, 1);
    }
    
}
