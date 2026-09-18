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
import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * 2-column grid of "folder" cards: one card per {@link VideoGroup}, using its first
 * video's thumbnail as the cover and the group's title as the label. Used for ROW-type
 * sections that are better browsed as folders than as horizontal shelves (currently
 * Music). Tapping a folder opens the details screen for that group's video list.
 */
public class FolderCardAdapter extends RecyclerView.Adapter<FolderCardAdapter.ViewHolder> {
    public interface OnFolderAction {
        void onFolder(VideoGroup group);
    }

    private int mCardWidth;
    private final OnFolderAction mClick;
    private final List<VideoGroup> mGroups = new ArrayList<>();

    public FolderCardAdapter(int cardWidth, OnFolderAction click) {
        mCardWidth = cardWidth;
        mClick = click;
    }

    /** See {@link VideoCardAdapter#setCardWidth} — same orientation-change resize path. */
    public void setCardWidth(int cardWidth) {
        if (mCardWidth != cardWidth) {
            mCardWidth = cardWidth;
            notifyDataSetChanged();
        }
    }

    /**
     * A continuation of an existing group carries its full cumulative video list, so an
     * existing folder card is simply refreshed in place; an unseen group id becomes a new
     * folder card. Mirrors {@link ShelfAdapter#appendGroup}.
     */
    public void appendGroup(VideoGroup group) {
        for (int i = 0; i < mGroups.size(); i++) {
            if (mGroups.get(i).getId() == group.getId()) {
                mGroups.set(i, group);
                notifyItemChanged(i);
                return;
            }
        }
        if (group.isEmpty()) {
            return;
        }
        mGroups.add(group);
        notifyItemInserted(mGroups.size() - 1);
    }

    public void clear() {
        mGroups.clear();
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
