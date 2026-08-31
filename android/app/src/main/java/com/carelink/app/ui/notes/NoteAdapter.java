package com.carelink.app.ui.notes;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.carelink.app.R;
import com.carelink.app.data.local.entity.CareNoteEntity;
import com.carelink.app.utils.AppUtils;
import java.util.ArrayList;
import java.util.List;

/** 备注列表适配器 */
public class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.NoteViewHolder> {

    private final List<CareNoteEntity> items = new ArrayList<>();

    public void setItems(List<CareNoteEntity> items) {
        submitList(items);
    }

    public void submitList(List<CareNoteEntity> items) {
        this.items.clear();
        if (items != null) {
            this.items.addAll(items);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public NoteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_note_card, parent, false);
        return new NoteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NoteViewHolder holder, int position) {
        holder.bind(items.get(position));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class NoteViewHolder extends RecyclerView.ViewHolder {
        private TextView tvContent, tvTime, tvImportant;

        NoteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvContent = itemView.findViewById(R.id.tv_note_content);
            tvTime = itemView.findViewById(R.id.tv_note_time);
            tvImportant = itemView.findViewById(R.id.tv_important);
        }

        void bind(CareNoteEntity entity) {
            tvContent.setText(entity.content);
            tvTime.setText(AppUtils.formatDateTime(entity.createdAt));
            tvImportant.setVisibility(entity.isImportant == 1 ? View.VISIBLE : View.GONE);
        }
    }
}

