package com.carelink.app.ui.elder;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.carelink.app.R;
import com.carelink.app.domain.model.TodayTaskItem;
import java.util.ArrayList;
import java.util.List;

/** 老人端打卡任务适配器 */
public class TaskAdapter extends RecyclerView.Adapter<TaskAdapter.TaskViewHolder> {

    private List<TodayTaskItem> items = new ArrayList<>();
    private OnTaskClickListener listener;

    public interface OnTaskClickListener {
        void onTaskDone(TodayTaskItem item);
    }

    public void setListener(OnTaskClickListener listener) { this.listener = listener; }

    public void setItems(List<TodayTaskItem> items) {
        this.items = items;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TaskViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_task_card, parent, false);
        return new TaskViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TaskViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    class TaskViewHolder extends RecyclerView.ViewHolder {
        private TextView tvTitle, tvTime, tvStatus;
        private Button btnDone;

        TaskViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tv_task_title);
            tvTime = itemView.findViewById(R.id.tv_task_time);
            tvStatus = itemView.findViewById(R.id.tv_task_status);
            btnDone = itemView.findViewById(R.id.btn_task_done);
        }

        void bind(TodayTaskItem item) {
            tvTitle.setText(item.title);
            tvTime.setText(item.plannedTime > 0 ?
                    new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                            .format(new java.util.Date(item.plannedTime)) : "");

            if (item.isDone) {
                tvStatus.setText("已完成");
                tvStatus.setTextColor(0xFF4CAF50);
                btnDone.setVisibility(View.GONE);
            } else {
                tvStatus.setText("待完成");
                tvStatus.setTextColor(0xFFFF9800);
                btnDone.setVisibility(View.VISIBLE);
                btnDone.setOnClickListener(v -> {
                    if (listener != null) listener.onTaskDone(item);
                });
            }
        }
    }
}
