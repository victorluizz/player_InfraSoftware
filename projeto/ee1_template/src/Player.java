import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;

import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.locks.Condition;
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
    private Thread threadPlayMusic;
    private ArrayList<Song> songToPlay = new ArrayList<>();
    private ReentrantLock lock = new ReentrantLock();
    private ReentrantLock lockPlayPause = new ReentrantLock();
    private Condition verificadorPlayPause = lockPlayPause.newCondition();
    private  int indiceMusicaAtual;
    private boolean musicNext = false;
    private boolean musicPrevious = false;
    private int novoFrame;
    private boolean musicStop = false;


    public Player() {


        //button events
        ActionListener buttonListenerPlayNow = e -> {
            start(window.getSelectedSong());

        };
        ActionListener buttonListenerRemove = e -> removeFromQueue(window.getSelectedSong());
        ActionListener buttonListenerAddSong = e -> {
            addToQueue();
        };
        ActionListener buttonListenerPlayPause = e -> {
            if (playerPaused){
                resume();
            } else{
                pause();
            }

        };
        ActionListener buttonListenerStop = e -> stop();
        ActionListener buttonListenerNext = e -> next();
        ActionListener buttonListenerPrevious = e -> previous();
        ActionListener buttonListenerShuffle = e -> {
            shuffle = !shuffle;
            if (shuffle) {
                pause();
            }
        };
        ActionListener buttonListenerRepeat = e -> {
            repeat = !repeat;
            if (repeat) {
                pause();
            }
        };

        //mouse events
        MouseMotionListener scrubberListenerMotion = new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                pause();
                novoFrame = (window.getScrubberValue()/(int)currentSong.getMsPerFrame());
                window.setTime(currentFrame*(int)currentSong.getMsPerFrame(), (int)currentSong.getMsLength());

            }

            @Override
            public void mouseMoved(MouseEvent e) {
            }
        };
        MouseListener scrubberListenerClick = new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
            }

            @Override
            public void mousePressed(MouseEvent e) {
                pause();
                novoFrame = (window.getScrubberValue()/(int)currentSong.getMsPerFrame());
                window.setTime(currentFrame*(int)currentSong.getMsPerFrame(), (int)currentSong.getMsLength());

            }

            @Override
            public void mouseReleased(MouseEvent e) {
                try{
                    if (novoFrame >= currentFrame){
                        skipToFrame(novoFrame);
                    } else {
                        bitstream.close();
                        device = FactoryRegistry.systemRegistry().createAudioDevice();
                        device.open(decoder = new Decoder());
                        bitstream = new Bitstream(currentSong.getBufferedInputStream());
                        currentFrame = 0;
                        skipToFrame(novoFrame);
                    }

                    resume();
                } catch (FileNotFoundException ex) {
                    ex.printStackTrace();
                } catch (BitstreamException ex) {
                    ex.printStackTrace();
                } catch (JavaLayerException ex) {
                    ex.printStackTrace();
                }

            }

            @Override
            public void mouseEntered(MouseEvent e) {
            }

            @Override
            public void mouseExited(MouseEvent e) {
            }
        };

        String windowTitle = "JPlayer";

        window = new PlayerWindow(
                windowTitle,
                getDisplayInfo(),
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
    private String[][] getDisplayInfo() {
        String[][] arrayAuxiliar = new String[songToPlay.size()][];

        for (int i = 0; i < songToPlay.size(); i++) {
            arrayAuxiliar[i] = songToPlay.get(i).getDisplayInfo();

        }

        return arrayAuxiliar;


    }

    //<editor-fold desc="Queue Utilities">
    public void addToQueue() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    Song newSong = window.getNewSong();

                    boolean musicaExiste = false;
                    for (int i = 0; i < songToPlay.size(); i++) {
                        if (newSong.getFilePath().equals(songToPlay.get(i).getFilePath())) {
                            musicaExiste = true;
                            break;
                        }
                    }
                    if (!musicaExiste) songToPlay.add(newSong);

                    window.updateQueueList(getDisplayInfo());


                } catch (IOException | BitstreamException | UnsupportedTagException | InvalidDataException dq) {
                    System.out.println("Erro");
                } finally {
                    lock.unlock();
                }
            }
        }).start();
    }

    ;

    public void removeFromQueue(String filePath) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    lock.lock();
                    int songListTamanho = songToPlay.size();

                    for (int i = 0; i < songListTamanho; i++) {
                        if (filePath.equals(songToPlay.get(i).getFilePath())) {

                            if (indiceMusicaAtual == i) {
                                indiceMusicaAtual -= 1;
                                next();
                                if (indiceMusicaAtual == songToPlay.size() - 1){
                                    stop();
                                }
                            }
                            if (i < indiceMusicaAtual){
                                indiceMusicaAtual -= 1;
                                if (indiceMusicaAtual == 0){
                                    window.setEnabledPreviousButton(false);
                                }

                            }
                            songToPlay.remove(i);


                            break;
                        }
                    }


                    window.updateQueueList(getDisplayInfo());

                } finally {
                    lock.unlock();
                }
            }
        }).start();

    }

    public void getQueueAsArray() {
    }

    class RunnablePlay implements Runnable {
        @Override

        public void run() {
            try {
                window.setEnabledStopButton(true);
                window.setEnabledScrubber(true);
                while (indiceMusicaAtual < songToPlay.size()){
                    if (indiceMusicaAtual == songToPlay.size() - 1){
                        window.setEnabledNextButton(false);
                    } else{
                        window.setEnabledNextButton(true);
                    }
                    if (indiceMusicaAtual == 0){
                        window.setEnabledPreviousButton(false);
                    } else{
                        window.setEnabledPreviousButton(true);
                    }
                    currentSong = songToPlay.get(indiceMusicaAtual);
                    device = FactoryRegistry.systemRegistry().createAudioDevice();
                    device.open(decoder = new Decoder());
                    bitstream = new Bitstream(currentSong.getBufferedInputStream());
                    playerPaused = false;
                    currentFrame = 0;


                    boolean verificarIncrementacao = true;
                    while (playNextFrame()) {
                        lockPlayPause.lock();
                        try{
                            if (musicStop){
                                musicStop = false;
                                currentFrame = 0;
                                window.resetMiniPlayer();
                                threadPlayMusic.stop(); //Talvez tenha bugs nesse top... :(


                            }
                            if(playerPaused){
                                verificadorPlayPause.await();
                            }
                            window.setTime(currentFrame * (int) currentSong.getMsPerFrame(), (int) currentSong.getMsLength());
                            currentFrame++;
                            if(musicNext){
                                musicNext = false;
                                verificarIncrementacao = true;
                                break;
                            }
                            if (musicPrevious){
                                musicPrevious = false;
                                verificarIncrementacao = false;
                                break;
                            }
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        } finally {
                            lockPlayPause.unlock();
                        }
                    }
                    currentFrame = 0;

                    if(verificarIncrementacao){
                        indiceMusicaAtual++;
                    } else{
                        indiceMusicaAtual--;
                    }
                }
                window.resetMiniPlayer();
            } catch (JavaLayerException | FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    //</editor-fold>

    //<editor-fold desc="Controls">
    public void start(String musicStart) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                lock.lock();
                try {

                    for (int i = 0; i < songToPlay.size(); i++) {
                        if (musicStart.equals(songToPlay.get(i).getFilePath())) {
                            indiceMusicaAtual = i;
                            threadPlayMusic = new Thread(new RunnablePlay());
                            playerPaused = false;
                            window.updatePlayPauseButtonIcon(playerPaused);
                            window.setEnabledPlayPauseButton(!playerPaused);
                            threadPlayMusic.start();

                        }

                    }

                } finally {
                    lock.unlock();
                }

            }
        }).start();

    }


    public void stop() {
        musicStop = true;

    }

    public void pause() {
        playerPaused = true;
        window.updatePlayPauseButtonIcon(playerPaused);

    }

    public void resume() {
        playerPaused = false;
        lockPlayPause.lock();
        try{
            verificadorPlayPause.signalAll();
        } finally {
            lockPlayPause.unlock();
        }
        window.updatePlayPauseButtonIcon(playerPaused);

    }

    public void next() {
        musicNext = true;



    }

    public void previous() {
        musicPrevious = true;
    }
    //</editor-fold>

    //<editor-fold desc="Getters and Setters">

    //</editor-fold>
}
