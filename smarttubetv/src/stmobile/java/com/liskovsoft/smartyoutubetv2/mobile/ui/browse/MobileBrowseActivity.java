package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.liskovsoft.smartyoutubetv2.mobile.ui.base.MobileActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileDefaultsMigration;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Host for the native phone Home screen. Replaces the TV BrowseActivity for the
 * stmobile flavor (wired in {@link com.liskovsoft.smartyoutubetv2.mobile.ui.main.MobileApplication}).
 */
public class MobileBrowseActivity extends MobileActivity {
    private static final int REQ_POST_NOTIFICATIONS = 1001;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        MobileDefaultsMigration.applyOnce(this);
        setContentView(R.layout.mobile_browse_activity);

        if (getSupportFragmentManager().findFragmentById(R.id.mobile_browse_root) == null) {
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.mobile_browse_root, new MobileBrowseFragment())
                    .commit();
        }
    }

    /**
     * Ask for the Android 13+ POST_NOTIFICATIONS runtime permission. No-op below API 33 (granted at
     * install) or when already granted. Called when the user turns on Upload notifications from
     * Settings — posting silently no-ops without it, so this is best-effort and never blocks the UI.
     */
    public void requestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_POST_NOTIFICATIONS);
    }
}
