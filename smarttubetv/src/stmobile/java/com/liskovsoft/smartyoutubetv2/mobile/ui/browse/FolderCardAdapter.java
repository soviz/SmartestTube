package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 2-column grid of "folder" cards: one card per {@link VideoGroup}, using its first
 * video's thumbnail as the cover and the group's title as the label. Used for ROW-type
 * sections that are better browsed as folders than as horizontal shelves (currently
 * Music). Tapping a folder opens the details screen for that group's video list.
 *
 * Two extra behaviors on top of a plain list, both specific to Music:
 * - Genre filter: {@link #setGenreFilter} narrows the visible folders to those whose title
 *   matches a genre (a folder's title IS its genre here - see
 *   MobileBrowseFragment.rebuildGenreFilter, there's no separate genre field in the data).
 *   The very first folder that ever arrives (YouTube's "Liked Music" row) is pinned: it's
 *   always shown first regardless of the active filter, since it's the user's own music,
 *   not a genre shelf.
 * - Lazy reveal: new folders are queued and only inserted a page at a time via
 *   {@link #revealMore()}, instead of showing every group the moment it arrives - the Music
 *   tab's data layer (YouTubeContentService, an upstream submodule) fetches ALL of its
 *   available folder pages eagerly in one burst when the section loads, so without this
 *   buffering the whole list would appear at once instead of growing as the user scrolls.
 */
public class FolderCardAdapter extends RecyclerView.Adapter<FolderCardAdapter.ViewHolder> {
    public interface OnFolderAction {
        void onFolder(VideoGroup group);
    }

    public interface OnGenresChanged {
        void onGenresChanged(List<String> genres, String pinnedTitle);
    }

    /** Folders shown immediately, before any scrolling. */
    private static final int INITIAL_PAGE_SIZE = 8;
    /** Folders revealed per {@link #revealMore()} call (one screen's worth of scrolling). */
    private static final int PAGE_SIZE = 6;

    private int mCardWidth;
    private final OnFolderAction mClick;
    private OnGenresChanged mOnGenresChanged;
    /** Every folder received so far, in arrival order - the source of truth for filtering. */
    private final List<VideoGroup> mAllGroups = new ArrayList<>();
    /** Currently displayed subset of mAllGroups (post-filter, already revealed). */
    private final List<VideoGroup> mGroups = new ArrayList<>();
    /** Matches the active filter but not yet revealed - see {@link #revealMore()}. */
    private final List<VideoGroup> mPending = new ArrayList<>();
    /** The first folder ever received (YouTube's "Liked Music"); always shown, unfiltered. */
    private VideoGroup mPinnedGroup;
    @Nullable
    private String mGenreFilter; // null = "All"

    public FolderCardAdapter(int cardWidth, OnFolderAction click) {
        mCardWidth = cardWidth;
        mClick = click;
    }

    public void setOnGenresChanged(OnGenresChanged listener) {
        mOnGenresChanged = listener;
    }

    /** See {@link VideoCardAdapter#setCardWidth} — same orientation-change resize path. */
    public void setCardWidth(int cardWidth) {
        if (mCardWidth != cardWidth) {
            mCardWidth = cardWidth;
            notifyDataSetChanged();
        }
    }

    /**
     * A continuation of an existing (already-shown) group carries its full cumulative video
     * list, so its folder card is simply refreshed in place. An unseen group is queued
     * instead of being inserted right away - see {@link #revealMore()}. Mirrors
     * {@link ShelfAdapter#appendGroup}.
     */
    public void appendGroup(VideoGroup group) {
        for (int i = 0; i < mAllGroups.size(); i++) {
            if (mAllGroups.get(i).getId() == group.getId()) {
                mAllGroups.set(i, group);
                if (mPinnedGroup != null && mPinnedGroup.getId() == group.getId()) {
                    mPinnedGroup = group;
                }
                int shownIndex = mGroups.indexOf(mAllGroups.get(i));
                if (shownIndex >= 0) {
                    mGroups.set(shownIndex, group);
                    notifyItemChanged(shownIndex);
                }
                return;
            }
        }
        if (group.isEmpty()) {
            return;
        }
        boolean isFirstEver = mAllGroups.isEmpty();
        mAllGroups.add(group);
        if (isFirstEver) {
            mPinnedGroup = group;
        }
        notifyGenresChanged();

        if (matchesFilter(group) && !(isFirstEver)) { // pinned group is handled separately below
            mPending.add(group);
        }
        if (isFirstEver) {
            // Pinned group goes straight in, ahead of anything the filter would queue.
            mGroups.add(0, group);
            notifyItemInserted(0);
        } else if (mGroups.size() < INITIAL_PAGE_SIZE) {
            // Keep topping up the first page as groups trickle in, not just once: with only
            // the pinned card showing, the grid has nothing to scroll yet, so the scroll
            // listener that would normally drive revealMore() never fires - each arriving
            // group must retry filling the first page itself until it's actually full (or
            // there's simply nothing left to fill it with).
            revealMore(INITIAL_PAGE_SIZE - mGroups.size());
        }
    }

    private boolean matchesFilter(VideoGroup group) {
        return mGenreFilter == null || mGenreFilter.equals(group.getTitle());
    }

    /** Re-applies the current filter to the already-received groups (all except the pinned one). */
    public void setGenreFilter(@Nullable String genreTitle) {
        mGenreFilter = genreTitle;

        mGroups.clear();
        mPending.clear();
        if (mPinnedGroup != null) {
            mGroups.add(mPinnedGroup);
        }
        for (VideoGroup group : mAllGroups) {
            if (group == mPinnedGroup) {
                continue;
            }
            if (matchesFilter(group)) {
                mPending.add(group);
            }
        }
        notifyDataSetChanged();
        revealMore(INITIAL_PAGE_SIZE);
    }

    private void notifyGenresChanged() {
        if (mOnGenresChanged == null) {
            return;
        }
        Set<String> genres = new LinkedHashSet<>();
        for (VideoGroup group : mAllGroups) {
            if (group == mPinnedGroup) {
                continue;
            }
            String title = group.getTitle();
            if (title != null && !title.isEmpty()) {
                genres.add(title);
            }
        }
        mOnGenresChanged.onGenresChanged(new ArrayList<>(genres), mPinnedGroup != null ? mPinnedGroup.getTitle() : null);
    }

    /** True while there are buffered folders the scroll listener can still reveal. */
    public boolean hasMore() {
        return !mPending.isEmpty();
    }

    /** Insert the next page of buffered folders. Called when the grid is scrolled near the end. */
    public void revealMore() {
        revealMore(PAGE_SIZE);
    }

    private void revealMore(int count) {
        if (mPending.isEmpty()) {
            return;
        }
        int n = Math.min(count, mPending.size());
        int start = mGroups.size();
        for (int i = 0; i < n; i++) {
            mGroups.add(mPending.remove(0));
        }
        notifyItemRangeInserted(start, n);
    }

    public void clear() {
        mAllGroups.clear();
        mGroups.clear();
        mPending.clear();
        mPinnedGroup = null;
        mGenreFilter = null;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return mGroups.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_folder_card, parent, false);
        if (view.getLayoutParams() != null) {
            view.getLayoutParams().width = mCardWidth;
        }
        ViewHolder holder = new ViewHolder(view);
        holder.thumbFrame.getLayoutParams().height = mCardWidth * 9 / 16;
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        if (holder.itemView.getLayoutParams() != null) {
            holder.itemView.getLayoutParams().width = mCardWidth;
        }
        holder.thumbFrame.getLayoutParams().height = mCardWidth * 9 / 16;

        VideoGroup group = mGroups.get(position);
        holder.title.setText(group.getTitle());

        List<Video> videos = group.getVideos();
        Video cover = videos != null && !videos.isEmpty() ? videos.get(0) : null;
        holder.count.setText(String.valueOf(videos != null ? videos.size() : 0));

        if (cover != null) {
            Glide.with(holder.itemView.getContext())
                    .load(cover.getCardImageUrl())
                    .into(holder.thumb);
        } else {
            Glide.with(holder.itemView.getContext()).clear(holder.thumb);
        }

        holder.itemView.setOnClickListener(v -> {
            if (mClick != null) {
                mClick.onFolder(group);
            }
        });
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View thumbFrame;
        final ImageView thumb;
        final TextView count;
        final TextView title;

        ViewHolder(View itemView) {
            super(itemView);
            thumbFrame = itemView.findViewById(R.id.folder_thumb_frame);
            thumb = itemView.findViewById(R.id.folder_thumb);
            count = itemView.findViewById(R.id.folder_count);
            title = itemView.findViewById(R.id.folder_title);
        }
    }
}
