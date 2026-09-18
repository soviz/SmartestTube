package com.liskovsoft.smartyoutubetv2.tv.ui.playback.actions;

import android.content.Context;

import com.liskovsoft.smartyoutubetv2.tv.R;

public class VideoOffAction extends TwoStateAction {
    public VideoOffAction(Context context) {
        super(context, R.id.action_video_off, R.drawable.action_video_off);

        String label = context.getString(R.string.action_video_off);
        String[] labels = new String[2];
        // Note, labels denote the action taken when clicked
        labels[INDEX_OFF] = label;
        labels[INDEX_ON] = label;
        setLabels(labels);
    }
}
