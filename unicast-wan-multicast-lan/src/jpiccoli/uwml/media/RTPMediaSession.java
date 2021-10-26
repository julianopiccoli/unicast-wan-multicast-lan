package jpiccoli.uwml.media;

import java.awt.Dimension;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Vector;
import javax.media.Controller;
import javax.media.ControllerClosedEvent;
import javax.media.ControllerErrorEvent;
import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.Format;
import javax.media.MediaLocator;
import javax.media.NoProcessorException;
import javax.media.Player;
import javax.media.Processor;
import javax.media.RealizeCompleteEvent;
import javax.media.control.BufferControl;
import javax.media.control.TrackControl;
import javax.media.format.VideoFormat;
import javax.media.protocol.ContentDescriptor;
import javax.media.protocol.DataSource;
import javax.media.rtp.Participant;
import javax.media.rtp.RTPControl;
import javax.media.rtp.RTPManager;
import javax.media.rtp.ReceiveStream;
import javax.media.rtp.ReceiveStreamListener;
import javax.media.rtp.RemoteListener;
import javax.media.rtp.SendStream;
import javax.media.rtp.SessionListener;
import javax.media.rtp.event.ActiveReceiveStreamEvent;
import javax.media.rtp.event.ByeEvent;
import javax.media.rtp.event.InactiveReceiveStreamEvent;
import javax.media.rtp.event.NewParticipantEvent;
import javax.media.rtp.event.NewReceiveStreamEvent;
import javax.media.rtp.event.ReceiveStreamEvent;
import javax.media.rtp.event.ReceiverReportEvent;
import javax.media.rtp.event.RemoteEvent;
import javax.media.rtp.event.RemotePayloadChangeEvent;
import javax.media.rtp.event.SessionEvent;
import javax.media.rtp.event.StreamMappedEvent;
import javax.media.rtp.event.TimeoutEvent;
import javax.media.rtp.rtcp.Feedback;

public class RTPMediaSession implements ReceiveStreamListener, SessionListener, ControllerListener, RemoteListener {
    private RTPManager     manager;
    private RTPTransmitter transmitter;
    private MediaLocator   locator;
    private Processor      processor;
    private DataSource     dataOutput;

    private Hashtable<Player, SSRCHolder> players;
    private RTPCallback callback;

    public RTPMediaSession(RTPCallback callback, String locator, RTPDemux demux, byte dataHeader, byte ctrlHeader) {
        this.locator = new MediaLocator(locator);
        transmitter = new RTPTransmitter(demux, dataHeader, ctrlHeader);
        manager = RTPManager.newInstance();
        players = new Hashtable<Player, SSRCHolder>();
        this.callback = callback;
    }

    /**
     * Starts the transmission. Returns null if transmission started ok.
     * Otherwise it returns a string with the reason why the setup failed.
     */
    public synchronized String start() {
        String result = createProcessor();
        if (result != null) return result;
        // Create an RTP session to transmit the output of the
        // processor to the specified IP address and port no.
        result = createTransmitter();
        if (result != null) {
            processor.close();
            processor = null;
            return result;
        }
        // Start the transmission
        processor.start();
        manager.addRemoteListener(this);
        return null;
    }

    /**
     * Stops the transmission if already started
     */
    public void stop() {
        synchronized (this) {
            if (processor != null) {
                processor.stop();
                processor.close();
                processor = null;
                //manager.removeTargets("Session ended.");
                //manager.dispose();
            }
        }
    }

    private String createProcessor() {
        if (locator == null) return "Locator is null";
        DataSource ds;
        try {
            ds = javax.media.Manager.createDataSource(locator);
        } catch (Exception e) {
            return "Couldn't create DataSource";
        }
        // Try to create a processor to handle the input media locator
        try {
            processor = javax.media.Manager.createProcessor(ds);
        } catch (NoProcessorException npe) {
            return "Couldn't create processor";
        } catch (IOException ioe) {
            return "IOException creating processor";
        }
        // Wait for it to configure
        boolean result = waitForState(processor, Processor.Configured);
        if (result == false) return "Couldn't configure processor";
        // Get the tracks from the processor
        TrackControl[] tracks = processor.getTrackControls();
        // Do we have atleast one track?
        if (tracks == null || tracks.length < 1) return "Couldn't find tracks in processor";
        // Set the output content descriptor to RAW_RTP
        // This will limit the supported formats reported from
        // Track.getSupportedFormats to only valid RTP formats.
        ContentDescriptor cd = new ContentDescriptor(ContentDescriptor.RAW_RTP);
        processor.setContentDescriptor(cd);
        Format supported[];
        Format chosen;
        boolean atLeastOneTrack = false;
        // Program the tracks.
        for (int i = 0; i < tracks.length; i++) {
            if (tracks[i].isEnabled()) {
                supported = tracks[i].getSupportedFormats();
                // We've set the output content to the RAW_RTP.
                // So all the supported formats should work with RTP.
                // We'll just pick the first one.
                if (supported.length > 0) {
                    if (supported[0] instanceof VideoFormat) {
                        // For video formats, we should double check the
                        // sizes since not all formats work in all sizes.
                        chosen = checkForVideoSizes(tracks[i].getFormat(), supported[1]);
                    } else chosen = supported[0];
                    tracks[i].setFormat(chosen);
                    System.err.println("Track " + i + " is set to transmit as:");
                    System.err.println("  " + chosen);
                    atLeastOneTrack = true;
                } else tracks[i].setEnabled(false);
            } else tracks[i].setEnabled(false);
        }
        if (!atLeastOneTrack) return "Couldn't set any of the tracks to a valid RTP format";
        // Realize the processor. This will internally create a flow
        // graph and attempt to create an output datasource for JPEG/RTP
        // audio frames.
        result = waitForState(processor, Controller.Realized);
        if (result == false) return "Couldn't realize processor";
        // Get the output data source of the processor
        dataOutput = processor.getDataOutput();
        return null;
    }

    /**
     * Use the RTPManager API to create sessions for each media 
     * track of the processor.
     */
    private String createTransmitter() {
        SendStream sendStream;
        try {
            // Initialize the RTPManager with the RTPSocketAdapter
            sendStream = manager.createSendStream(dataOutput, 0);
            sendStream.start();
        } catch (Exception e) {
            return e.getMessage();
        }
        return null;
    }

    /**
     * For JPEG and H263, we know that they only work for particular
     * sizes.  So we'll perform extra checking here to make sure they
     * are of the right sizes.
     */
    Format checkForVideoSizes(Format original, Format supported) {
        int width, height;
        Dimension size = ((VideoFormat) original).getSize();
        Format jpegFmt = new Format(VideoFormat.JPEG_RTP);
        Format h263Fmt = new Format(VideoFormat.H263_RTP);
        if (supported.matches(jpegFmt)) {
            // For JPEG, make sure width and height are divisible by 8.
            width = (size.width % 8 == 0 ? size.width : (int) (size.width / 8) * 8);
            height = (size.height % 8 == 0 ? size.height : (int) (size.height / 8) * 8);
        } else if (supported.matches(h263Fmt)) {
            // For H.263, we only support some specific sizes.
            if (size.width < 128) {
                width = 128;
                height = 96;
            } else if (size.width < 176) {
                width = 176;
                height = 144;
            } else {
                width = 352;
                height = 288;
            }
        } else {
            // We don't know this particular format.  We'll just
            // leave it alone then.
            return supported;
        }
        return (new VideoFormat(null, new Dimension(width, height), Format.NOT_SPECIFIED, null, Format.NOT_SPECIFIED)).intersects(supported);
    }

    public boolean initialize() {
        try {
            // Open the RTP sessions.
            // Parse the session addresses.
            manager.addSessionListener(this);
            manager.addReceiveStreamListener(this);
            // Initialize the RTPManager with the RTPSocketAdapter
            manager.initialize(transmitter);
            // You can try out some other buffer size to see
            // if you can get better smoothness.
            BufferControl bc = (BufferControl) manager.getControl("javax.media.control.BufferControl");
            if (bc != null) bc.setBufferLength(350);
        } catch (Exception e) {
            System.err.println("Cannot create the RTP Session: " + e.getMessage());
            return false;
        }
        return true;
    }

    /**
     * Close the players and the session managers.
     */
    public void close() {
        if (manager != null) {
            manager.removeTargets("Closing session from AVReceive3");
            manager.dispose();
            manager = null;
        }
    }

    /**
     * SessionListener.
     */
    public synchronized void update(SessionEvent evt) {
        if (evt instanceof NewParticipantEvent) {
            Participant p = ((NewParticipantEvent) evt).getParticipant();
            System.err.println("  - A new participant had just joined: " + p.getCNAME());
        }
    }

    /**
     * ReceiveStreamListener
     */
    public synchronized void update(ReceiveStreamEvent evt) {
        Participant participant = evt.getParticipant(); // could be null.
        ReceiveStream stream = evt.getReceiveStream(); // could be null.
        if (evt instanceof RemotePayloadChangeEvent) {
            System.err.println("  - Received an RTP PayloadChangeEvent.");
            System.err.println("Sorry, cannot handle payload change.");
            System.exit(0);
        } else if (evt instanceof NewReceiveStreamEvent || evt instanceof ActiveReceiveStreamEvent) {
            try {
                stream = ((NewReceiveStreamEvent) evt).getReceiveStream();
                DataSource ds = stream.getDataSource();
                if (ds == null) return;
                ds.connect();
                SSRCHolder holder = new SSRCHolder();
                holder.ssrc = stream.getSSRC();
                // Find out the formats.
                RTPControl ctl = (RTPControl) ds.getControl("javax.media.rtp.RTPControl");
                if (ctl != null) {
                    System.err.println("  - Recevied new RTP stream: " + ctl.getFormat());
                } else System.err.println("  - Recevied new RTP stream");
                if (participant == null) {
                    System.err.println("      The sender of this stream had yet to be identified.");
                } else {
                    holder.cName = participant.getCNAME();
                    System.err.println("      The stream comes from: " + participant.getCNAME());
                }
                // create a player by passing datasource to the Media Manager
                Player p = javax.media.Manager.createPlayer(ds);
                if (p == null) return;
                players.put(p, holder);
                p.addControllerListener(this);
                p.realize();
            } catch (Exception e) {
                System.err.println("NewReceiveStreamEvent exception " + e.getMessage());
                return;
            }
        } else if (evt instanceof StreamMappedEvent) {
            if (stream != null && stream.getDataSource() != null) {
                DataSource ds = stream.getDataSource();
                callback.streamMapped(this, stream.getSSRC(), participant.getCNAME());
                // Find out the formats.
                RTPControl ctl = (RTPControl) ds.getControl("javax.media.rtp.RTPControl");
                System.err.println("  - The previously unidentified stream ");
                if (ctl != null) System.err.println("      " + ctl.getFormat());
                System.err.println("      had now been identified as sent by: " + participant.getCNAME());
            }
        } else if (evt instanceof ByeEvent) {
            System.err.println("  - Got \"bye\" from: " + participant.getCNAME());
        } else if (evt instanceof InactiveReceiveStreamEvent || evt instanceof TimeoutEvent) {
            if (stream != null) {
                callback.streamLost(this, stream.getSSRC());
            } else {
                System.out.println("Stream = null");
            }
        }
    }

    public void update(RemoteEvent re) {
        if (re instanceof ReceiverReportEvent) {
            ReceiverReportEvent rre = (ReceiverReportEvent) re;
            Vector reports = rre.getReport().getFeedbackReports();
            for (int i = 0; i < reports.size(); i++) {
                Feedback feedback = (Feedback) reports.get(i);
                String userName = "unknown";
                if (rre.getReport().getParticipant() != null && rre.getReport().getParticipant().getCNAME() != null) { 
                    userName = rre.getReport().getParticipant().getCNAME();
                }
                System.out.println("FractionLost from " + userName + " : " + feedback.getFractionLost());
            }
        }
    }
    
    public void controllerUpdate(ControllerEvent ce) {
        if (ce.getSourceController() != null && ce.getSourceController() instanceof Player) {
            Player p = (Player) ce.getSourceController();
            if (ce instanceof RealizeCompleteEvent) {
                SSRCHolder holder = players.remove(p);
                if (holder != null) {
                    callback.streamReceived(this, holder.ssrc, p);                    
                    if (holder.cName != null) {
                        callback.streamMapped(this, holder.ssrc, holder.cName);
                    }
                }
                p.start();
            } else if (ce instanceof ControllerErrorEvent) {
                players.remove(p);
            }
        }
    }

    /****************************************************************
     * Convenience methods to handle processor's state changes.
     ****************************************************************/
    private Integer stateLock = new Integer(0);
    private boolean failed    = false;

    Integer getStateLock() {
        return stateLock;
    }

    void setFailed() {
        failed = true;
    }

    private synchronized boolean waitForState(Processor p, int state) {
        p.addControllerListener(new StateListener());
        failed = false;
        // Call the required method on the processor
        if (state == Processor.Configured) {
            p.configure();
        } else if (state == Processor.Realized) {
            p.realize();
        }
        // Wait until we get an event that confirms the
        // success of the method, or a failure event.
        // See StateListener inner class
        while (p.getState() < state && !failed) {
            synchronized (getStateLock()) {
                try {
                    getStateLock().wait();
                } catch (InterruptedException ie) {
                    return false;
                }
            }
        }
        if (failed) return false;
        else return true;
    }

    /****************************************************************
     * Inner Classes
     ****************************************************************/
    class StateListener implements ControllerListener {
        public void controllerUpdate(ControllerEvent ce) {
            // If there was an error during configure or
            // realize, the processor will be closed
            if (ce instanceof ControllerClosedEvent) setFailed();
            // All controller events, send a notification
            // to the waiting thread in waitForState method.
            if (ce instanceof ControllerEvent) {
                synchronized (getStateLock()) {
                    getStateLock().notifyAll();
                }
            }
        }
    }

    private class SSRCHolder {
        private long ssrc;
        String cName;
    }
    
}
