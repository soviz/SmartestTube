package com.liskovsoft.smartyoutubetv2.mobile.ui.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.ExoFormatItem;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;

/**
 * One-time, stmobile-only defaults applied on first run of this build - a deliberate, explicit
 * override of {@code PlayerData.getDefaultVideoFormat()} (which otherwise auto-picks the
 * highest resolution the device's decoder supports, often FHD/4K) and of the audio language
 * default (otherwise the device's current locale). Each default has its own applied-flag so a
 * later addition here doesn't re-apply the ones already run on a device, and neither ever
 * overwrites a value the user has since changed themselves in Settings.
 */
public final class MobileDefaultsMigration {
    private static final String PREFS_NAME = "mobile_defaults_migration";
    // v2: bumped because the video default could end up saved as NO_VIDEO on a device that had
    // already applied v1 (e.g. after testing the "video off by default" Settings toggle) -
    // v1's flag would then block this fix from ever re-running there.
    private static final String KEY_VIDEO_APPLIED = "video_480p_30fps_applied_v2";
    private static final String KEY_AUDIO_LANG_APPLIED = "audio_lang_original_applied";

    private MobileDefaultsMigration() {}

    public static void applyOnce(Context context) {
        SharedPreferences prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        PlayerData playerData = PlayerData.instance(context);

        if (!prefs.getBoolean(KEY_VIDEO_APPLIED, false)) {
            playerData.setFormat(ExoFormatItem.fromVideoSpec("854,480,30,avc", false));
            prefs.edit().putBoolean(KEY_VIDEO_APPLIED, true).apply();
        }

        if (!prefs.getBoolean(KEY_AUDIO_LANG_APPLIED, false)) {
            // "" is the app's own marker for "Original" (see AppDialogUtil's audio-language
            // picker, which writes/compares the same empty string for its "Original" entry) -
            // not a placeholder for "unset".
            playerData.setAudioLanguage("");
            prefs.edit().putBoolean(KEY_AUDIO_LANG_APPLIED, true).apply();
        }
    }
}
