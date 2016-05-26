package com.example.course.musicplayer.ui;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.example.course.musicplayer.R;



import java.util.ArrayList;


public class MediaBrowserFragment extends Fragment {


    private static final String ARG_MEDIA_ID = "media_id";

    private BrowseAdapter mBrowserAdapter;

    public MediaBrowserFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_list, container, false);

        mBrowserAdapter = new BrowseAdapter(getActivity());

        ListView listView = (ListView) rootView.findViewById(R.id.list_view);
        listView.setAdapter(mBrowserAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                MediaBrowserCompat.MediaItem item = mBrowserAdapter.getItem(position);
            }
        });


        //TODO: removew this fake data after data provider readly
        mBrowserAdapter.clear();
        for (int i=0; i<=15 ; i++) {
            MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                    .setMediaId("Test_Media")
                    .setTitle("Test Title" + i)
                    .setSubtitle("This is Test sub title")
                    .build();
            MediaBrowserCompat.MediaItem testItem = new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_PLAYABLE);
            mBrowserAdapter.add(testItem);
        }
        mBrowserAdapter.notifyDataSetChanged();

        return rootView;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }

    public String getMediaId() {
        Bundle args = getArguments();
        if(args != null) {
            return args.getString(ARG_MEDIA_ID);
        }
        return null;
    }

    public void setMediaId(String mediaId) {
        Bundle args = new Bundle(1);
        args.putString(MediaBrowserFragment.ARG_MEDIA_ID, mediaId);
        setArguments(args);
    }


    private static class BrowseAdapter extends ArrayAdapter<MediaBrowserCompat.MediaItem> {
        public BrowseAdapter(Context context) {
            super(context, R.layout.media_list_item, new ArrayList<MediaBrowserCompat.MediaItem>());
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            MediaBrowserCompat.MediaItem item = getItem(position);
            int itemState = MediaItemViewHolder.STATE_PLAYING;
            return MediaItemViewHolder.setupView((Activity) getContext(), convertView, parent,
                    item.getDescription(), itemState);
        }
    }
}
