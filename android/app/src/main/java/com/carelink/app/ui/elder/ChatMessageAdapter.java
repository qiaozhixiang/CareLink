package com.carelink.app.ui.elder;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.carelink.app.R;

import java.util.ArrayList;
import java.util.List;

public class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.ChatMessageViewHolder> {

    private final List<ChatMessage> items = new ArrayList<>();

    public void submitList(List<ChatMessage> messages) {
        items.clear();
        if (messages != null) {
            items.addAll(messages);
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ChatMessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LinearLayout root = new LinearLayout(parent.getContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT,
                RecyclerView.LayoutParams.WRAP_CONTENT));
        root.setPadding(0, 0, 0, dp(parent, 12));

        TextView bubble = new TextView(parent.getContext());
        bubble.setTextSize(16);
        bubble.setLineSpacing(8f, 1.0f);
        bubble.setPadding(dp(parent, 16), dp(parent, 12), dp(parent, 16), dp(parent, 12));
        bubble.setMaxWidth(dp(parent, 280));
        bubble.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(bubble);

        TextView time = new TextView(parent.getContext());
        time.setTextSize(12);
        time.setPadding(dp(parent, 6), dp(parent, 6), dp(parent, 6), 0);
        root.addView(time);

        return new ChatMessageViewHolder(root, bubble, time);
    }

    @Override
    public void onBindViewHolder(@NonNull ChatMessageViewHolder holder, int position) {
        ChatMessage item = items.get(position);
        holder.bubble.setText(item.getContent());
        holder.time.setText(item.getTime());

        LinearLayout.LayoutParams bubbleParams = (LinearLayout.LayoutParams) holder.bubble.getLayoutParams();
        LinearLayout.LayoutParams timeParams = (LinearLayout.LayoutParams) holder.time.getLayoutParams();

        if (item.getType() == ChatMessage.TYPE_USER) {
            holder.root.setGravity(Gravity.END);
            bubbleParams.gravity = Gravity.END;
            timeParams.gravity = Gravity.END;
            holder.bubble.setBackgroundResource(R.drawable.bg_tag_blue);
            holder.bubble.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.brand_blue));
            holder.time.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
        } else {
            holder.root.setGravity(Gravity.START);
            bubbleParams.gravity = Gravity.START;
            timeParams.gravity = Gravity.START;
            holder.bubble.setBackgroundResource(R.drawable.bg_info_card);
            holder.bubble.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_primary));
            holder.time.setTextColor(ContextCompat.getColor(holder.itemView.getContext(), R.color.text_secondary));
        }

        holder.bubble.setLayoutParams(bubbleParams);
        holder.time.setLayoutParams(timeParams);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    private int dp(ViewGroup parent, int value) {
        float density = parent.getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }

    static class ChatMessageViewHolder extends RecyclerView.ViewHolder {
        final LinearLayout root;
        final TextView bubble;
        final TextView time;

        ChatMessageViewHolder(@NonNull View itemView, TextView bubble, TextView time) {
            super(itemView);
            this.root = (LinearLayout) itemView;
            this.bubble = bubble;
            this.time = time;
        }
    }
}
