package com.liskovsoft.smartyoutubetv2.mobile.ui.base;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Shared show/hide-system-bars logic for the phone flavor's two window "roots" - the regular
 * {@link MobileActivity} screens (Home, search, channel, ...) and {@link
 * com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackFragment}, which isn't a
 * MobileActivity subclass (it hosts on the shared TV PlaybackActivity) but needs the exact same
 * behavior for its non-full-screen states.
 *
 * Deliberately uses ONLY the modern {@code OnApplyWindowInsetsListener}/{@code
 * WindowInsetsCompat} path, not the legacy {@code View.setFitsSystemWindows()} flag: mixing the
 * two is a documented footgun - once {@code WindowCompat.setDecorFitsSystemWindows(window, false)}
 * has been called even once on this window (which {@link #hide} does, for landscape full-screen
 * playback), the platform stops automatically dispatching insets through the legacy
 * fitsSystemWindows path for the rest of that window's lifetime, even after switching back to
 * {@code setDecorFitsSystemWindows(true)}. The listener path works regardless of that history.
 */
public class SystemBarsHelper {
    private static final String TAG = "SystemBarsHelper";

    private SystemBarsHelper() {
    }

    /** Shows the system status/navigation bars and has {@code root} reserve space for them. */
    public static void show(Window window, View root) {
        Log.d(TAG, "show() root=" + root + " attached=" + (root != null && root.isAttachedToWindow()));
        WindowCompat.setDecorFitsSystemWindows(window, true);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.show(WindowInsetsCompat.Type.systemBars());
        applyBarsPadding(root, true);
    }

    /**
     * Hides the system status/navigation bars (swipe-to-reveal-temporarily still works) and lets
     * the content run edge-to-edge again.
     */
    public static void hide(Window window, View root) {
        WindowCompat.setDecorFitsSystemWindows(window, false);
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());
        applyBarsPadding(root, false);
    }

    /** Convenience overload: applies to the first (and typically only) child of {@code android.R.id.content}. */
    public static void show(Window window, ViewGroup contentRoot) {
        if (contentRoot != null && contentRoot.getChildCount() > 0) {
            show(window, contentRoot.getChildAt(0));
        }
    }

    private static void applyBarsPadding(View root, boolean visible) {
        if (root == null) {
            Log.d(TAG, "applyBarsPadding: root is null, bailing");
            return;
        }
        // Registered every call (cheap, idempotent) so it's always current for whichever state
        // (show/hide) is active - a stale listener from a previous call would otherwise keep
        // re-applying the wrong padding on every future system-driven insets dispatch (keyboard,
        // rotation, cutout change, ...).
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            if (visible) {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
                Log.d(TAG, "listener fired: visible=true bars=" + bars);
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                Log.d(TAG, "listener fired: visible=false, clearing padding");
                v.setPadding(0, 0, 0, 0);
            }
            return insets;
        });
        // requestApplyInsets() only queues a dispatch for the NEXT insets pass, which the system
        // may not actually redeliver if nothing about the window's insets state itself changed
        // (verified on device: re-showing the bars after they'd been hidden, with no intervening
        // size/config change, never fired the listener at all). Applying the window's current
        // insets synchronously here as well guarantees the padding lands immediately either way.
        WindowInsetsCompat current = ViewCompat.getRootWindowInsets(root);
        Log.d(TAG, "applyBarsPadding: visible=" + visible + " currentInsets=" + current);
        if (current != null) {
            if (visible) {
                Insets bars = current.getInsets(WindowInsetsCompat.Type.systemBars());
                Log.d(TAG, "sync-applying bars=" + bars + " to root=" + root);
                root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                root.setPadding(0, 0, 0, 0);
            }
        } else {
            Log.d(TAG, "getRootWindowInsets returned null - view not attached yet?");
        }
        ViewCompat.requestApplyInsets(root);
    }
}
