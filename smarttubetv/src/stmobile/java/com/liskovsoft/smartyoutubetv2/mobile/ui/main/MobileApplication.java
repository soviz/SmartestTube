package com.liskovsoft.smartyoutubetv2.mobile.ui.main;

import android.content.SharedPreferences;

import com.liskovsoft.smartyoutubetv2.common.app.views.AddDeviceView;
import com.liskovsoft.smartyoutubetv2.common.app.views.AppDialogView;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelUploadsView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ChannelView;
import com.liskovsoft.smartyoutubetv2.common.app.views.PlaybackView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SearchView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SignInView;
import com.liskovsoft.smartyoutubetv2.common.app.views.SplashView;
import com.liskovsoft.smartyoutubetv2.common.app.views.ViewManager;
import com.liskovsoft.smartyoutubetv2.common.app.views.WebBrowserView;
import com.liskovsoft.smartyoutubetv2.common.app.models.playback.manager.PlayerConstants;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerTweaksData;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.channel.MobileChannelActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.channeluploads.MobileChannelUploadsActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.dialogs.MobileAppDialogActivity;
import com.liskovsoft.smartyoutubetv2.mobile.notifications.NotificationPollWorker;
import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileNotificationPrefs;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileThemePrefs;
import com.liskovsoft.smartyoutubetv2.mobile.ui.search.MobileSearchActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.signin.MobileSignInActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.adddevice.AddDeviceActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.MainApplication;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.webbrowser.WebBrowserActivity;

/**
 * Application class for the phone (stmobile) flavor.
 *
 * Overrides view routing so the phone build uses its own native portrait screens
 * ({@link MobileBrowseActivity}, {@link MobileSearchActivity}, ...) instead of the TV
 * Leanback ones. The remaining screens are reused from the TV code unchanged (later
 * phases will replace them in turn).
 */
public class MobileApplication extends MainApplication {
    @Override
    public void onCreate() {
        // Apply the persisted Theme choice (System / Light / Dark) before any activity
        // is created so the first frame uses the correct values/ vs values-night/ tokens
        // - avoids a recreate-flicker on cold start.
        MobileThemePrefs.apply(this);
        super.onCreate();

        // Notifications section/feature removed from the app. Force the push toggle off
        // regardless of the previously saved user choice, and make sure no poll is scheduled.
        MobileNotificationPrefs.setEnabled(this, false);
        NotificationPollWorker.schedule(this);

        hideScreenDimmingButtonOnce();
        defaultBackgroundPlaybackOnce();
    }

    /**
     * Default phones to background audio playback. Upstream defaults to BACKGROUND_MODE_DEFAULT,
     * which releases the player when the screen turns off — so locking the phone stops playback
     * and no lockscreen media controls appear. Phone users expect audio to keep playing, so we set
     * BACKGROUND_MODE_SOUND once. Done once so a user who deliberately changes it keeps their
     * choice. stmobile-only; shared common code is untouched, keeping the fork upstream-mergeable.
     */
    private void defaultBackgroundPlaybackOnce() {
        SharedPreferences prefs = getSharedPreferences("mobile_player_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("background_sound_defaulted", false)) {
            PlayerData.instance(this).setBackgroundMode(PlayerConstants.BACKGROUND_MODE_SOUND);
            prefs.edit().putBoolean("background_sound_defaulted", true).apply();
        }
    }

    /**
     * Hide the Screen Dimming player button on phones. It blacks the screen for audio-only /
     * battery saving with a TV remote, but on a phone it reads as a broken control (it just turns
     * the screen black) — and you can simply lock the phone instead. Done once so a user who
     * deliberately re-enables it in player settings keeps it. stmobile-only; shared common code
     * (the button is added by the TV {@code VideoPlayerGlue}) is untouched, keeping the fork
     * upstream-mergeable.
     */
    private void hideScreenDimmingButtonOnce() {
        SharedPreferences prefs = getSharedPreferences("mobile_player_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean("screen_dimming_hidden", false)) {
            PlayerTweaksData.instance(this).setPlayerButtonDisabled(PlayerTweaksData.PLAYER_BUTTON_SCREEN_DIMMING);
            prefs.edit().putBoolean("screen_dimming_hidden", true).apply();
        }
    }

    @Override
    protected void setupViewManager() {
        ViewManager viewManager = ViewManager.instance(this);

        viewManager.setRoot(MobileBrowseActivity.class);
        viewManager.register(SplashView.class, SplashActivity.class); // root activity, no parent
        viewManager.register(BrowseView.class, MobileBrowseActivity.class); // phone Home
        viewManager.register(PlaybackView.class, MobilePlaybackActivity.class, MobileBrowseActivity.class);
        viewManager.register(AppDialogView.class, MobileAppDialogActivity.class, MobilePlaybackActivity.class);
        viewManager.register(SearchView.class, MobileSearchActivity.class, MobileBrowseActivity.class);
        viewManager.register(SignInView.class, MobileSignInActivity.class, MobileBrowseActivity.class);
        viewManager.register(AddDeviceView.class, AddDeviceActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelView.class, MobileChannelActivity.class, MobileBrowseActivity.class);
        viewManager.register(ChannelUploadsView.class, MobileChannelUploadsActivity.class, MobileBrowseActivity.class);
        viewManager.register(WebBrowserView.class, WebBrowserActivity.class, MobileBrowseActivity.class);
    }
}
