package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.liskovsoft.smartyoutubetv2.common.app.models.data.Video;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.ChannelPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Flat list of video cards. Used both for grid sections and (horizontally) inside shelves.
 * The card width is fixed per adapter; the thumbnail keeps a 16:9 ratio.
 *
 * Public so the phone Search screen ({@code mobile.ui.search}) can reuse the same card.
 */
public class VideoCardAdapter extends RecyclerView.Adapter<VideoCardAdapter.ViewHolder> {
    public interface OnVideoAction {
        void onVideo(Video video);
    }

    private int mCardWidth;
    private final boolean mListMode;
    private final OnVideoAction mClick;
    private final OnVideoAction mLongClick;
    private final List<Video> mVideos = new ArrayList<>();
    // Home/Subscriptions/etc. can hand back the same video across separate continuation
    // pages (upstream feed quirk); track already-shown ids so re-appending the same
    // video doesn't render a visible duplicate card.
    private final Set<String> mVideoIds = new HashSet<>();

    public VideoCardAdapter(int cardWidth, OnVideoAction click, OnVideoAction longClick) {
        this(cardWidth, false, click, longClick);
    }

    /**
     * @param listMode true renders each item as a full-width row (thumbnail left, text right)
     *                 instead of the default grid card. Used for Music folders that are a
     *                 plain track list (few items) rather than a mix (many items).
     */
    public VideoCardAdapter(int cardWidth, boolean listMode, OnVideoAction click, OnVideoAction longClick) {
        mCardWidth = cardWidth;
        mListMode = listMode;
        mClick = click;
        mLongClick = longClick;
    }

    /**
     * Update the per-card width (e.g. after an orientation change, where the grid span
     * and therefore the column width change). The host activities declare
     * {@code configChanges="orientation|..."} so they are NOT recreated on rotation -
     * the fragment re-reads the span and calls this to keep card width == column width.
     */
    public void setCardWidth(int cardWidth) {
        if (mCardWidth != cardWidth) {
            mCardWidth = cardWidth;
            notifyDataSetChanged();
        }
    }

    public void setVideos(List<Video> videos) {
        mVideos.clear();
        mVideoIds.clear();
        if (videos != null) {
            for (Video video : videos) {
                addIfNew(video);
            }
        }
        notifyDataSetChanged();
    }

    /**
     * Append a continuation's new tail with a range-insert instead of a full
     * {@code notifyDataSetChanged}. Used by shelves so a horizontally-scrolled row keeps
     * its scroll position when more items page in — a full reset (or re-setting the
     * adapter on the row) would snap the shelf back to the start.
     */
    public void appendVideos(List<Video> videos) {
        if (videos == null || videos.isEmpty()) {
            return;
        }
        int start = mVideos.size();
        int added = 0;
        for (Video video : videos) {
            if (addIfNew(video)) {
                added++;
            }
        }
        if (added > 0) {
            notifyItemRangeInserted(start, added);
        }
    }

    /**
     * @return true if the video's id was not already shown and it was added to the list.
     * Videos without an id (e.g. section placeholders) are always added, since there's
     * nothing to dedupe them by.
     */
    private boolean addIfNew(Video video) {
        if (video == null) {
            return false;
        }
        if (video.videoId != null && !mVideoIds.add(video.videoId)) {
            return false;
        }
        mVideos.add(video);
        return true;
    }

    public void clear() {
        mVideos.clear();
        mVideoIds.clear();
        notifyDataSetChanged();
    }

    public void remove(List<Video> videos) {
        if (videos != null && mVideos.removeAll(videos)) {
            for (Video video : videos) {
                if (video != null && video.videoId != null) {
                    mVideoIds.remove(video.videoId);
                }
            }
            notifyDataSetChanged();
        }
    }

    public Video getLast() {
        return mVideos.isEmpty() ? null : mVideos.get(mVideos.size() - 1);
    }

    @Override
    public int getItemCount() {
        return mVideos.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(mListMode ? R.layout.mobile_video_row : R.layout.mobile_video_card, parent, false);
        ViewHolder holder = new ViewHolder(view);

        if (mListMode) {
            // Row thumbnail is a fixed-size square-ish box, not a full-width 16:9 card.
            int thumbWidth = mCardWidth * 2 / 3;
            holder.thumbFrame.getLayoutParams().width = thumbWidth;
            holder.thumbFrame.getLayoutParams().height = thumbWidth * 9 / 16;
        } else {
            if (view.getLayoutParams() != null) {
                view.getLayoutParams().width = mCardWidth;
            }
            holder.thumbFrame.getLayoutParams().height = mCardWidth * 9 / 16;
        }
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        // Re-apply width on every bind so setCardWidth() (orientation change) resizes
        // recycled cards, keeping each card the width of its grid column.
        if (mListMode) {
            int thumbWidth = mCardWidth * 2 / 3;
            holder.thumbFrame.getLayoutParams().width = thumbWidth;
            holder.thumbFrame.getLayoutParams().height = thumbWidth * 9 / 16;
        } else {
            if (holder.itemView.getLayoutParams() != null) {
                holder.itemView.getLayoutParams().width = mCardWidth;
            }
            holder.thumbFrame.getLayoutParams().height = mCardWidth * 9 / 16;
        }

        Video video = mVideos.get(position);
        holder.title.setText(video.getTitle());
        String author = video.getAuthor();
        String badge = video.badge;

        if (mListMode) {
            // Row's second line is view count + publish date only - no channel/author, since
            // music clips only expose the channel's raw "@handle" (no readable channel name),
            // see Video.getAuthor()/MusicItem.getUserName(). Duration is a badge overlaid on
            // the thumbnail here too, same as the grid card.
            String meta = extractMeta(video.getSecondTitleFull(), author);
            holder.author.setText(meta != null ? meta : "");

            if (badge != null && !badge.isEmpty()) {
                holder.duration.setText(badge);
                holder.duration.setVisibility(View.VISIBLE);
            } else {
                holder.duration.setVisibility(View.GONE);
            }
        } else {
            holder.author.setText(author != null ? author : "");
            holder.author.setOnClickListener(v -> ChannelPresenter.instance(v.getContext()).openChannel(video));

            // View count + publish date, e.g. "1.2M views • 3 days ago". getSecondTitleFull()
            // is the raw "Channel • views • published" string the author was parsed out of
            // (see Video.getAuthor()/extractAuthor()); re-join every segment except the one
            // that matched as the author.
            String meta = extractMeta(video.getSecondTitleFull(), author);
            if (meta != null && !meta.isEmpty()) {
                holder.meta.setText("  •  " + meta);
                holder.meta.setVisibility(View.VISIBLE);
            } else {
                holder.meta.setVisibility(View.GONE);
            }

            // Duration/length badge overlaid on the thumbnail (YouTube-style). video.badge holds
            // the duration text ("12:34") for normal videos and occasionally a label ("LIVE").
            // Explicit GONE branch matters: cards are recycled, so a badge-less video must clear
            // a badge left over from a recycled holder.
            if (badge != null && !badge.isEmpty()) {
                holder.duration.setText(badge);
                holder.duration.setVisibility(View.VISIBLE);
            } else {
                holder.duration.setVisibility(View.GONE);
            }
        }

        Glide.with(holder.itemView.getContext())
                .load(video.getBestCardImageUrl())
                .error(Glide.with(holder.itemView.getContext()).load(video.getFallbackCardImageUrl()))
                .into(holder.thumb);

        holder.itemView.setOnClickListener(v -> {
            if (mClick != null) {
                mClick.onVideo(video);
            }
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (mLongClick != null) {
                mLongClick.onVideo(video);
            }
            return true;
        });
    }

    /**
     * Re-joins {@code secondTitle}'s "•"-separated segments (channel, views, published date,
     * labels like "LIVE"/"4K") minus whichever one segment {@link Video#getAuthor()} already
     * matched as the channel name, giving the remaining "views • published date" (order as
     * returned by the API, not reordered).
     */
    private static String extractMeta(CharSequence secondTitle, String author) {
        if (secondTitle == null) {
            return null;
        }
        String[] parts = secondTitle.toString().split(Video.TERTIARY_TEXT_DELIM);
        StringBuilder result = new StringBuilder();
        boolean skippedAuthor = author == null; // nothing to skip if there was no author match
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!skippedAuthor && trimmed.equals(author)) {
                skippedAuthor = true;
                continue;
            }
            if (result.length() > 0) {
                result.append("  •  ");
            }
            result.append(trimmed);
        }
        return result.length() > 0 ? result.toString() : null;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View thumbFrame;
        final ImageView thumb;
        final TextView duration;
        final TextView title;
        final TextView author;
        final TextView meta;

        ViewHolder(View itemView) {
            super(itemView);
            thumbFrame = itemView.findViewById(R.id.card_thumb_frame);
            thumb = itemView.findViewById(R.id.card_thumb);
            duration = itemView.findViewById(R.id.card_duration);
            title = itemView.findViewById(R.id.card_title);
            author = itemView.findViewById(R.id.card_author);
            meta = itemView.findViewById(R.id.card_meta); // absent in mobile_video_row (listMode)
        }
    }
}
