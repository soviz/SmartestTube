package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;

/**
 * In-memory hand-off for a single {@link VideoGroup} between the Music folder grid and
 * the folder-details screen. VideoGroup/Video aren't Parcelable, and the group is already
 * fully loaded on the Music screen, so this avoids re-fetching or serializing it just to
 * cross the Activity boundary. Single slot is enough: the details screen reads it once in
 * onViewCreated, immediately after the folder tap that set it.
 */
public class MusicFolderStore {
    private static VideoGroup sPendingGroup;

    public static void setPendingGroup(VideoGroup group) {
        sPendingGroup = group;
    }

    public static VideoGroup takePendingGroup() {
        VideoGroup group = sPendingGroup;
        sPendingGroup = null;
        return group;
    }
}
