package com.example.course.musicplayer.ui;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.NavigationView;
import android.support.v4.media.MediaBrowserCompat;
import android.text.TextUtils;

import com.example.course.musicplayer.R;
import com.example.course.musicplayer.ui.MediaBrowserFragment.MediaFragmentListener;
import com.example.course.musicplayer.utils.LogHelper;

import static com.example.course.musicplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM;
import static com.example.course.musicplayer.utils.MediaIDHelper.MEDIA_ID_ROOT;

public class MusicPlayerActivity extends ExtendBaseActivity
        implements MediaFragmentListener {

    private static final String TAG = LogHelper.makeLogTag(MusicPlayerActivity.class.getSimpleName());
    private static final String SAVED_MEDIA_ID="com.example.course.musicplayer.MEDIA_ID";
    private static final String FRAGMENT_TAG = "mp_list_container";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initializedToolbar();

        initializeFromParams(savedInstanceState, getIntent());
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        String mediaId = getMediaId();
        if (mediaId != null) {
            outState.putString(SAVED_MEDIA_ID, mediaId);
        }
        super.onSaveInstanceState(outState);
    }

    public String getMediaId() {
        MediaBrowserFragment fragment = getBrowseFragment();
        if (fragment == null) {
            return null;
        }
        return fragment.getMediaId();
    }

    private void initializeFromParams(Bundle savedInstanceState, Intent intent) {
        String mediaId = null;
        if (savedInstanceState != null) {
            // If there is a saved media ID, use it
            mediaId = savedInstanceState.getString(SAVED_MEDIA_ID);
        } else if (intent.hasExtra(SAVED_MEDIA_ID)) {
            mediaId = intent.getExtras().getString(SAVED_MEDIA_ID);
        }
        navigateToBrowser(mediaId, false);
        setNavigationItem(mediaId);
    }

    public void navigateToBrowser(String mediaId, boolean addToBackstack) {
        MediaBrowserFragment fragment = getBrowseFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = new MediaBrowserFragment();
            fragment.setMediaId(mediaId);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                    R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                    R.animator.slide_in_from_left, R.animator.slide_out_to_right);
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG);
            if (addToBackstack) {
                transaction.addToBackStack(null);
            }
            transaction.commit();
        }
    }

    private MediaBrowserFragment getBrowseFragment() {
        return (MediaBrowserFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }

    @Override
    protected void onMediaControllerConnected() {
        getBrowseFragment().onConnected();
    }

    @Override
    public void onMediaItemSelected(MediaBrowserCompat.MediaItem item) {
        LogHelper.d(TAG, "onMediaItemSelected, mediaId=" + item.getMediaId());
        if (item.isPlayable()) {
            getSupportMediaController().getTransportControls()
                    .playFromMediaId(item.getMediaId(), null);
        } else if (item.isBrowsable()) {
            navigateToBrowser(item.getMediaId(), true);
        } else {
            LogHelper.w(TAG, "Ignoring MediaItem that is neither browsable nor playable: ",
                    "mediaId=", item.getMediaId());
        }
    }

    @Override
    public void setToolbarTitle(CharSequence title) {
        LogHelper.d(TAG, "Setting toolbar title to ", title);
        if (title == null) {
            title = getString(R.string.app_name);
        }
        setTitle(title);
    }

    @Override
    public void setNavigationItem(String mediaId) {
        LogHelper.d(TAG, "Setting navigation view mediaId to ", mediaId);
        if (mediaId != null) {
            updateNavigationView(mediaId);
        }
    }
}
