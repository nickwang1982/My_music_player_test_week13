package com.example.course.musicplayer.playback;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.PowerManager;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import com.example.course.musicplayer.MusicService;
import com.example.course.musicplayer.model.MusicProvider;
import com.example.course.musicplayer.model.MusicProviderSource;
import com.example.course.musicplayer.utils.LogHelper;
import com.example.course.musicplayer.utils.MediaIDHelper;

import java.io.IOException;

public class LocalPlayback implements Playback, AudioManager.OnAudioFocusChangeListener,
        MediaPlayer.OnCompletionListener,MediaPlayer.OnErrorListener,
        MediaPlayer.OnPreparedListener, MediaPlayer.OnSeekCompleteListener {
    private static final String TAG = LogHelper.makeLogTag(LocalPlayback.class.getSimpleName());

    // we don't have audio focus, and can't duck (play at a low volume)
    private static final int AUDIO_NO_FOCUS_NO_DUCK = 0;
    // we don't have focus, but can duck (play at a low volume)
    private static final int AUDIO_NO_FOCUS_CAN_DUCK = 1;
    // we have full audio focus
    private static final int AUDIO_FOCUSED  = 2;
    private static final float VOLUME_DUCK = 0.2f ;
    private static final float VOLUME_NORMAL = 1.0f ;

    private final Context mContext;
    private int mState;

    private Callback mCallback;
    private final MusicProvider mMusicProvider;
    private final AudioManager mAudioManager;
    private MediaPlayer mMediaPlayer;

    private boolean mPlayOnFocusGain;
    private volatile int mCurrentPosition;
    private volatile String mCurrentMediaId;
    private int mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK;
    private volatile boolean mAudioNoisyReceiverRegistered;

    private final IntentFilter mAudioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private final BroadcastReceiver mAudioNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                LogHelper.d(TAG, "Headphones disconnected.");
                if (isPlaying()) {
                    Intent i = new Intent(context, MusicService.class);
                    i.setAction(MusicService.ACTION_CMD);
                    i.putExtra(MusicService.CMD_NAME, MusicService.CMD_PAUSE);
                    mContext.startService(i);
                }
            }
        }
    };

    public LocalPlayback(Context context, MusicProvider musicProvider) {
        this.mContext = context;
        this.mMusicProvider = musicProvider;

        this.mAudioManager = (AudioManager) context.getSystemService(context.AUDIO_SERVICE);
        this.mState = PlaybackStateCompat.STATE_NONE;
    }

    @Override
    public void start() {

    }

    @Override
    public void stop(boolean notifyListeners) {
        mState = PlaybackStateCompat.STATE_STOPPED;
        mCurrentPosition = getCurrentStreamPosition();

        giveUpAudioFocus();
        unregisterAudioNoisyReceiver();
        relaxResources();
    }


    @Override
    public void setState(int state) {
        this.mState = state;
    }

    @Override
    public int getState() {
        return mState;
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isPlaying() {
        return mPlayOnFocusGain || (mMediaPlayer != null && mMediaPlayer.isPlaying());
    }

    @Override
    public int getCurrentStreamPosition() {
        return mMediaPlayer != null ?
                mMediaPlayer.getCurrentPosition() : mCurrentPosition;
    }

    @Override
    public void setCurrentStreamPosition(int pos) {

    }

    @Override
    public void updateLastKnownStreamPosition() {
        if (mMediaPlayer != null) {
            mCurrentPosition = mMediaPlayer.getCurrentPosition();
        }
    }

    @Override
    public void play(MediaSessionCompat.QueueItem item) {
        mPlayOnFocusGain = true;
        tryToGetAudioFocus();
        registerAudioNoisyReceiver();
        String mediaId = item.getDescription().getMediaId();
        boolean mediaHasChanged = !TextUtils.equals(mediaId, mCurrentMediaId);
        if (mediaHasChanged) {
            mCurrentPosition = 0;
            mCurrentMediaId = mediaId;
        }
        if (mState == PlaybackStateCompat.STATE_PAUSED && !mediaHasChanged && mMediaPlayer != null) {
            configMediaPlayerState();
        } else {
            mState = PlaybackStateCompat.STATE_STOPPED;
            relaxResources();
            MediaMetadataCompat track = mMusicProvider.getMusic(
                    MediaIDHelper.extractMusicIDFromMediaID(item.getDescription().getMediaId()));

            String source = track.getString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE);

            try {
                createMediaPlayerIfNeeded();

                mState = PlaybackStateCompat.STATE_BUFFERING;
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setDataSource(source);

                mMediaPlayer.prepareAsync();
            } catch (IOException ex) {
                LogHelper.e(TAG, ex, "Exception playing song");
                if (mCallback != null) {
                    mCallback.onError(ex.getMessage());
                }
            }
        }
    }



    @Override
    public void pause() {
        if (mState == PlaybackStateCompat.STATE_PLAYING) {
            // Pause media player and cancel the 'foreground service' state.
            if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                mMediaPlayer.pause();
                mCurrentPosition = mMediaPlayer.getCurrentPosition();
            }
            // while paused, retain the MediaPlayer but give up audio focus
            relaxResources();
            giveUpAudioFocus();
        }
        mState = PlaybackStateCompat.STATE_PAUSED;
        if (mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
        unregisterAudioNoisyReceiver();
    }

    @Override
    public void seekTo(int position) {
        LogHelper.d(TAG, "seekTo called with ", position);

        if (mMediaPlayer == null) {
            // If we do not have a current media player, simply update the current position
            mCurrentPosition = position;
        } else {
            if (mMediaPlayer.isPlaying()) {
                mState = PlaybackStateCompat.STATE_BUFFERING;
            }
            mMediaPlayer.seekTo(position);
            if (mCallback != null) {
                mCallback.onPlaybackStatusChanged(mState);
            }
        }
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        this.mCurrentMediaId = mediaId;
    }

    @Override
    public String getCurrentMediaId() {
        return mCurrentMediaId;
    }

    @Override
    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    /**
     * Try to get the system audio focus.
     */
    private void tryToGetAudioFocus() {
        LogHelper.d(TAG, "tryToGetAudioFocus");
        if (mAudioFocus != AUDIO_FOCUSED) {
            int result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocus = AUDIO_FOCUSED;
            }
        }
    }
    /**
     * Give up the audio focus.
     */
    private void giveUpAudioFocus() {
        LogHelper.d(TAG, "giveUpAudioFocus");
        if (mAudioFocus == AUDIO_FOCUSED) {
            if (mAudioManager.abandonAudioFocus(this) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                mAudioFocus = AUDIO_NO_FOCUS_NO_DUCK;
            }
        }
    }

    /**
     * Makes sure the media player exists and has been reset. This will create
     * the media player if needed, or reset the existing media player if one
     * already exists.
     */
    private void createMediaPlayerIfNeeded() {
        LogHelper.d(TAG, "createMediaPlayerIfNeeded. needed? ", (mMediaPlayer==null));
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();

            // Make sure the media player will acquire a wake-lock while
            // playing. If we don't do that, the CPU might go to sleep while the
            // song is playing, causing playback to stop.
            mMediaPlayer.setWakeMode(mContext.getApplicationContext(),
                    PowerManager.PARTIAL_WAKE_LOCK);

            // we want the media player to notify us when it's ready preparing,
            // and when it's done playing:
            mMediaPlayer.setOnPreparedListener(this);
            mMediaPlayer.setOnCompletionListener(this);
            mMediaPlayer.setOnErrorListener(this);
            mMediaPlayer.setOnSeekCompleteListener(this);
        } else {
            mMediaPlayer.reset();
        }
    }


    @Override
    public void onAudioFocusChange(int focusChange) {
        LogHelper.d(TAG, "onAudioFocusChange. focusChange=", focusChange);
        if (focusChange == AudioManager.AUDIOFOCUS_GAIN) {
            mAudioFocus = AUDIO_FOCUSED;
        } else if (focusChange == AudioManager.AUDIOFOCUS_LOSS ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
            boolean canDuck = focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK;
            mAudioFocus = canDuck ? AUDIO_NO_FOCUS_CAN_DUCK : AUDIO_NO_FOCUS_NO_DUCK;

            if (mState == PlaybackStateCompat.STATE_PLAYING && !canDuck) {
                // If we don't have audio focus and can't duck, we save the information that
                // we were playing, so that we can resume playback once we get the focus back.
                mPlayOnFocusGain = true;
            }
        } else {
            LogHelper.e(TAG, "onAudioFocusChange: Ignoring unsupported focusChange: ", focusChange);
        }

        configMediaPlayerState();
    }

    /**
     * Reconfigures MediaPlayer according to audio focus settings and
     * starts/restarts it. This method starts/restarts the MediaPlayer
     * respecting the current audio focus state. So if we have focus, it will
     * play normally; if we don't have focus, it will either leave the
     * MediaPlayer paused or set it to a low volume, depending on what is
     * allowed by the current focus settings. This method assumes mPlayer !=
     * null, so if you are calling it, you have to do so from a context where
     * you are sure this is the case.
     */
    private void configMediaPlayerState() {
        LogHelper.d(TAG, "configMediaPlayerState. mAudioFocus=", mAudioFocus);
        if (mAudioFocus == AUDIO_NO_FOCUS_NO_DUCK) {
            if (mState == PlaybackStateCompat.STATE_PLAYING) {
                pause();
            }
        } else {
            if (mAudioFocus == AUDIO_NO_FOCUS_CAN_DUCK) {
                mMediaPlayer.setVolume(VOLUME_DUCK, VOLUME_DUCK);
            } else {
                if (mMediaPlayer != null) {
                    mMediaPlayer.setVolume(VOLUME_NORMAL, VOLUME_NORMAL); // we can be loud again
                }
            }
            // If we were playing when we lost focus, we need to resume playing.
            if (mPlayOnFocusGain) {
                if (mMediaPlayer != null && !mMediaPlayer.isPlaying()) {
                    LogHelper.d(TAG,"configMediaPlayerState startMediaPlayer. seeking to ",
                            mCurrentPosition);
                    if (mCurrentPosition == mMediaPlayer.getCurrentPosition()) {
                        mMediaPlayer.start();
                        mState = PlaybackStateCompat.STATE_PLAYING;
                    } else {
                        mMediaPlayer.seekTo(mCurrentPosition);
                        mState = PlaybackStateCompat.STATE_BUFFERING;
                    }
                }
                mPlayOnFocusGain = false;
            }
        }
        if (mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
    }

    /**
     * Releases resources used by the service for playback. This includes the
     * "foreground service" status, the wake locks and possibly the MediaPlayer.
     */
    private void relaxResources() {
        LogHelper.d(TAG, "relaxResources. releaseMediaPlayer");

        // stop and release the Media Player, if it's available
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mMediaPlayer.release();
            mMediaPlayer = null;
        }
    }

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mContext.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mContext.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        LogHelper.d(TAG, "onCompletion from MediaPlayer");
        // The media player finished playing the current song, so we go ahead
        // and start the next.
        if (mCallback != null) {
            mCallback.onCompletion();
        }
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        LogHelper.e(TAG, "Media player error: what=" + what + ", extra=" + extra);
        if (mCallback != null) {
            mCallback.onError("MediaPlayer error " + what + " (" + extra + ")");
        }
        return true; // true indicates we handled the error
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        LogHelper.d(TAG, "onPrepared from MediaPlayer");
        // The media player is done preparing. That means we can start playing if we
        // have audio focus.
        configMediaPlayerState();
    }

    @Override
    public void onSeekComplete(MediaPlayer mp) {
        LogHelper.d(TAG, "onSeekComplete from MediaPlayer:", mp.getCurrentPosition());
        mCurrentPosition = mp.getCurrentPosition();
        if (mState == PlaybackStateCompat.STATE_BUFFERING) {
            mMediaPlayer.start();
            mState = PlaybackStateCompat.STATE_PLAYING;
        }
        if (mCallback != null) {
            mCallback.onPlaybackStatusChanged(mState);
        }
    }
}
