package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.os.Bundle;

import androidx.annotation.Nullable;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the Music folder-details screen: the video grid behind a single folder card
 * on the Music tab. The group to show is handed off in-memory via
 * {@link MusicFolderStore#setPendingGroup} right before this activity is started (see
 * {@code MobileBrowseFragment}'s folder click handler) — it isn't Parcelable and is
 * already fully loaded, so there's nothing to fetch or serialize here.
 */
public class MobileMusicFolderActivity extends MobileActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mobile_music_folder_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_music_folder_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_music_folder_root, new MobileMusicFolderFragment())
                    .commit();
        }
    }

    @Override
    public void onBackPressed() {
        getViewManager().removeTop(this);
        super.onBackPressed();
    }

    @Override
    public void finish() {
        getViewManager().removeTop(this);
        MusicFolderStore.clearPendingGroup();
        super.finish();
    }
}
