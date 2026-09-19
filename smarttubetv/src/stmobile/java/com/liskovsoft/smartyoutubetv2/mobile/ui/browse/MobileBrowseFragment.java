package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.liskovsoft.mediaserviceinterfaces.data.MediaGroup;
import com.liskovsoft.mediaserviceinterfaces.oauth.Account;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.BrowseSection;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.models.errors.ErrorFragmentData;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.BrowsePresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.PlaybackPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.SearchPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.AccountSelectionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.settings.AccountSettingsPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.views.BrowseView;
import com.liskovsoft.smartyoutubetv2.common.exoplayer.selector.FormatItem;
import com.liskovsoft.smartyoutubetv2.common.misc.MediaServiceManager;
import com.liskovsoft.smartyoutubetv2.common.prefs.PlayerData;
import com.liskovsoft.smartyoutubetv2.mobile.notifications.NotificationPollWorker;
import com.liskovsoft.smartyoutubetv2.mobile.ui.about.MobileAboutActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.playback.MobilePlaybackActivity;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileNotificationPrefs;
import com.liskovsoft.smartyoutubetv2.mobile.ui.prefs.MobileThemePrefs;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Native portrait Home screen. Implements {@link BrowseView} and is driven by the
 * existing {@link BrowsePresenter} unchanged — all section/video-group loading logic is
 * reused from the TV code; only the view layer is new.
 *
 * ROW sections (Home, Trending, Music) render as stacked horizontal shelves; GRID
 * sections (Subscriptions, History, ...) render as a 2-column grid. The drawer menu
 * opens with an edge-swipe or the toolbar button.
 */
public class MobileBrowseFragment extends Fragment implements BrowseView, MediaServiceManager.AccountChangeListener {
    private BrowsePresenter mPresenter;
    private DrawerLayout mDrawer;
    private SwipeRefreshLayout mSwipeRefresh;
    private RecyclerView mContentList;
    private ProgressBar mProgressBar;
    private TextView mEmptyMessage;
    private View mEmptyContainer;
    private Button mEmptyButton;
    private TextView mToolbarTitle;
    private ImageView mAccountView;
    private View mBottomNavHome;
    private View mBottomNavSubscriptions;
    private View mBottomNavHistory;
    private SectionAdapter mSectionAdapter;
    private VideoCardAdapter mGridAdapter;
    private ShelfAdapter mShelfAdapter;
    private FolderCardAdapter mFolderAdapter;
    private SettingsItemAdapter mSettingsAdapter;
    private boolean mProgressShowing;
    private boolean mSwipeRefreshing;
    private boolean mSectionSelected;
    private int mCurrentSectionId = -1;
    private int mGridCardWidth;
    private int mGridSpan;
    private int mShelfCardWidth;
    // Music genre filter (see FolderCardAdapter's class doc): a folder's own title doubles
    // as its "genre" - there's no separate genre field anywhere in the data. Above this count
    // the chip row would overflow into multiple lines / require horizontal scrolling to read
    // at a glance, so it's replaced by a single dropdown button instead.
    private static final int MAX_GENRE_CHIPS = 5;
    private HorizontalScrollView mGenreChipsScroll;
    private LinearLayout mGenreChips;
    private View mGenreDropdown;
    private TextView mGenreDropdownLabel;
    private List<String> mGenres = new ArrayList<>();
    @Nullable
    private String mSelectedGenre; // null = "All"
    // Flat grid sections (Home, Subscriptions, History, ...): each VideoGroup continuation
    // arrives via VideoGroup.from(baseGroup, ...), which mutates and re-sends the SAME group
    // object with the full cumulative video list (see VideoGroupObjectAdapter.append()
    // upstream, which relies on this same contract). Track how many of each group's videos
    // are already appended to the flat mGridAdapter list, so a continuation only
    // range-inserts its new tail instead of redrawing everything.
    private final Map<VideoGroup, Integer> mGridGroupSizes = new HashMap<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_browse_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mDrawer = view.findViewById(R.id.drawer_layout);
        mContentList = view.findViewById(R.id.content_list);
        mSwipeRefresh = view.findViewById(R.id.swipe_refresh);
        mProgressBar = view.findViewById(R.id.progress_bar);
        mEmptyMessage = view.findViewById(R.id.empty_message);
        mEmptyContainer = view.findViewById(R.id.empty_container);
        mEmptyButton = view.findViewById(R.id.empty_button);
        mToolbarTitle = view.findViewById(R.id.toolbar_title);
        mGenreChipsScroll = view.findViewById(R.id.mobile_genre_chips_scroll);
        mGenreChips = view.findViewById(R.id.mobile_genre_chips);
        mGenreDropdown = view.findViewById(R.id.mobile_genre_dropdown);
        mGenreDropdownLabel = view.findViewById(R.id.mobile_genre_dropdown_label);
        mGenreDropdown.setOnClickListener(v -> showGenreDropdownMenu());

        view.findViewById(R.id.btn_menu).setOnClickListener(v -> {
            if (mDrawer.isDrawerOpen(GravityCompat.START)) {
                mDrawer.closeDrawer(GravityCompat.START);
            } else {
                mDrawer.openDrawer(GravityCompat.START);
            }
        });

        view.findViewById(R.id.btn_search).setOnClickListener(v ->
                SearchPresenter.instance(getContext()).startSearch(null));

        // Account switcher — mirrors SmartTube TV's title-bar orb (NavigateTitleView):
        // tap cycles to the next account, long-press opens account management.
        mAccountView = view.findViewById(R.id.btn_account);
        mAccountView.setOnClickListener(v ->
                AccountSelectionPresenter.instance(getContext()).nextAccountOrDialog());
        mAccountView.setOnLongClickListener(v -> {
            AccountSettingsPresenter.instance(getContext()).show();
            return true;
        });
        MediaServiceManager.instance().addAccountListener(this);
        updateAccountIcon();

        view.findViewById(R.id.drawer_about).setOnClickListener(v -> {
            if (mDrawer != null) {
                mDrawer.closeDrawer(GravityCompat.START);
            }
            startActivity(new Intent(getContext(), MobileAboutActivity.class));
        });

        // Bottom navigation bar: Home / Subscriptions / History jump straight to that section.
        mBottomNavHome = view.findViewById(R.id.bottom_nav_home);
        mBottomNavSubscriptions = view.findViewById(R.id.bottom_nav_subscriptions);
        mBottomNavHistory = view.findViewById(R.id.bottom_nav_history);
        mBottomNavHome.setOnClickListener(v -> navigateToSection(MediaGroup.TYPE_HOME));
        mBottomNavSubscriptions.setOnClickListener(v -> navigateToSection(MediaGroup.TYPE_SUBSCRIPTIONS));
        mBottomNavHistory.setOnClickListener(v -> navigateToSection(MediaGroup.TYPE_HISTORY));

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        mGridSpan = getResources().getInteger(R.integer.mobile_grid_span);
        mGridCardWidth = screenWidth / mGridSpan;
        mShelfCardWidth = (int) (screenWidth * 0.42f);

        mSectionAdapter = new SectionAdapter(this::onSectionPicked);
        RecyclerView sectionList = view.findViewById(R.id.section_list);
        sectionList.setLayoutManager(new LinearLayoutManager(getContext()));
        sectionList.setAdapter(mSectionAdapter);

        // Horizontal swipe switches between the bottom nav bar tabs (Home/Music/History).
        // See mSwipeToSwitchTab.
        mContentList.addOnItemTouchListener(mSwipeToSwitchTab);

        mSwipeRefresh.setColorSchemeResources(R.color.brand_accent);
        mSwipeRefresh.setProgressBackgroundColorSchemeResource(R.color.mobile_surface);
        mSwipeRefresh.setOnRefreshListener(() -> {
            // Settings section has nothing to reload; otherwise re-fetch the current section.
            if (mPresenter == null || mSettingsAdapter != null) {
                stopSwipeRefresh();
                return;
            }
            mSwipeRefreshing = true;
            mPresenter.refresh(false);
        });

        mPresenter = BrowsePresenter.instance(getContext());
        mPresenter.setView(this);
        mPresenter.onViewInitialized();
    }

    private void onSectionPicked(BrowseSection section) {
        if (mDrawer != null) {
            mDrawer.closeDrawer(GravityCompat.START);
        }
        selectSection(mSectionAdapter.indexOf(section), true);
    }

    /** Bottom nav bar Home/Music/History taps: jump to that section by MediaGroup id. */
    private void navigateToSection(int sectionId) {
        if (mDrawer != null) {
            mDrawer.closeDrawer(GravityCompat.START);
        }
        if (mSectionAdapter != null) {
            selectSection(mSectionAdapter.indexOfSection(sectionId), true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPresenter != null) {
            mPresenter.onViewResumed();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mPresenter != null) {
            mPresenter.onViewPaused();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        MediaServiceManager.instance().removeAccountListener(this);
        if (mPresenter != null) {
            mPresenter.onViewDestroyed();
        }
    }

    @Override
    public void onAccountChanged(Account account) {
        updateAccountIcon();
    }

    /**
     * Load the signed-in account's circular avatar into the toolbar button — the touch-UI
     * counterpart of {@code NavigateTitleView.updateAccountIcon()}. Falls back to the default
     * account placeholder when no account is selected (or it has no avatar).
     */
    private void updateAccountIcon() {
        if (mAccountView == null) {
            return;
        }

        Account current = MediaServiceManager.instance().getSelectedAccount();

        if (current != null && current.getAvatarImageUrl() != null) {
            // A real photo: drop the placeholder's monochrome tint so it shows in full colour.
            mAccountView.setImageTintList(null);
            Glide.with(this)
                    .load(current.getAvatarImageUrl())
                    .placeholder(R.drawable.browse_title_account)
                    .circleCrop()
                    .into(mAccountView);
        } else {
            Glide.with(this).clear(mAccountView);
            // Re-apply the toolbar tint so the placeholder matches the other toolbar icons.
            mAccountView.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(mAccountView.getContext(), R.color.mobile_text_primary)));
            mAccountView.setImageResource(R.drawable.browse_title_account);
        }
    }

    // ----- BrowseView -----

    @Override
    public void addSection(int index, BrowseSection section) {
        if (mSectionAdapter != null && section != null) {
            mSectionAdapter.add(index, section);
            // Auto-open a default section so the user lands on videos rather than an empty
            // "pick a section" Home — notably after sign-in, where the section list is
            // rebuilt. Deferred via post() so a presenter-driven selection during the same
            // load wins (it sets mSectionSelected first); otherwise we open the first
            // section (the Home feed). Re-arms on removeAllSections (a full refresh).
            if (!mSectionSelected && mContentList != null) {
                mContentList.post(this::autoSelectDefaultSection);
            }
        }
    }

    private void autoSelectDefaultSection() {
        if (mSectionSelected || mSectionAdapter == null || mSectionAdapter.getItemCount() == 0) {
            return;
        }
        selectSection(0, false);
    }

    @Override
    public void removeSection(BrowseSection section) {
        if (mSectionAdapter != null) {
            mSectionAdapter.remove(section);
        }
    }

    @Override
    public void removeAllSections() {
        if (mSectionAdapter != null) {
            mSectionAdapter.clear();
        }
        // Section list is being rebuilt — re-arm the default-section auto-open.
        mSectionSelected = false;
        mCurrentSectionId = -1;
    }

    @Override
    public void selectSection(int index, boolean focusOnContent) {
        if (mSectionAdapter == null || index < 0 || index >= mSectionAdapter.getItemCount()) {
            return;
        }
        BrowseSection section = mSectionAdapter.getItem(index);
        mSectionSelected = true;
        mCurrentSectionId = section.getId();
        mSectionAdapter.setSelected(section);
        if (mToolbarTitle != null) {
            mToolbarTitle.setText(section.getTitle());
        }
        updateBottomNavSelection(section.getId());
        setupContentForType(section);
        if (mPresenter != null) {
            mPresenter.onSectionFocused(section.getId());
        }
    }

    /** Highlights the bottom nav bar tab matching the now-selected section, if any. */
    private void updateBottomNavSelection(int sectionId) {
        setBottomNavSelected(mBottomNavHome, sectionId == MediaGroup.TYPE_HOME);
        setBottomNavSelected(mBottomNavSubscriptions, sectionId == MediaGroup.TYPE_SUBSCRIPTIONS);
        setBottomNavSelected(mBottomNavHistory, sectionId == MediaGroup.TYPE_HISTORY);
    }

    private void setBottomNavSelected(View tab, boolean selected) {
        if (tab != null) {
            tab.setSelected(selected);
        }
    }

    @Override
    public void updateSection(VideoGroup group) {
        if (group == null || mContentList == null) {
            return;
        }
        switch (group.getAction()) {
            case VideoGroup.ACTION_REPLACE:
                if (mShelfAdapter != null) mShelfAdapter.clear();
                if (mGridAdapter != null) mGridAdapter.clear();
                if (mFolderAdapter != null) mFolderAdapter.clear();
                mGridGroupSizes.clear();
                mGenres = new ArrayList<>();
                mSelectedGenre = null;
                hideGenreFilter();
                hideEmptyMessage();
                break;
            case VideoGroup.ACTION_REMOVE:
            case VideoGroup.ACTION_REMOVE_AUTHOR:
                if (mGridAdapter != null) mGridAdapter.remove(group.getVideos());
                if (mShelfAdapter != null) mShelfAdapter.removeVideos(group.getVideos());
                break;
            case VideoGroup.ACTION_SYNC:
                // Percent-watched markers only; not rendered natively yet.
                break;
            default: // ACTION_APPEND / ACTION_PREPEND
                if (group.isEmpty()) {
                    break;
                }
                hideEmptyMessage();
                if (mFolderAdapter != null) {
                    // One folder card per group (Music): don't unpack its videos into the
                    // grid, just add/refresh the card.
                    mFolderAdapter.appendGroup(group);
                } else if (mShelfAdapter != null) {
                    mShelfAdapter.appendGroup(group);
                } else if (mGridAdapter != null) {
                    appendGridGroup(group);
                }
                break;
        }
    }

    @Override
    public void updateSection(SettingsGroup group) {
        if (group == null || mContentList == null) {
            return;
        }
        hideEmptyMessage();
        mShelfAdapter = null;
        mGridAdapter = null;
        mFolderAdapter = null;
        mSettingsAdapter = new SettingsItemAdapter(prependThemeRow(group.getItems()));
        mContentList.setLayoutManager(new LinearLayoutManager(getContext()));
        mContentList.setAdapter(mSettingsAdapter);
    }

    /**
     * Prepend a phone-only "Theme" row to the upstream Settings list. Upstream's
     * ColorScheme picker is TV-only (all 8 schemes are dark variants) and isn't
     * surfaced in the stmobile UI, so this is a dedicated Day-Night toggle that
     * drives {@link MobileThemePrefs}.
     */
    private List<SettingsItem> prependThemeRow(List<SettingsItem> upstreamItems) {
        Context context = getContext();
        List<SettingsItem> items = new ArrayList<>();
        if (context != null) {
            items.add(new SettingsItem(
                    context.getString(R.string.mobile_theme_title),
                    this::showThemePicker,
                    R.drawable.settings_theme));
            items.add(SettingsItem.forSwitch(
                    context.getString(R.string.mobile_video_off_title),
                    R.drawable.settings_video_off,
                    isVideoOffByDefault(context),
                    this::setVideoOffByDefault));
            items.add(new SettingsItem(
                    context.getString(R.string.mobile_notifications_title),
                    this::showNotificationsToggle,
                    R.drawable.settings_notifications));
        }
        if (upstreamItems != null) {
            // Drop upstream's "About" row: it's the TV-oriented About panel (dead
            // "Check for updates", the meaningless ATV/Amazon "global search" bridge). The
            // phone build's own About screen in the drawer footer (MobileAboutActivity) is
            // the single About surface.
            String aboutTitle = context != null ? context.getString(R.string.settings_about) : null;
            for (SettingsItem item : upstreamItems) {
                if (aboutTitle != null && aboutTitle.equals(item.title)) {
                    continue;
                }
                items.add(item);
            }
        }
        return items;
    }

    private void showThemePicker() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        MobileThemePrefs.Mode[] modes = MobileThemePrefs.Mode.values();
        String[] labels = {
                context.getString(R.string.mobile_theme_option_system),
                context.getString(R.string.mobile_theme_option_light),
                context.getString(R.string.mobile_theme_option_dark),
        };
        MobileThemePrefs.Mode current = MobileThemePrefs.getMode(context);
        int checked = current.ordinal();
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_theme_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    if (modes[which] == current) {
                        dialog.dismiss();
                        return;
                    }
                    MobileThemePrefs.setMode(context, modes[which]);
                    dialog.dismiss();
                    // MotherActivity is a FragmentActivity (not AppCompatActivity), so
                    // setDefaultNightMode does not auto-recreate. MobileActivity reads
                    // the pref in attachBaseContext, so a manual recreate() pulls in the
                    // new uiMode override.
                    if (getActivity() != null) {
                        getActivity().recreate();
                    }
                })
                .show();
    }

    /**
     * Phone-only explicit on/off toggle for "video off by default" - a plain Settings row
     * instead of burying this in the quality-preset picker, where NO_VIDEO isn't even offered
     * as a choice. Reads/writes the same {@link PlayerData#setFormat} state the in-player
     * video-off button and {@code VideoLoaderController}'s per-video default format use, so
     * there's a single source of truth: turning this on here is equivalent to toggling video
     * off in the player and leaving it off, and toggling it off in the player doesn't drift
     * out of sync with this row (both read/write PlayerData.TYPE_VIDEO the same way).
     */
    private boolean isVideoOffByDefault(Context context) {
        return FormatItem.NO_VIDEO.equals(PlayerData.instance(context).getFormat(FormatItem.TYPE_VIDEO));
    }

    private void setVideoOffByDefault(boolean turnOn) {
        Context context = getContext();
        if (context == null) {
            return;
        }
        PlayerData.instance(context).setFormat(turnOn ? FormatItem.NO_VIDEO : FormatItem.VIDEO_AUTO);
    }

    /**
     * Phone-only "Upload notifications" on/off toggle (Part 2 — push). Drives
     * {@link MobileNotificationPrefs} and (re)schedules {@link NotificationPollWorker}. On enable,
     * asks for the Android 13+ POST_NOTIFICATIONS permission via the host activity.
     */
    private void showNotificationsToggle() {
        Context context = getContext();
        if (context == null) {
            return;
        }
        String[] labels = {
                context.getString(R.string.mobile_notifications_option_off),
                context.getString(R.string.mobile_notifications_option_on),
        };
        boolean enabled = MobileNotificationPrefs.isEnabled(context);
        int checked = enabled ? 1 : 0;
        new AlertDialog.Builder(context)
                .setTitle(R.string.mobile_notifications_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    boolean turnOn = which == 1;
                    dialog.dismiss();
                    if (turnOn == enabled) {
                        return;
                    }
                    MobileNotificationPrefs.setEnabled(context, turnOn);
                    NotificationPollWorker.schedule(context);
                    if (turnOn && getActivity() instanceof MobileBrowseActivity) {
                        ((MobileBrowseActivity) getActivity()).requestPostNotificationsPermission();
                    }
                })
                .show();
    }

    @Override
    public void clearSection(BrowseSection section) {
        if (mShelfAdapter != null) mShelfAdapter.clear();
        if (mGridAdapter != null) mGridAdapter.clear();
        if (mFolderAdapter != null) mFolderAdapter.clear();
        mGridGroupSizes.clear();
        mGenres = new ArrayList<>();
        mSelectedGenre = null;
        hideGenreFilter();
    }

    @Override
    public void selectSectionItem(int index) {
        // Touch UI: no D-pad selection to restore.
    }

    @Override
    public void selectSectionItem(Video item) {
        // Touch UI: no D-pad selection to restore.
    }

    @Override
    public void showError(ErrorFragmentData data) {
        if (mEmptyContainer == null) {
            return;
        }
        mEmptyMessage.setText(data != null ? data.getMessage() : "");
        if (data != null && data.getActionText() != null) {
            mEmptyButton.setText(data.getActionText());
            mEmptyButton.setOnClickListener(v -> data.onAction());
            mEmptyButton.setVisibility(View.VISIBLE);
        } else {
            mEmptyButton.setVisibility(View.GONE);
        }
        mEmptyContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void showProgressBar(boolean show) {
        mProgressShowing = show;
        // While pull-to-refresh is active the SwipeRefreshLayout spinner stands in for
        // the centre bar; don't show both.
        if (mProgressBar != null) {
            mProgressBar.setVisibility(show && !mSwipeRefreshing ? View.VISIBLE : View.GONE);
        }
        if (!show) {
            stopSwipeRefresh();
        }
    }

    private void stopSwipeRefresh() {
        mSwipeRefreshing = false;
        if (mSwipeRefresh != null) {
            mSwipeRefresh.setRefreshing(false);
        }
    }

    @Override
    public boolean isProgressBarShowing() {
        return mProgressShowing;
    }

    @Override
    public void focusOnContent() {
        // Touch UI: no focus to move.
    }

    @Override
    public boolean isEmpty() {
        return (mGridAdapter == null || mGridAdapter.getItemCount() == 0)
                && (mShelfAdapter == null || mShelfAdapter.getItemCount() == 0)
                && (mFolderAdapter == null || mFolderAdapter.getItemCount() == 0)
                && (mSettingsAdapter == null || mSettingsAdapter.getItemCount() == 0);
    }

    @Override
    public void updateBadge() {
        // Keep the toolbar account avatar in sync (the phone counterpart of the TV account orb).
        updateAccountIcon();
    }

    // ----- helpers -----

    /**
     * Settings opening over an active PIP player leaves the pop-up floating on top of (or behind)
     * the settings UI with no way to reach it - close PIP for real (not just move it to background)
     * so opening Settings always lands on a clean screen.
     */
    private void closePipIfNeeded() {
        if (getContext() == null || !PlaybackPresenter.instance(getContext()).isInPipMode()) {
            return;
        }
        Activity activity = PlaybackPresenter.instance(getContext()).getActivity();
        if (activity instanceof MobilePlaybackActivity) {
            ((MobilePlaybackActivity) activity).exitPip();
        }
    }

    private void setupContentForType(BrowseSection section) {
        hideEmptyMessage();
        mContentList.clearOnScrollListeners();
        mGridGroupSizes.clear();
        hideGenreFilter();
        int type = section.getType();
        if (type == BrowseSection.TYPE_ROW && section.getId() == MediaGroup.TYPE_MUSIC) {
            // Music: folders (one cover card per row) instead of horizontal shelves.
            mFolderAdapter = new FolderCardAdapter(mGridCardWidth, mFolderClick);
            mFolderAdapter.setOnGenresChanged(this::updateGenreFilterUi);
            mShelfAdapter = null;
            mGridAdapter = null;
            mSettingsAdapter = null;
            mContentList.setLayoutManager(new GridLayoutManager(getContext(), mGridSpan));
            mContentList.setAdapter(mFolderAdapter);
            mContentList.addOnScrollListener(mFolderScrollListener);
            mGenres = new ArrayList<>();
            mSelectedGenre = null;
        } else if (type == BrowseSection.TYPE_ROW
                && (section.getId() == MediaGroup.TYPE_HOME || section.getId() == MediaGroup.TYPE_SUBSCRIPTIONS)) {
            // Home ("Recommended") and Subscriptions: a single full-width column instead of
            // horizontal shelves-per-topic, with lazy-load-on-scroll like the other grid sections
            // (span 1, so each card fills the screen width).
            mGridAdapter = new VideoCardAdapter(screenWidthPx(), mVideoClick, mVideoLongClick);
            mShelfAdapter = null;
            mFolderAdapter = null;
            mSettingsAdapter = null;
            mContentList.setLayoutManager(new LinearLayoutManager(getContext()));
            mContentList.setAdapter(mGridAdapter);
            mContentList.addOnScrollListener(mGridScrollListener);
        } else if (type == BrowseSection.TYPE_ROW) {
            mShelfAdapter = new ShelfAdapter(mShelfCardWidth, mVideoClick, mVideoLongClick,
                    mShelfScrollEnd);
            mFolderAdapter = null;
            mGridAdapter = null;
            mSettingsAdapter = null;
            mContentList.setLayoutManager(new LinearLayoutManager(getContext()));
            mContentList.setAdapter(mShelfAdapter);
        } else if (type == BrowseSection.TYPE_SETTINGS_GRID) {
            // The adapter is supplied separately via updateSection(SettingsGroup).
            mShelfAdapter = null;
            mGridAdapter = null;
            mFolderAdapter = null;
            closePipIfNeeded();
        } else {
            mGridAdapter = new VideoCardAdapter(mGridCardWidth, mVideoClick, mVideoLongClick);
            mShelfAdapter = null;
            mFolderAdapter = null;
            mSettingsAdapter = null;
            mContentList.setLayoutManager(new GridLayoutManager(getContext(), mGridSpan));
            mContentList.setAdapter(mGridAdapter);
            mContentList.addOnScrollListener(mGridScrollListener);
        }
    }

    // Hosting activity declares configChanges="orientation|..." so it is NOT recreated on
    // rotation; re-read the grid span (values-sw600dp-land widens it) and resize cards.
    // Only the grid section uses a GridLayoutManager; shelves/settings are left as-is.
    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mGridSpan = getResources().getInteger(R.integer.mobile_grid_span);
        mGridCardWidth = getResources().getDisplayMetrics().widthPixels / mGridSpan;
        if (mContentList != null && mContentList.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) mContentList.getLayoutManager()).setSpanCount(mGridSpan);
        }
        if (mGridAdapter != null) {
            boolean isHomeFullWidth = mContentList != null
                    && mContentList.getLayoutManager() instanceof LinearLayoutManager
                    && !(mContentList.getLayoutManager() instanceof GridLayoutManager);
            mGridAdapter.setCardWidth(isHomeFullWidth ? screenWidthPx() : mGridCardWidth);
        }
        if (mFolderAdapter != null) {
            mFolderAdapter.setCardWidth(mGridCardWidth);
        }
    }

    private final VideoCardAdapter.OnVideoAction mVideoClick = video -> {
        if (mPresenter == null) {
            return;
        }
        // On the "Channels" multi-grid section, onVideoItemSelected mirrors the TV
        // two-pane behavior: it replaces the grid with the tapped channel's uploads
        // (meant for the TV's right-hand pane). The phone has a single grid, so that
        // silently overwrites the channel list — and it stays overwritten after returning
        // from the channel page until a manual pull-to-refresh. Skip the selection step
        // here; onVideoItemClicked still opens the channel page. Every other section keeps
        // onVideoItemSelected so scroll-position memory works.
        if (!mPresenter.isMultiGridChannelUploadsSection()) {
            mPresenter.onVideoItemSelected(video);
        }
        mPresenter.onVideoItemClicked(video);
    };

    private final VideoCardAdapter.OnVideoAction mVideoLongClick = video -> {
        if (mPresenter != null) {
            mPresenter.onVideoItemLongClicked(video);
        }
    };

    private final FolderCardAdapter.OnFolderAction mFolderClick = group -> {
        if (getContext() == null) {
            return;
        }
        MusicFolderStore.setPendingGroup(group);
        startActivity(new Intent(getContext(), MobileMusicFolderActivity.class));
    };

    private final ShelfAdapter.OnShelfScrollEnd mShelfScrollEnd = last -> {
        if (mPresenter != null && last != null) {
            mPresenter.onScrollEnd(last);
        }
    };

    /**
     * Music folders: reveal the next buffered page (see {@link FolderCardAdapter#revealMore})
     * as the grid nears its end, instead of the whole burst-fetched list appearing at once.
     */
    private final RecyclerView.OnScrollListener mFolderScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy <= 0 || mFolderAdapter == null || !mFolderAdapter.hasMore()) {
                return;
            }
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            if (!(lm instanceof GridLayoutManager)) {
                return;
            }
            int lastVisible = ((GridLayoutManager) lm).findLastVisibleItemPosition();
            if (lastVisible >= mFolderAdapter.getItemCount() - 4) {
                mFolderAdapter.revealMore();
            }
        }
    };

    private final RecyclerView.OnScrollListener mGridScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            if (dy <= 0 || mProgressShowing || mGridAdapter == null) {
                return;
            }
            RecyclerView.LayoutManager lm = recyclerView.getLayoutManager();
            int lastVisible;
            if (lm instanceof GridLayoutManager) {
                lastVisible = ((GridLayoutManager) lm).findLastVisibleItemPosition();
            } else if (lm instanceof LinearLayoutManager) {
                lastVisible = ((LinearLayoutManager) lm).findLastVisibleItemPosition();
            } else {
                return;
            }
            if (lastVisible >= mGridAdapter.getItemCount() - 4) {
                Video last = mGridAdapter.getLast();
                if (last != null && mPresenter != null) {
                    mPresenter.onScrollEnd(last);
                }
            }
        }
    };

    private int screenWidthPx() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    // ----- Music genre filter -----

    private void hideGenreFilter() {
        if (mGenreChipsScroll != null) mGenreChipsScroll.setVisibility(View.GONE);
        if (mGenreDropdown != null) mGenreDropdown.setVisibility(View.GONE);
    }

    /**
     * Called by {@link FolderCardAdapter} whenever the set of known genres (distinct folder
     * titles, excluding the pinned "Liked Music" folder) changes - i.e. as new folders arrive.
     */
    private void updateGenreFilterUi(List<String> genres, @Nullable String pinnedTitle) {
        mGenres = genres;
        if (getContext() == null || mGenreChipsScroll == null || mGenreDropdown == null) {
            return;
        }
        if (genres.isEmpty()) {
            hideGenreFilter();
            return;
        }
        if (genres.size() > MAX_GENRE_CHIPS) {
            mGenreChipsScroll.setVisibility(View.GONE);
            mGenreDropdown.setVisibility(View.VISIBLE);
            mGenreDropdownLabel.setText(mSelectedGenre != null ? mSelectedGenre : getString(R.string.mobile_genre_all));
        } else {
            mGenreDropdown.setVisibility(View.GONE);
            mGenreChipsScroll.setVisibility(View.VISIBLE);
            buildGenreChips(genres);
        }
    }

    private void buildGenreChips(List<String> genres) {
        if (mGenreChips == null || getContext() == null) {
            return;
        }
        mGenreChips.removeAllViews();
        mGenreChips.addView(makeGenreChip(getString(R.string.mobile_genre_all), mSelectedGenre == null));
        for (String genre : genres) {
            mGenreChips.addView(makeGenreChip(genre, genre.equals(mSelectedGenre)));
        }
    }

    private View makeGenreChip(String title, boolean selected) {
        TextView chip = new TextView(getContext());
        chip.setText(title);
        chip.setSingleLine(true);
        chip.setEllipsize(android.text.TextUtils.TruncateAt.END);
        chip.setMaxWidth((int) (screenWidthPx() * 0.55f));
        chip.setTextSize(13);
        chip.setTextColor(ContextCompat.getColorStateList(getContext(), R.color.mobile_genre_chip_text));
        chip.setBackgroundResource(R.drawable.mobile_genre_chip_bg);
        chip.setSelected(selected);
        int hPad = (int) (12 * getResources().getDisplayMetrics().density);
        int vPad = (int) (6 * getResources().getDisplayMetrics().density);
        chip.setPadding(hPad, vPad, hPad, vPad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int marginEnd = (int) (8 * getResources().getDisplayMetrics().density);
        lp.setMargins(0, 0, marginEnd, 0);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> selectGenre(getString(R.string.mobile_genre_all).equals(title) ? null : title));
        return chip;
    }

    private void showGenreDropdownMenu() {
        if (getContext() == null || mGenreDropdown == null) {
            return;
        }
        PopupMenu menu = new PopupMenu(getContext(), mGenreDropdown);
        menu.getMenu().add(getString(R.string.mobile_genre_all));
        for (String genre : mGenres) {
            menu.getMenu().add(genre);
        }
        menu.setOnMenuItemClickListener(item -> {
            CharSequence title = item.getTitle();
            selectGenre(getString(R.string.mobile_genre_all).contentEquals(title) ? null : title.toString());
            return true;
        });
        menu.show();
    }

    private void selectGenre(@Nullable String genre) {
        mSelectedGenre = genre;
        if (mFolderAdapter != null) {
            mFolderAdapter.setGenreFilter(genre);
        }
        updateGenreFilterUi(mGenres, null);
    }

    /**
     * Appends a flat-grid VideoGroup (Home, Subscriptions, History, ...) without a full
     * {@code notifyDataSetChanged}. A continuation re-sends the SAME group object mutated
     * in place with the full cumulative video list (see {@code VideoGroup.from(baseGroup, ...)}
     * upstream) — so this range-inserts only the tail past what was already appended for that
     * group, instead of redrawing the whole list on every page (which caused visible
     * thumbnail flicker on first load, when several groups/pages arrive back-to-back).
     */
    private void appendGridGroup(VideoGroup group) {
        Integer alreadyAppended = mGridGroupSizes.get(group);
        List<Video> videos = group.getVideos();
        int total = videos != null ? videos.size() : 0;
        if (alreadyAppended == null) {
            mGridAdapter.appendVideos(videos);
            mGridGroupSizes.put(group, total);
        } else if (total > alreadyAppended) {
            mGridAdapter.appendVideos(videos.subList(alreadyAppended, total));
            mGridGroupSizes.put(group, total);
        }
    }

    private void showEmptyMessage(String message) {
        if (mEmptyContainer == null) {
            return;
        }
        mEmptyMessage.setText(message);
        mEmptyButton.setVisibility(View.GONE);
        mEmptyContainer.setVisibility(View.VISIBLE);
    }

    private void hideEmptyMessage() {
        if (mEmptyContainer != null) {
            mEmptyContainer.setVisibility(View.GONE);
        }
    }

    /**
     * Switches between the bottom nav bar tabs (Home/Music/History, in that order) on an
     * on-screen horizontal swipe — left goes to the next tab, right to the previous one.
     * Registered on the outer content RecyclerView, so it is consulted before the inner
     * shelves: a shelf that can still scroll toward the swipe direction keeps the gesture;
     * a shelf already at that end (or a grid section with no horizontal scroller) yields it
     * to switch tabs instead.
     */
    private final RecyclerView.OnItemTouchListener mSwipeToSwitchTab = new RecyclerView.OnItemTouchListener() {
        private float mDownX;
        private float mDownY;
        private boolean mDecided;
        private boolean mTriggered;

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    mDownX = e.getX();
                    mDownY = e.getY();
                    mDecided = false;
                    mTriggered = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    if (mDecided) {
                        return mTriggered;
                    }
                    float dx = e.getX() - mDownX;
                    float dy = e.getY() - mDownY;
                    int slop = ViewConfiguration.get(rv.getContext()).getScaledTouchSlop();
                    if (Math.abs(dx) < slop * 2 && Math.abs(dy) < slop * 2) {
                        return false; // wait for a clear direction
                    }
                    mDecided = true;
                    if (Math.abs(dx) > Math.abs(dy) && shelfYieldsSwipe(rv, mDownX, mDownY, dx > 0)) {
                        mTriggered = true;
                        switchToAdjacentTab(dx > 0 ? -1 : 1);
                    }
                    return mTriggered;
                default:
                    return false;
            }
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            // Gesture is fully handled in onInterceptTouchEvent.
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            // no-op
        }
    };

    /**
     * True if a horizontal swipe at (x, y) should switch tabs instead of scrolling a shelf:
     * either there is no horizontal shelf under the finger (grid section or empty area), or
     * the shelf there is already at the end the swipe is heading toward.
     */
    private boolean shelfYieldsSwipe(RecyclerView contentList, float x, float y, boolean swipingRight) {
        View child = contentList.findChildViewUnder(x, y);
        if (child == null) {
            return true;
        }
        View shelfList = child.findViewById(R.id.shelf_list);
        if (shelfList == null) {
            return true;
        }
        // Swiping right drags content toward its start (canScrollHorizontally(-1)); swiping
        // left drags it toward its end (canScrollHorizontally(1)).
        return !shelfList.canScrollHorizontally(swipingRight ? -1 : 1);
    }

    /** Moves the bottom-nav selection by {@code direction} (+1 = next tab, -1 = previous). */
    private void switchToAdjacentTab(int direction) {
        if (mSectionAdapter == null) {
            return;
        }
        int[] tabSectionIds = {MediaGroup.TYPE_HOME, MediaGroup.TYPE_SUBSCRIPTIONS, MediaGroup.TYPE_HISTORY};
        int currentTab = -1;
        for (int i = 0; i < tabSectionIds.length; i++) {
            if (tabSectionIds[i] == mCurrentSectionId) {
                currentTab = i;
                break;
            }
        }
        if (currentTab < 0) {
            return;
        }
        int nextTab = currentTab + direction;
        if (nextTab < 0 || nextTab >= tabSectionIds.length) {
            return;
        }
        int index = mSectionAdapter.indexOfSection(tabSectionIds[nextTab]);
        if (index >= 0) {
            animateTabSwitch(direction, () -> selectSection(index, false));
        }
    }

    /**
     * Page-turn transition for a tab switch: slides the outgoing content off in the swipe
     * direction, swaps it for the new tab's content (via {@code onSwitch}, mid-flight while
     * off-screen so the swap itself isn't visible), then slides the new content in from the
     * opposite side. {@code direction} is the same +1/-1 (next/previous tab) switchToAdjacentTab
     * uses, which is also the new content's arrival side: +1 (next tab, swiped in from the
     * screen's right edge) arrives from the right, -1 from the left.
     */
    private void animateTabSwitch(int direction, Runnable onSwitch) {
        if (mContentList == null) {
            onSwitch.run();
            return;
        }
        float width = mContentList.getWidth();
        if (width <= 0) {
            onSwitch.run();
            return;
        }
        mContentList.animate().cancel();
        mContentList.animate()
                .translationX(-direction * width)
                .setDuration(140)
                .withEndAction(() -> {
                    onSwitch.run();
                    mContentList.setTranslationX(direction * width);
                    mContentList.animate()
                            .translationX(0)
                            .setDuration(140)
                            .start();
                })
                .start();
    }
}
