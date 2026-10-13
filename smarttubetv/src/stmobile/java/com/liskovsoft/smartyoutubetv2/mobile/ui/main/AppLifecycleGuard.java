package com.liskovsoft.smartyoutubetv2.mobile.ui.main;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.liskovsoft.sharedutils.mylogger.Log;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.misc.MotherActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.browse.MobileBrowseActivity;
import com.liskovsoft.smartyoutubetv2.tv.ui.main.SplashActivity;

/**
 * Keeps the phone app from running in a half-initialized or stale state after a long idle.
 *
 * 1) Process-death restore. All one-time app init (proxy, VideoStateService, account selection,
 *    background warm-up, ...) lives in SplashPresenter. When Android kills the idle process and the
 *    user returns via Recents (or a START_STICKY service revives the process), the system recreates
 *    the top activity directly and Splash never runs. Any MotherActivity created before Splash in
 *    this process is therefore finished together with its task and the app is re-entered through
 *    Splash - a real cold start.
 *
 * 2) Stale session. If the UI comes back to the foreground after more than {@link #STALE_SESSION_MS}
 *    in the background and nothing is playing, the whole task is replaced with a fresh Home screen
 *    (fresh feed, no stale player/fragments). The process itself is not killed: a self-kill would
 *    leave the task in Recents and lead straight back into case 1.
 */
final class AppLifecycleGuard implements Application.ActivityLifecycleCallbacks {
    private static final String TAG = AppLifecycleGuard.class.getSimpleName();
    private static final long STALE_SESSION_MS = 60 * 60 * 1_000L;
    private static final String LAUNCHERS_PACKAGE = "com.liskovsoft.smartyoutubetv2.tv.launchers.";

    private final Context mAppContext;
    private boolean mSplashSeen;
    private boolean mRedirecting;
    private int mStartedCount;
    /** elapsedRealtime() when the last visible activity stopped, 0 while in foreground. */
    private long mBackgroundSinceMs;

    AppLifecycleGuard(@NonNull Context context) {
        mAppContext = context.getApplicationContext();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {
        if (activity instanceof SplashActivity) {
            mSplashSeen = true;
            mRedirecting = false;
            return;
        }

        // Launcher shortcuts are entry points of their own (they forward to Splash themselves).
        if (mSplashSeen || !(activity instanceof MotherActivity)
                || activity.getClass().getName().startsWith(LAUNCHERS_PACKAGE)) {
            return;
        }

        // Created before Splash in this process: restored after process death.
        Log.d(TAG, "Activity %s created without Splash. Restarting through Splash...", activity.getClass().getSimpleName());
        activity.finishAffinity();

        if (!mRedirecting) {
            mRedirecting = true;
            Intent intent = new Intent(mAppContext, SplashActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        }
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        mStartedCount++;

        // Splash finishes itself right away - let the activity it opens make the decision.
        if (mBackgroundSinceMs == 0 || activity.isFinishing()) {
            return;
        }

        long idleMs = SystemClock.elapsedRealtime() - mBackgroundSinceMs;
        mBackgroundSinceMs = 0;

        if (idleMs < STALE_SESSION_MS || mRedirecting) {
            return;
        }

        if (PlaybackPresenter.instance(mAppContext).isPlaying()) {
            return; // background audio / PIP still running - the session is alive
        }

        Log.d(TAG, "Session idle for %s min. Restarting Home...", idleMs / 60_000);
        Intent intent = new Intent(mAppContext, MobileBrowseActivity.class);
        // Destroys every activity of the task (incl. a paused player) and creates a fresh Home.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        mStartedCount = Math.max(0, mStartedCount - 1);

        if (mStartedCount == 0 && !activity.isChangingConfigurations()) {
            mBackgroundSinceMs = SystemClock.elapsedRealtime();
        }
    }

    @Override
    public void onActivityResumed(@NonNull Activity activity) {
        // NOP
    }

    @Override
    public void onActivityPaused(@NonNull Activity activity) {
        // NOP
    }

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {
        // NOP
    }

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {
        // NOP
    }
}
