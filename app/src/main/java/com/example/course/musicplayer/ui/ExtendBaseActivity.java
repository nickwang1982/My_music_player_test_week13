package com.example.course.musicplayer.ui;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import com.example.course.musicplayer.MusicService;
import com.example.course.musicplayer.R;
import com.example.course.musicplayer.utils.LogHelper;

/**
 * Base activity for activities that need to show a playback control fragment when media is playing.
 */
public abstract class ExtendBaseActivity extends BaseActivity implements MediaBrowserProvider {

    private static final String TAG = LogHelper.makeLogTag(BaseActivity.class.getSimpleName());

    private MediaBrowserCompat mMediaBrowser;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LogHelper.d(TAG, "Activity onCreate");

        // Connect a media browser just to get the media session token. There are other ways
        // this can be done, for example by sharing the session token directly.
        mMediaBrowser = new MediaBrowserCompat(this,
            new ComponentName(this, MusicService.class), mConnectionCallback, null);
    }

    @Override
    protected void onStart() {
        super.onStart();
        LogHelper.d(TAG, "Activity onStart");
        mMediaBrowser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        LogHelper.d(TAG, "Activity onStop");
        mMediaBrowser.disconnect();
    }

    @Override
    public MediaBrowserCompat getMediaBrowser() {
        return mMediaBrowser;
    }



    private final MediaBrowserCompat.ConnectionCallback mConnectionCallback =
        new MediaBrowserCompat.ConnectionCallback() {
            @Override
            public void onConnected() {
                LogHelper.d(TAG, "Music service connected");
                    //TODO: add connect actions later
                try {
                    connectToSession(mMediaBrowser.getSessionToken());
                } catch (RemoteException e) {
                    LogHelper.e(TAG, e, "could not connect media controller");
                }
            }
        };

    private void connectToSession(MediaSessionCompat.Token token) throws RemoteException {
        onMediaControllerConnected();
    }

    protected void onMediaControllerConnected() {
        // empty implementation, can be overridden by clients.
    }
}
