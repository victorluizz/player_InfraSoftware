import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import org.w3c.dom.ls.LSOutput;
import support.PlayerWindow;
import support.Song;
import support.CustomFileChooser;
import java.lang.reflect.InvocationTargetException;

import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;
public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice the audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;
    private static int maxFrames;

    private boolean repeat = false;
    private boolean shuffle = false;
    private boolean playerEnabled = false;
    private boolean playerPaused = true;
    private Song currentSong;
    private int currentFrame = 0;
    private int newFrame;
    private String[][] sTPlay;
    ReentrantLock lock = new ReentrantLock();
    public Player() {
        sTPlay = new String[1][6];

        //button events
        ActionListener buttonListenerPlayNow = e -> {
            start(window.getSelectedSong());

        };
        ActionListener buttonListenerRemove =  e -> removeFromQueue(window.getSelectedSong());
        ActionListener buttonListenerAddSong =  e -> {
            addToQueue();
        };
        ActionListener buttonListenerPlayPause =  e -> {
            playerEnabled = !playerEnabled;
            if (playerEnabled) {
                pause();
            }
        };
        ActionListener buttonListenerStop =  e -> stop();
        ActionListener buttonListenerNext =  e -> next();
        ActionListener buttonListenerPrevious =  e -> previous();
        ActionListener buttonListenerShuffle =  e -> {
            shuffle = !shuffle;
            if (shuffle) {
                pause();
            }
        };
        ActionListener buttonListenerRepeat =  e -> {
            repeat = !repeat;
            if (repeat) {
                pause();
            }
        };

        //mouse events
        MouseMotionListener scrubberListenerMotion = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                mouseDragged(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {}
        };
        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {}

            @Override
            public void mousePressed(MouseEvent e) {}

            @Override
            public void mouseReleased(MouseEvent e) {
                mouseReleased(e);
            }

            @Override
            public void mouseEntered(MouseEvent e) {}

            @Override
            public void mouseExited(MouseEvent e) {}
        };

        String windowTitle = "JPlayer";

        window = new PlayerWindow(
                windowTitle,
                sTPlay,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerRepeat,
                scrubberListenerClick,
                scrubberListenerMotion);
    }

    //<editor-fold desc="Essential">

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {
        // TODO Is this thread safe?
        if (device != null) {
            Header h = bitstream.readFrame();
            if (h == null) return false;

            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
            device.write(output.getBuffer(), 0, output.getBufferLength());
            bitstream.closeFrame();
        }
        return true;
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        // TODO Is this thread safe?
        Header h = bitstream.readFrame();
        if (h == null) return false;
        bitstream.closeFrame();
        currentFrame++;
        return true;
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     * @throws BitstreamException
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        // TODO Is this thread safe?
        if (newFrame > currentFrame) {
            int framesToSkip = newFrame - currentFrame;
            boolean condition = true;
            while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
        }
    }
    //</editor-fold>

    //<editor-fold desc="Queue Utilities">
    public void addToQueue() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    Song songString = window.getNewSong();
                    String[][] newSongArray = new String[sTPlay.length + 1][6];

                    for (int i = 0; i < sTPlay.length; i++) {
                        newSongArray[i] = sTPlay[i];
                    }

                    newSongArray[sTPlay.length][0] = songString.getTitle();
                    newSongArray[sTPlay.length][1] = songString.getAlbum();
                    newSongArray[sTPlay.length][2] = songString.getArtist();
                    newSongArray[sTPlay.length][3] = songString.getYear();
                    newSongArray[sTPlay.length][4] = songString.getStrLength();
                    newSongArray[sTPlay.length][5] = songString.getFilePath();

                    sTPlay = newSongArray;
                    window.updateQueueList(newSongArray);


                } catch (IOException | BitstreamException | UnsupportedTagException | InvalidDataException dq){
                    System.out.println("Erro");
                } finally {
                    lock.unlock();
                }
            }
        }).start();
    };

    public void removeFromQueue(String filePath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                int indexMsc = 0;
                int songListTamanho = sTPlay.length;;

                String[][] newSongArray = new String[sTPlay.length -1][6];

                for(int i = 0; i < songListTamanho - 1; i++){
                    if (filePath.equals(sTPlay[indexMsc][5])){
                        indexMsc++;
                    }
                    newSongArray[i] = sTPlay[indexMsc];
                    indexMsc++;
                }

                sTPlay = newSongArray;
                window.updateQueueList(newSongArray);
                lock.unlock();
            }
        }).start();

       }

    public void getQueueAsArray() {
    }

    //</editor-fold>

    //<editor-fold desc="Controls">
    public void start(String sTPlay) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                try {
                    //File file = fileChooser.getSelectedFile();
                    maxFrames = new Mp3File(sTPlay).getFrameCount();
                    device = FactoryRegistry.systemRegistry().createAudioDevice();
                    device.open(decoder = new Decoder());
                    bitstream = new Bitstream(new BufferedInputStream(new FileInputStream(sTPlay)));
//                        bitstream = new Bitstream(array[1].getBufferedInputStream());
                    //progressBar.setMaximum(maxFrames);
                } catch (JavaLayerException | InvalidDataException | UnsupportedTagException | IOException ex) {
                    ex.printStackTrace();
                }
                finally {
                    lock.unlock();
                }
                if (device != null) {
                    try {
                        Header h;
                        int currentFrame = 0;
                        do {
                            h = bitstream.readFrame();
                            SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
                            device.write(output.getBuffer(), 0, output.getBufferLength());
                            bitstream.closeFrame();
                            //demoWindow.setProgress(currentFrame);
                            currentFrame++;
                        } while (h != null);
                    } catch (JavaLayerException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();

    }

    public void stop() {
    }

    public void pause() {
    }

    public void resume() {
    }

    public void next() {
    }

    public void previous() {
    }
    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>
}
