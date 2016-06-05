package com.example.course.musicplayer.model;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.provider.MediaStore;
import android.support.v4.media.MediaMetadataCompat;

import java.util.ArrayList;
import java.util.Iterator;

import static com.example.course.musicplayer.utils.MediaIDHelper.createMediaID;

/**
 * Utility class to get a list of MusicTrack's based on a server-side JSON
 * configuration.
 */
public class LocalMediaSource implements MusicProviderSource {

    private Context mContext;

    public LocalMediaSource(Context context) {
        mContext = context;
    }

    @Override
    public Iterator<MediaMetadataCompat> iterator() {


            ArrayList<MediaMetadataCompat> tracks = new ArrayList<>();
            ContentResolver cr = mContext.getContentResolver();
            if (cr != null) {
                Cursor cursor = cr.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        null, null, null, MediaStore.Audio.Media.DEFAULT_SORT_ORDER);

                if (null != cursor) {

                    if (cursor.moveToFirst()) {
                        do {
                            tracks.add(buildFromCursor(cursor));
                        } while (cursor.moveToNext());
                    }
                }
            }
            return tracks.iterator();

    }

    private MediaMetadataCompat buildFromCursor(Cursor cursor) {

        String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
        String album = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
        int duration = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
        String url = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.DATA));
        String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
        String id = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media._ID));

        // Adding the music source to the MediaMetadata (and consequently using it in the
        // mediaSession.setMetadata) is not a good idea for a real world music app, because
        // the session metadata can be accessed by notification listeners. This is done in this
        // sample for convenience only.
        //noinspection ResourceType
        return new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, id)
                .putString(MusicProviderSource.CUSTOM_METADATA_TRACK_SOURCE, url)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .build();
    }

}
