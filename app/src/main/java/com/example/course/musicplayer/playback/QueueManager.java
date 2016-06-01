package com.example.course.musicplayer.playback;


import android.content.res.Resources;
import android.graphics.Bitmap;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;

import com.example.course.musicplayer.AlbumArtCache;
import com.example.course.musicplayer.R;
import com.example.course.musicplayer.model.MusicProvider;
import com.example.course.musicplayer.utils.LogHelper;
import com.example.course.musicplayer.utils.MediaIDHelper;
import com.example.course.musicplayer.utils.QueueHelper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Simple data provider for queues. Keeps track of a current queue and a current index in the
 * queue. Also provides methods to set the current queue based on common queries, relying on a
 * given MusicProvider to provide the actual media metadata.
 */
public class QueueManager {
    private static final String TAG = LogHelper.makeLogTag(QueueManager.class.getSimpleName());

    private MusicProvider mMusicProvider;
    private MetadataUpdateListener mListener;
    private Resources mResources;

    // "Now playing" queue:
    private List<MediaSessionCompat.QueueItem> mPlayingQueue;
    private int mCurrentIndex;

    public QueueManager(MusicProvider musicProvider, Resources resources, MetadataUpdateListener listener) {
        this.mMusicProvider = musicProvider;
        this.mResources = resources;
        this.mListener = listener;

        mPlayingQueue = Collections.synchronizedList(new ArrayList<MediaSessionCompat.QueueItem>());
        mCurrentIndex = 0;
    }

    public boolean isSameBrowsingCategory(@NonNull String mediaId) {
        String[] newBrowseHierarchy = MediaIDHelper.getHierarchy(mediaId);
        MediaSessionCompat.QueueItem current = getCurrentMusic();
        if (current == null) {
            return false;
        }
        String[] currentBrowseHierarchy = MediaIDHelper.getHierarchy(current.getDescription().getMediaId());

        return Arrays.equals(newBrowseHierarchy, currentBrowseHierarchy);
    }

    private void setCurrentQueueIndex(int index) {
        if (index >= 0 && index < mPlayingQueue.size()) {
            mCurrentIndex = index;
            mListener.onCurrentQueueIndexUpdated(mCurrentIndex);
        }
    }

    public boolean setCurrentQueueItem(long queueId) {
        // set the current index on queue from the queue Id:
        int index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, queueId);
        setCurrentQueueIndex(index);
        return index >= 0;
    }

    public boolean setCurrentQueueItem(String mediaId) {
        // set the current index on queue from the music Id:
        int index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, mediaId);
        setCurrentQueueIndex(index);
        return index >= 0;
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

    protected void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue) {
        setCurrentQueue(title, newQueue, null);
    }

    protected void setCurrentQueue(String title, List<MediaSessionCompat.QueueItem> newQueue,
                                   String initialMediaId) {
        mPlayingQueue = newQueue;
        int index = 0;
        if (initialMediaId != null) {
            index = QueueHelper.getMusicIndexOnQueue(mPlayingQueue, initialMediaId);
        }
        mCurrentIndex = Math.max(index, 0);
        mListener.onQueueUpdated(title, newQueue);
    }

    public void setQueueFromMusic(String mediaId) {
        LogHelper.d(TAG, "setQueueFromMusic", mediaId);

        // The mediaId used here is not the unique musicId. This one comes from the
        // MediaBrowser, and is actually a "hierarchy-aware mediaID": a concatenation of
        // the hierarchy in MediaBrowser and the actual unique musicID. This is necessary
        // so we can build the correct playing queue, based on where the track was
        // selected from.
        boolean canReuseQueue = false;
        if (isSameBrowsingCategory(mediaId)) {
            canReuseQueue = setCurrentQueueItem(mediaId);
        }
        if (!canReuseQueue) {
            String queueTitle = mResources.getString(R.string.browse_musics_by_album_subtitle,
                    MediaIDHelper.extractBrowseCategoryValueFromMediaID(mediaId));
            setCurrentQueue(queueTitle,
                    QueueHelper.getPlayingQueue(mediaId, mMusicProvider), mediaId);
        }
        updateMetadata();
    }


    public void updateMetadata() {
        MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();
        if (currentMusic == null) {
            mListener.onMetadataRetrieveError();
            return;
        }
        final String musicId = MediaIDHelper.extractMusicIDFromMediaID(
                currentMusic.getDescription().getMediaId());
        MediaMetadataCompat metadata = mMusicProvider.getMusic(musicId);
        if (metadata == null) {
            throw new IllegalArgumentException("Invalid musicId " + musicId);
        }

        mListener.onMetadataChanged(metadata);

        // Set the proper album artwork on the media session, so it can be shown in the
        // locked screen and in other places.
        if (metadata.getDescription().getIconBitmap() == null &&
                metadata.getDescription().getIconUri() != null) {
            String albumUri = metadata.getDescription().getIconUri().toString();
            AlbumArtCache.getInstance().fetch(albumUri, new AlbumArtCache.FetchListener() {
                @Override
                public void onFetched(String artUrl, Bitmap bitmap, Bitmap icon) {
                    mMusicProvider.updateMusicArt(musicId, bitmap, icon);

                    // If we are still playing the same music, notify the listeners:
                    MediaSessionCompat.QueueItem currentMusic = getCurrentMusic();
                    if (currentMusic == null) {
                        return;
                    }
                    String currentPlayingId = MediaIDHelper.extractMusicIDFromMediaID(
                            currentMusic.getDescription().getMediaId());
                    if (musicId.equals(currentPlayingId)) {
                        mListener.onMetadataChanged(mMusicProvider.getMusic(currentPlayingId));
                    }
                }
            });
        }
    }


    public MediaSessionCompat.QueueItem getCurrentMusic() {
        if (!QueueHelper.isIndexPlayable(mCurrentIndex, mPlayingQueue)) {
            return null;
        }
        return mPlayingQueue.get(mCurrentIndex);
    }

    public interface MetadataUpdateListener {
        void onMetadataChanged(MediaMetadataCompat metadata);
        void onMetadataRetrieveError();
        void onCurrentQueueIndexUpdated(int queueIndex);
        void onQueueUpdated(String title, List<MediaSessionCompat.QueueItem> newQueue);
    }
}
