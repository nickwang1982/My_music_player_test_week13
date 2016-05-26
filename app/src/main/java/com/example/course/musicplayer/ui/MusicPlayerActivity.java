package com.example.course.musicplayer.ui;

import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import com.example.course.musicplayer.R;

public class MusicPlayerActivity extends BaseActivity {

    private static final String SAVED_MEDIA_ID="com.example.course.musicplayer.MEDIA_ID";
    private static final String FRAGMENT_TAG = "mp_list_container";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
        //TODO: Add media id from fragment later.
        return null;
    }

    private void initializeFromParams(Bundle savedInstanceState, Intent intent) {
        String mediaId = null;
        if (savedInstanceState != null) {
            // If there is a saved media ID, use it
            mediaId = savedInstanceState.getString(SAVED_MEDIA_ID);
        }
        navigateToBrowser(mediaId);
    }

    private void navigateToBrowser(String mediaId) {
        MediaBrowserFragment fragment = getBrowseFragment();

        if (fragment == null || !TextUtils.equals(fragment.getMediaId(), mediaId)) {
            fragment = new MediaBrowserFragment();
            fragment.setMediaId(mediaId);
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.setCustomAnimations(
                    R.animator.slide_in_from_right, R.animator.slide_out_to_left,
                    R.animator.slide_in_from_left, R.animator.slide_out_to_right);
            transaction.replace(R.id.container, fragment, FRAGMENT_TAG);
            // If this is not the top level media (root), we add it to the fragment back stack,
            // so that actionbar toggle and Back will work appropriately:
            if (mediaId != null) {
                transaction.addToBackStack(null);
            }
            transaction.commit();
        }
    }

    private MediaBrowserFragment getBrowseFragment() {
        return (MediaBrowserFragment) getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
    }
}
