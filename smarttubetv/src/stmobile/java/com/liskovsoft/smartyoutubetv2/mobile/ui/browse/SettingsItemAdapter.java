package com.liskovsoft.smartyoutubetv2.mobile.ui.browse;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.liskovsoft.smartyoutubetv2.common.app.models.data.SettingsItem;
import com.liskovsoft.smartyoutubetv2.tv.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertical list of settings entries for the Settings section. Each {@link SettingsItem}
 * carries its own action {@link Runnable} (built by the existing settings presenters), so
 * tapping a row just runs it — the native list only replaces the TV Leanback grid layer.
 */
class SettingsItemAdapter extends RecyclerView.Adapter<SettingsItemAdapter.ViewHolder> {
    private final List<SettingsItem> mItems = new ArrayList<>();

    SettingsItemAdapter(List<SettingsItem> items) {
        if (items != null) {
            mItems.addAll(items);
        }
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.mobile_settings_item, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        SettingsItem item = mItems.get(position);
        holder.title.setText(item.title);
        if (item.imageResId > 0) {
            holder.icon.setImageResource(item.imageResId);
            holder.icon.setVisibility(View.VISIBLE);
        } else {
            holder.icon.setVisibility(View.GONE);
        }
        if (item.isSwitch) {
            holder.switchView.setVisibility(View.VISIBLE);
            holder.switchView.setChecked(item.switchChecked);
            // The whole row toggles it (switchView itself is non-clickable - see the layout
            // comment) so the row's tap target isn't a tiny thumb, matching every other row here.
            holder.itemView.setOnClickListener(v -> {
                boolean checked = !holder.switchView.isChecked();
                holder.switchView.setChecked(checked);
                item.switchChecked = checked;
                if (item.onSwitchChange != null) {
                    item.onSwitchChange.onChange(checked);
                }
            });
        } else {
            holder.switchView.setVisibility(View.GONE);
            holder.itemView.setOnClickListener(v -> {
                if (item.onClick != null) {
                    item.onClick.run();
                }
            });
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView title;
        final Switch switchView;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.settings_icon);
            title = itemView.findViewById(R.id.settings_title);
            switchView = itemView.findViewById(R.id.settings_switch);
        }
    }
}
