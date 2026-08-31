package com.carelink.app.ui.family;

import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.carelink.app.R;
import com.carelink.app.data.local.pref.PreferenceManager;
import com.carelink.app.ui.elder.AiChatViewModel;
import com.carelink.app.ui.elder.ChatMessageAdapter;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class FamilyAiChatFragment extends Fragment {

    private AiChatViewModel viewModel;
    private ChatMessageAdapter adapter;
    private RecyclerView recyclerView;
    private TextInputEditText inputEditText;
    private MaterialButton sendButton;
    private MaterialButton selectImageButton;
    private MaterialButton clearButton;
    private ProgressBar progressBar;
    private TextView helperStatusView;
    private PreferenceManager preferenceManager;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                selectedImageUri = uri;
                if (uri != null) {
                    helperStatusView.setText("已选择图片");
                }
                Boolean loading = viewModel == null ? Boolean.FALSE : viewModel.getLoading().getValue();
                sendButton.setEnabled(!Boolean.TRUE.equals(loading) && hasAnyInput());
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        preferenceManager = new PreferenceManager(requireContext());

        androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_page));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(createHeroCard());
        root.addView(createAiSection());
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AiChatViewModel.class);
        viewModel.bindRoleScope(requireContext(), "FAMILY");
        adapter = new ChatMessageAdapter();
        recyclerView.setAdapter(adapter);
        observeViewModel();
        bindInputState();
        sendButton.setOnClickListener(v -> submitMessage());
        clearButton.setOnClickListener(v -> clearHistory());
    }

    private View createHeroCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(22));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_blue));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.addView(content);

        TextView title = new TextView(requireContext());
        title.setText("关怀助手");
        title.setTextSize(23);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        content.addView(title);

        TextView subtitle = new TextView(requireContext());
        String nickname = preferenceManager.getNickname();
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = "家属";
        }
        subtitle.setText(nickname + "，可直接发起对话。");
        subtitle.setTextSize(15);
        subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_80));
        subtitle.setPadding(0, dp(10), 0, dp(12));
        content.addView(subtitle);

        helperStatusView = new TextView(requireContext());
        helperStatusView.setText("可直接输入问题");
        helperStatusView.setTextSize(13);
        helperStatusView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_80));
        content.addView(helperStatusView);
        return card;
    }

    private View createAiSection() {
        MaterialCardView card = buildSectionCard();
        LinearLayout content = (LinearLayout) card.getChildAt(0);
        content.addView(createSectionTitle("家属端关怀助手", "", 18));
        content.addView(createMessageList());
        content.addView(createComposer());
        return card;
    }

    private MaterialCardView buildSectionCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(20));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_card));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(content);
        return card;
    }

    private View createSectionTitle(String titleText, String descText, int bodySize) {
        LinearLayout wrapper = new LinearLayout(requireContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        wrapper.setPadding(0, 0, 0, dp(10));

        TextView title = new TextView(requireContext());
        title.setText(titleText);
        title.setTextSize(bodySize + 1);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_primary));
        wrapper.addView(title);

        TextView desc = new TextView(requireContext());
        desc.setText(descText);
        desc.setTextSize(13);
        desc.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        desc.setPadding(0, dp(6), 0, 0);
        wrapper.addView(desc);
        return wrapper;
    }

    private View createMessageList() {
        recyclerView = new RecyclerView(requireContext());
        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        recyclerView.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(320));
        params.bottomMargin = dp(12);
        recyclerView.setLayoutParams(params);
        return recyclerView;
    }

    private View createComposer() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);

        TextInputLayout inputLayout = new TextInputLayout(requireContext());
        inputLayout.setHint("想和 AI 说点什么？");
        inputLayout.setBoxBackgroundMode(TextInputLayout.BOX_BACKGROUND_OUTLINE);
        inputLayout.setBoxCornerRadii(dp(18), dp(18), dp(18), dp(18));
        content.addView(inputLayout, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        inputEditText = new TextInputEditText(requireContext());
        inputEditText.setMinLines(2);
        inputEditText.setMaxLines(4);
        inputEditText.setTextSize(17);
        inputEditText.setGravity(Gravity.TOP | Gravity.START);
        inputLayout.addView(inputEditText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(12);
        content.addView(actions, actionParams);

        progressBar = new ProgressBar(requireContext());
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        progressParams.rightMargin = dp(10);
        actions.addView(progressBar, progressParams);

        TextView tip = new TextView(requireContext());
        tip.setText("");
        tip.setTextSize(13);
        tip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        tip.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        actions.addView(tip);

        clearButton = new MaterialButton(requireContext());
        clearButton.setText("清空历史");
        clearButton.setAllCaps(false);
        actions.addView(clearButton);

        selectImageButton = new MaterialButton(requireContext());
        selectImageButton.setText("选图");
        selectImageButton.setAllCaps(false);
        selectImageButton.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        actions.addView(selectImageButton);

        sendButton = new MaterialButton(requireContext());
        sendButton.setText("发送");
        sendButton.setEnabled(false);
        sendButton.setAllCaps(false);
        actions.addView(sendButton);
        return content;
    }

    private void observeViewModel() {
        viewModel.getMessages().observe(getViewLifecycleOwner(), messages -> {
            adapter.submitList(messages);
            if (messages != null && !messages.isEmpty()) {
                recyclerView.scrollToPosition(messages.size() - 1);
            }
        });

        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean isLoading = Boolean.TRUE.equals(loading);
            progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
            sendButton.setEnabled(!isLoading && hasAnyInput());
            clearButton.setEnabled(!isLoading);
            if (selectImageButton != null) {
                selectImageButton.setEnabled(!isLoading);
            }
            helperStatusView.setText(isLoading ? "AI 正在整理回复，请稍等..." : "可直接输入问题");
        });

        viewModel.getError().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.trim().isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                helperStatusView.setText(error);
            }
        });
    }

    private void bindInputState() {
        inputEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                Boolean loading = viewModel == null ? Boolean.FALSE : viewModel.getLoading().getValue();
                sendButton.setEnabled(!Boolean.TRUE.equals(loading) && hasAnyInput());
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }

    private void submitMessage() {
        if (!hasAnyInput()) {
            return;
        }
        String text = inputEditText.getText() == null ? "" : inputEditText.getText().toString().trim();
        String imageUri = selectedImageUri == null ? null : selectedImageUri.toString();
        inputEditText.setText("");
        selectedImageUri = null;
        viewModel.sendMessage(text, imageUri);
    }

    private void clearHistory() {
        viewModel.clearHistory();
        helperStatusView.setText("已清空家属端聊天记录，现在是新的独立会话");
        Toast.makeText(requireContext(), "已清空家属端 AI 聊天记录", Toast.LENGTH_SHORT).show();
    }

    private boolean hasInput() {
        return inputEditText != null
                && inputEditText.getText() != null
                && !inputEditText.getText().toString().trim().isEmpty();
    }

    private boolean hasAnyInput() {
        return hasInput() || selectedImageUri != null;
    }

    private int dp(int value) {
        float density = requireContext().getResources().getDisplayMetrics().density;
        return (int) (value * density + 0.5f);
    }
}
