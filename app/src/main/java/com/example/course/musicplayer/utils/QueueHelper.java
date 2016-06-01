package com.example.course.musicplayer.utils;

import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.example.course.musicplayer.model.MusicProvider;

import java.util.ArrayList;
import java.util.List;


import static com.example.course.musicplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_ALBUM;
import static com.example.course.musicplayer.utils.MediaIDHelper.MEDIA_ID_MUSICS_BY_SEARCH;
import static com.example.course.musicplayer.utils.MediaIDHelper.MEDIA_ID_ROOT;


public class QueueHelper {

    private static final String TAG = LogHelper.makeLogTag(QueueHelper.class.getSimpleName());

    private static final int RANDOM_QUEUE_SIZE = 10;


    public static List<MediaSessionCompat.QueueItem> getPlayingQueue(String mediaId,
                                                                     MusicProvider musicProvider) {

        // extract the browsing hierarchy from the media ID:
        String[] hierarchy = MediaIDHelper.getHierarchy(mediaId);

        Iterable<MediaMetadataCompat> tracks = null;

        if (hierarchy.length == 2) {

            String categoryType = hierarchy[0];
            String categoryValue = hierarchy[1];
            LogHelper.d(TAG, "Creating playing queue for ", categoryType, ",  ", categoryValue);


            // This sample only supports genre and by_search category types.
            if (categoryType.equals(MEDIA_ID_MUSICS_BY_ALBUM)) {
                tracks = musicProvider.getMusicsByAlbum(categoryValue);
            }

            if (tracks == null) {
                LogHelper.e(TAG, "Unrecognized mediaId type:  for media ", mediaId);
                return null;
            }
            return convertToQueue(tracks, hierarchy[0], hierarchy[1]);
        } else if (hierarchy.length == 1) {
            String categoryValue = hierarchy[0];
            LogHelper.d(TAG, "Creating playing queue for ", categoryValue);
            if (categoryValue.equals(MEDIA_ID_ROOT)) {
                tracks = musicProvider.getAllMusics();
            }
            if (tracks == null) {
                LogHelper.e(TAG, "Unrecognized mediaId type:  for media ", mediaId);
                return null;
            }

            return convertToQueue(tracks, hierarchy[0]);
        } else {
            LogHelper.e(TAG, "Could not build a playing queue for this mediaId: ", mediaId);
            return null;
        }
    }

    private static List<MediaSessionCompat.QueueItem> convertToQueue(
            Iterable<MediaMetadataCompat> tracks, String... categories) {
        List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
        int count = 0;
        for (MediaMetadataCompat track : tracks) {

            // We create a hierarchy-aware mediaID, so we know what the queue is about by looking
            // at the QueueItem media IDs.
            String hierarchyAwareMediaID = MediaIDHelper.createMediaID(
                    track.getDescription().getMediaId(), categories);

            MediaMetadataCompat trackCopy = new MediaMetadataCompat.Builder(track)
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, hierarchyAwareMediaID)
                    .build();

            // We don't expect queues to change after created, so we use the item index as the
            // queueId. Any other number unique in the queue would work.
            MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                    trackCopy.getDescription(), count++);
            queue.add(item);
        }
        return queue;

    }

    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
                                           long queueId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (queueId == item.getQueueId()) {
                return index;
            }
            index++;
        }
        return -1;
    }

    public static int getMusicIndexOnQueue(Iterable<MediaSessionCompat.QueueItem> queue,
                                           String mediaId) {
        int index = 0;
        for (MediaSessionCompat.QueueItem item : queue) {
            if (mediaId.equals(item.getDescription().getMediaId())) {
                return index;
            }
            index++;
        }
        return -1;
    }


    public static boolean isIndexPlayable(int index, List<MediaSessionCompat.QueueItem> queue) {
        return (queue != null && index >= 0 && index < queue.size());
    }
}
