package com.liskovsoft.smartyoutubetv2.common.app.models.data;

public class SettingsItem {
    public interface OnSwitchChange {
        void onChange(boolean checked);
    }

    public final String title;
    public final Runnable onClick;
    public int imageResId;
    // Optional: renders this row with a trailing Switch instead of relying on onClick to open a
    // picker dialog. Phone-only (mobile.ui.browse.SettingsItemAdapter) - TV ignores these fields
    // and keeps using onClick, since the Leanback settings grid has its own dialog-based flow.
    public boolean isSwitch;
    public boolean switchChecked;
    public OnSwitchChange onSwitchChange;

    public SettingsItem(String title, Runnable onClick) {
        this(title, onClick, -1);
    }

    public SettingsItem(String title, Runnable onClick, int imageResId) {
        this.title = title;
        this.onClick = onClick;
        this.imageResId = imageResId;
    }

    /** A phone-only Settings row rendered as a title + trailing Switch, no dialog. */
    public static SettingsItem forSwitch(String title, int imageResId, boolean checked, OnSwitchChange onSwitchChange) {
        SettingsItem item = new SettingsItem(title, null, imageResId);
        item.isSwitch = true;
        item.switchChecked = checked;
        item.onSwitchChange = onSwitchChange;
        return item;
    }
}
