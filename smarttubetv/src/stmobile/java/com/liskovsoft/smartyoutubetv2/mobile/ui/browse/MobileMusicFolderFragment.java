package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.content.res.Configuration;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.VideoGroup;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.VideoActionPresenter;
import com.liskovsoft.smartyoutubetv2.common.app.presenters.dialogs.menu.VideoMenuPresenter;
import com.liskovsoft.smartyoutubetv2.tv.R;

/**
 * Music folder-details screen: a single, already-loaded {@link VideoGroup} (one Music
 * folder card's contents) rendered as a plain 2-column grid. No presenter/pagination —
 * unlike Channel Uploads, a Music shelf's group is a fixed list handed in by
 * {@link MusicFolderStore}, not an incrementally-loaded feed.
 */
public class MobileMusicFolderFragment extends Fragment {
    private RecyclerView mGrid;
    private TextView mTitleView;
    private VideoCardAdapter mAdapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.mobile_music_folder_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mGrid = view.findViewById(R.id.folder_grid);
        mTitleView = view.findViewById(R.id.folder_details_title);

        view.findViewById(R.id.btn_back).setOnClickListener(v -> {
            if (getActivity() != null) {
                getActivity().finish();
            }
        });

        int span = getResources().getInteger(R.integer.mobile_grid_span);
        int cardWidth = getResources().getDisplayMetrics().widthPixels / span;
        mAdapter = new VideoCardAdapter(cardWidth, mVideoClick, mVideoLongClick);
        mGrid.setLayoutManager(new GridLayoutManager(getContext(), span));
        mGrid.setAdapter(mAdapter);

        VideoGroup group = MusicFolderStore.takePendingGroup();
        if (group != null) {
            if (mTitleView != null) {
                mTitleView.setText(group.getTitle());
            }
            mAdapter.setVideos(group.getVideos());
        }
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        int span = getResources().getInteger(R.integer.mobile_grid_span);
        if (mGrid != null && mGrid.getLayoutManager() instanceof GridLayoutManager) {
            ((GridLayoutManager) mGrid.getLayoutManager()).setSpanCount(span);
        }
        if (mAdapter != null) {
            mAdapter.setCardWidth(getResources().getDisplayMetrics().widthPixels / span);
        }
    }

    private final VideoCardAdapter.OnVideoAction mVideoClick = video ->
            VideoActionPresenter.instance(getContext()).apply(video);

    private final VideoCardAdapter.OnVideoAction mVideoLongClick = video ->
            VideoMenuPresenter.instance(getContext()).showMenu(video);
}
