package jp.oist.abcvlib.core.outputs;

import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import jp.oist.abcvlib.util.Logger;

import java.io.IOException;

import jp.oist.abcvlib.core.AbcvlibActivity;
import jp.oist.abcvlib.core.R;
import jp.oist.abcvlib.util.ErrorHandler;

import static android.content.Context.AUDIO_SERVICE;

public class MediaPlayerRunnable implements Runnable{

    private final String TAG = this.getClass().getName();

    private AbcvlibActivity abcvlibActivity;
    private AudioManager audioManager;
    private MediaPlayer mediaPlayer;
    // todo make this type more specific after knowing what type it actually is.
    private String audiofile; // was previously "android.resource://jp.oist.abcvlib.claplearn/" + R.raw.custommix

    /**
     * @param abcvlibActivity
     * @param audiofile String representing location of audiofile
     */
    public MediaPlayerRunnable(AbcvlibActivity abcvlibActivity, String audiofile){
        this.abcvlibActivity = abcvlibActivity;
        this.audiofile = audiofile;
    }

    public void run(){

        audioManager = (AudioManager) this.abcvlibActivity.getSystemService(AUDIO_SERVICE);
        mediaPlayer = new MediaPlayer();
        Uri loc = Uri.parse(audiofile);

        try {
            mediaPlayer.setDataSource(this.abcvlibActivity, loc);
            mediaPlayer.setAudioStreamType(AudioManager.STREAM_ALARM);
            mediaPlayer.setLooping(true);
            mediaPlayer.prepare();
        } catch (IllegalArgumentException | SecurityException| IllegalStateException | IOException e) {
            ErrorHandler.eLog(TAG,"Error running MediaPlayer", e, true);
        }
        audioManager.requestAudioFocus(null, AudioManager.STREAM_ALARM,AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        mediaPlayer.start();

    }
}
