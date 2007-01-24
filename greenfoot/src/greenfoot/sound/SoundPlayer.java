package greenfoot.sound;

import greenfoot.event.CompileListener;
import greenfoot.event.SimulationEvent;
import greenfoot.event.SimulationListener;
import greenfoot.util.GreenfootUtil;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;

import rmiextension.wrappers.event.RCompileEvent;

/**
 * Plays sounds from a file or URL. Several sounds can be played at the same
 * time.
 * 
 * <p>
 * 
 * The sounds will be stopped when a compiling or instantiating a new world.
 * 
 * @author Poul Henriksen
 * 
 */
public class SoundPlayer
    implements SimulationListener, CompileListener
{
    /**
     * Holds a list of all sounds currently playing. We use this list to stop
     * the sounds.
     */
    private List sounds = new ArrayList();

    /** singleton */
    private static SoundPlayer instance;

    /**
     * Only use clips when the size of the clip is below this value.
     */
    private static int maxClipSize = 500 * 1000;

    private SoundPlayer()
    {}

    public synchronized static SoundPlayer getInstance()
    {
        if (instance == null) {
            instance = new SoundPlayer();
        }
        return instance;
    }

    /**
     * Stops all sounds currently played by the soundplayer. Includes paused
     * sounds. Stopped sounds can NOT be resumed.
     * 
     */
    public synchronized void stop()
    {
        for (Iterator iter = sounds.iterator(); iter.hasNext();) {
            Sound element = (Sound) iter.next();
            element.stop();
        }
        sounds.clear();
    }

    /**
     * Pauses all sounds. Can be resumed.
     * 
     */
    public synchronized void pause()
    {
        for (Iterator iter = sounds.iterator(); iter.hasNext();) {
            Sound element = (Sound) iter.next();
            element.pause();
        }
    }

    /**
     * Resumes paused sounds.
     * 
     */
    public synchronized void resume()
    {
        for (Iterator iter = sounds.iterator(); iter.hasNext();) {
            Sound element = (Sound) iter.next();
            element.resume();
        }
    }

    /**
     * Plays the sound from file.
     * 
     * @param file Name of a file or an url
     * @throws IOException
     * @throws UnsupportedAudioFileException
     * @throws LineUnavailableException
     */
    public void play(final String file)
        throws IOException, UnsupportedAudioFileException, LineUnavailableException
    {

        // First, determine the size of the sound, if possible
        URL url = GreenfootUtil.getURL(file, "sounds");
        int size = url.openConnection().getContentLength();

        if (size == -1 || size > maxClipSize) {
            // If we can not get the size, or if it is a big file we stream it
            // in a thread.
            final Sound sound = new SoundStream(url, SoundPlayer.this);
            sounds.add(sound);
            Thread t = new Thread() {
                public void run()
                {
                    sound.play();
                }
            };
            t.start();
        }
        else {
            // The sound is small enough to be loaded into memory as a clip.
            Sound sound = new SoundClip(url, SoundPlayer.this);
            sounds.add(sound);
            sound.play();

        }
    }

    /**
     * Method that should be called by a sound when it is finished playing.
     */
    synchronized void soundFinished(Sound s)
    {
        sounds.remove(s);
    }

    /**
     * Stop sounds when simulation is disabled (a new world is created).
     */
    public void simulationChanged(SimulationEvent e)
    {
        if (e.getType() == SimulationEvent.DISABLED) {
            stop();
        }
        else if (e.getType() == SimulationEvent.STOPPED) {
            // pause();
        }
        else if (e.getType() == SimulationEvent.STARTED) {
            // resume();
        }
    }

    /**
     * Stop sounds when compiling.
     */
    public void compileStarted(RCompileEvent event)
    {
        stop();
    }

    public void compileError(RCompileEvent event)
    {}

    public void compileWarning(RCompileEvent event)
    {}

    public void compileSucceeded(RCompileEvent event)
    {}

    public void compileFailed(RCompileEvent event)
    {}
}
