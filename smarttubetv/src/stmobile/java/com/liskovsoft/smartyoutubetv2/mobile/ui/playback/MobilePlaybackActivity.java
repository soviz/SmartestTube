package com.liskovsoft.smartyoutubetv2.mobile.ui.playback;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.MotionEvent;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.tv.R;
import com.liskovsoft.smartyoutubetv2.tv.ui.playback.PlaybackActivity;

/**
 * Phone playback host for the stmobile flavor.
 *
 * Reuses the shared {@link PlaybackActivity} (the whole player — engine, controls, PIP —
 * is kept intact) and adds the phone behaviors: screen orientation by video type (Shorts lock
 * portrait, regular videos rotate freely for the upright strip layout) and touch-friendly
 * overlay handling (a tap on the faded player only reveals the controls — see
 * {@link MobilePlaybackFragment#interceptPlayerTouch}).
 *
 * Known limitation: orientation is decided when the activity is created / re-entered, so a
 * single player session that crosses a Short/regular-video boundary (e.g. autoplay) keeps
 * the first video's orientation until the activity is recreated.
 */
public class MobilePlaybackActivity extends PlaybackActivity {
    private MobilePlaybackFragment mMobileFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        // Must run BEFORE super.onCreate()'s setContentView() so the very first layout pass
        // already reserves space for the system bars (matches the portrait/strip state
        // MobilePlaybackFragment.applySystemBarsVisibility applies at onResume). Setting this
        // only from that later runtime call left a one-frame gap where the window was still in
        // its default edge-to-edge fit mode - the content laid out under the status bar, then
        // jumped down by the bar's height once the fragment's own call landed a frame or two
        // later. The fragment's call still runs afterwards (for the actual show/hide + landscape
        // full-screen toggling), this just makes the initial state consistent with it.
        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);

        super.onCreate(savedInstanceState);

        Fragment fragment =
                getSupportFragmentManager().findFragmentByTag(getString(R.string.playback_tag));
        if (fragment instanceof MobilePlaybackFragment) {
            mMobileFragment = (MobilePlaybackFragment) fragment;
        }

        applyOrientationForCurrentVideo();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        applyOrientationForCurrentVideo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Confirmed via logcat (InsetsController's from= stack traces): on a cold player start
        // the platform itself hides the status bar shortly after this - the OS's own
        // ViewRootImpl.controlInsetsForCompatibility runs during WindowManagerGlobal.addView,
        // which (per the captured stack) happens AFTER Activity.onResume() returns, as part of
        // ActivityThread.handleResumeActivity - so a show() called directly here still gets
        // clobbered by that later compatibility hide. MobilePlaybackFragment's own
        // applySystemBarsVisibility() was only re-showing the bar once the video's metadata
        // arrived via setVideo(), up to ~1.5s later on a slow load - that gap is the "status bar
        // appears late and pushes the strip down" jump. Posting this to the decor view's message
        // queue runs it on the very next looper pass, once addView (and its compatibility hide)
        // has already completed, so the show() actually sticks instead of being immediately
        // overwritten. applyMobileLayout() still runs its own show/hide afterwards for the actual
        // landscape/PiP/Shorts logic.
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            getWindow().getDecorView().post(() -> {
                WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
                new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView())
                        .show(WindowInsetsCompat.Type.systemBars());
            });
        }
    }

    /**
     * Phone Back UX (issue #23): pressing Back leaves the video for good.
     *
     * The shared {@link PlaybackActivity#finish()} keeps the player engine alive on Back when
     * background audio is on (the phone default, {@code BACKGROUND_MODE_SOUND}) and navigates to
     * the player's "parent view". That left audio playing after Back and parked a still-running
     * player activity beneath the channel page — pressing Back on the channel then revealed the
     * live player again, a channel↔player loop. Here we instead stop playback and finish the
     * player ({@link #finishReally()}), so Back drops back to the previous screen with no lingering
     * audio and nothing to loop into.
     *
     * Left to the base class:
     * - PIP (either already in PIP, or the user picked {@code BACKGROUND_MODE_PIP}) — Back should
     *   enter/keep PIP, an explicit opt-in feature, not be hijacked into a stop.
     * - Home / lock-screen background audio — that runs through onUserLeaveHint/onStop, a different
     *   path that this override doesn't touch, so listening with the screen off still works.
     */
    @Override
    public void onBackPressed() {
        if (isInPipMode() || getPlayerData().getBackgroundMode() == PlayerData.BACKGROUND_MODE_PIP) {
            super.onBackPressed();
            return;
        }

        finishReally();
    }

    /**
     * Swipe-down-to-minimize (issue: "windowed mode like the official YouTube app"): leaves the
     * player screen while keeping the engine alive and playback running, so the user is free to
     * navigate the rest of the app underneath.
     *
     * There's no safe way to keep this Activity's ExoPlayer instance alive while genuinely handing
     * the foreground to another Activity in the same task other than the platform's own mechanism
     * for that: real Android Picture-in-Picture. blockEngine(true) is what stops the shared
     * {@link PlaybackFragment}'s onStop() from releasing the player (see maybeReleasePlayer()); the
     * inherited {@link PlaybackActivity#finish()} (called as {@code finish()}, not
     * {@code super.finish()}, so the override below actually runs) then sees isEngineBlocked() via
     * doNotDestroy()/wannaEnterToPip() and pins this activity into a small floating PIP window
     * instead of destroying it - the video keeps playing in that window while Home (already
     * sitting under this activity in the shared phone task) becomes free to use normally,
     * including navigating between its own screens.
     */
    public void enterWindowedMode() {
        if (mMobileFragment != null) {
            mMobileFragment.blockEngine(true);
        }
        finish();
    }

    /**
     * PIP-close fix: don't relaunch Home when the PIP window is dismissed (the X) or when Back
     * finishes the player normally.
     *
     * {@code LeanbackActivity.finishReally()} calls {@code getViewManager().startParentView(this)}
     * before tearing the activity down - a TV-only need, since TV activities are
     * {@code singleInstance} (each in its own task) and Back has nothing to reveal without
     * explicitly launching the parent. On the phone all activities share (or, for the player, sit
     * on top of) one task, so Android already reveals whatever was underneath once this activity
     * finishes; {@code startParentView} is not just redundant here, it actively breaks navigation:
     * it does a blind {@code Stack.pop()} on the ViewManager's activity stack, which - if the
     * player was already removed from that stack on PIP entry (see {@link #startParentViewOnPip()})
     * - pops whatever real caller (e.g. Search) is now on top instead, so the fallback to the
     * hardcoded default parent (Home) kicks in and Home opens instead of the caller.
     */
    @Override
    protected boolean shouldStartParentViewOnFinish() {
        return false;
    }

    /**
     * PIP fix (#33): don't relaunch Home when the player drops into PIP.
     *
     * The shared base launches the parent activity here. On the phone, Home
     * ({@code MobileBrowseActivity}) is {@code singleTask} and already sits at the root of this
     * task, so it is revealed automatically the moment the player pins to a PIP window. Relaunching
     * it races with that task-pinning transition and can clear the player out of the task — the
     * player is destroyed, its engine released, and the pop-up shows Home instead of the video.
     *
     * We still do the one thing {@code startParentView} does that matters here: drop the player
     * from the ViewManager stack, so returning from PIP resolves correctly.
     */
    @Override
    protected void startParentViewOnPip() {
        getViewManager().removeTop(this);
    }

    /**
     * PIP audio-leak fix: stop playback when the PIP window is dismissed (the X).
     *
     * Entering PIP calls {@code blockEngine(true)} so the ExoPlayer survives the activity being
     * stopped — that's what keeps the video playing in the pop-up ({@code maybeReleasePlayer()}
     * deliberately no-ops while the engine is blocked). But closing the PIP window does NOT finish
     * this activity: the overridden {@link PlaybackActivity#finish()} treats a blocked engine as
     * "keep playing", so the activity just parks in the stopped state ({@code onStop} runs with
     * {@code isFinishing() == false} and no {@code onDestroy}) and the blocked engine keeps
     * playing audio behind the now-closed window.
     *
     * The reliable discriminator (verified on device) is the lifecycle state when PIP mode ends:
     * dismissing the window delivers {@code onPictureInPictureModeChanged(false)} while the activity
     * is only CREATED (stopped, not returning to the foreground); expanding PIP back to fullscreen
     * delivers it at STARTED/RESUMED. So on the dismiss case we unblock the engine and finish for
     * real ({@link #finishReally()} releases the now-unblocked player and tears the activity down).
     * Screen-off / Home background audio in {@code BACKGROUND_MODE_SOUND} never enters PIP, so this
     * path doesn't touch it.
     */
    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode);

        if (!isInPictureInPictureMode
                && !getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.STARTED)) {
            if (mMobileFragment != null) {
                mMobileFragment.blockEngine(false);
            }
            finishReally();
        }
    }

    /**
     * Programmatic equivalent of dismissing the PIP window (the X): same unblock-then-finishReally
     * sequence as {@link #onPictureInPictureModeChanged}, for callers (e.g. opening Settings) that
     * need PIP closed on demand rather than waiting for the system callback.
     */
    public void exitPip() {
        if (mMobileFragment != null) {
            mMobileFragment.blockEngine(false);
        }
        finishReally();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        // While the overlay is faded out its buttons are still hit-testable (Leanback hides by
        // alpha, not visibility) — consume the tap so it only reveals the controls instead of
        // pressing an invisible button.
        if (mMobileFragment != null && mMobileFragment.interceptPlayerTouch(event)) {
            return true;
        }

        return super.dispatchTouchEvent(event);
    }

    private void applyOrientationForCurrentVideo() {
        Video video = PlaybackPresenter.instance(this).getVideo();

        if (video != null && video.isShorts) {
            // Shorts are 9:16 — lock to portrait so they fill the screen.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            // Regular videos rotate freely: landscape = full-screen, upright = 16:9 strip with
            // the up-next panel below (MobilePlaybackFragment.applyMobileLayout). The manifest
            // lists "orientation" in configChanges, so rotation never recreates the activity
            // (no rebuffer) — the fragment just re-applies constraints.
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_USER);
        }
    }
}
