package com.carelink.app.ui.alert;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.carelink.app.R;
import com.carelink.app.data.local.entity.AlertEventEntity;
import com.carelink.app.utils.AppUtils;
import java.util.ArrayList;
import java.util.List;

/** 异常列表适配器 */
public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder> {

    private List<AlertEventEntity> items = new ArrayList<>();
    private OnAlertClickListener listener;

    public interface OnAlertClickListener {
        void onHandle(AlertEventEntity alert);
    }

    public void setListener(OnAlertClickListener listener) { this.listener = listener; }

    public void setItems(List<AlertEventEntity> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert_card, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    class AlertViewHolder extends RecyclerView.ViewHolder {
        private TextView tvDesc, tvLevel, tvTime;
        private Button btnHandle;

        AlertViewHolder(@NonNull View itemView) {
            super(itemView);
            tvDesc = itemView.findViewById(R.id.tv_alert_desc);
            tvLevel = itemView.findViewById(R.id.tv_alert_level);
            tvTime = itemView.findViewById(R.id.tv_alert_time);
            btnHandle = itemView.findViewById(R.id.btn_handle);
        }

        void bind(AlertEventEntity entity) {
            tvDesc.setText(entity.description);
            tvLevel.setText(AppUtils.getAlertLevelLabel(entity.level));
            tvLevel.setTextColor(AppUtils.getAlertLevelColor(entity.level));
            tvTime.setText(AppUtils.formatDateTime(entity.createdAt));

            if ("PENDING".equals(entity.status)) {
                btnHandle.setVisibility(View.VISIBLE);
                btnHandle.setOnClickListener(v -> {
                    if (listener != null) listener.onHandle(entity);
                });
            } else {
                btnHandle.setVisibility(View.GONE);
            }
        }
    }
}
