package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

/**
 * In-memory hand-off for a single {@link VideoGroup} between the Music folder grid and
 * the folder-details screen. VideoGroup/Video aren't Parcelable, and the group is already
 * fully loaded on the Music screen, so this avoids re-fetching or serializing it just to
 * cross the Activity boundary.
 *
 * Not cleared on read: {@code ViewManager.startParentView()} (used when returning from the
 * player) can spawn a second {@link MobileMusicFolderActivity} instance on top of the
 * original one (it starts a plain new Intent rather than reusing the existing instance), so
 * onViewCreated may run more than once for the same folder visit. Clearing on first read left
 * that second instance with a null group - an empty screen with just the back button until a
 * second back press exposed the original, already-populated instance underneath. The group is
 * cleared explicitly instead, when the folder screen actually closes.
 */
public class MusicFolderStore {
    private static VideoGroup sPendingGroup;

    public static void setPendingGroup(VideoGroup group) {
        sPendingGroup = group;
    }

    public static VideoGroup getPendingGroup() {
        return sPendingGroup;
    }

    public static void clearPendingGroup() {
        sPendingGroup = null;
    }
}
