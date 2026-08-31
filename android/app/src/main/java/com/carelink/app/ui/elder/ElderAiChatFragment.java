package com.carelink.app.ui.elder;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
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
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.Locale;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ElderAiChatFragment extends Fragment {

    private AiChatViewModel viewModel;
    private ChatMessageAdapter adapter;
    private RecyclerView recyclerView;
    private TextInputEditText inputEditText;
    private MaterialButton sendButton;
    private MaterialButton selectImageButton;
    private MaterialButton voiceButton;
    private MaterialButton clearButton;
    private MaterialButton backHomeButton;
    private ProgressBar progressBar;
    private TextView helperStatusView;
    private Uri selectedImageUri;

    private final ActivityResultLauncher<String> imagePickerLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                selectedImageUri = uri;
                if (uri != null && helperStatusView != null) {
                    helperStatusView.setText("已选择图片，发送时会附带给 AI。");
                }
                Boolean loading = viewModel == null ? Boolean.FALSE : viewModel.getLoading().getValue();
                if (sendButton != null) {
                    sendButton.setEnabled(!Boolean.TRUE.equals(loading) && hasAnyInput());
                }
            });

    private final ActivityResultLauncher<Intent> speechLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != android.app.Activity.RESULT_OK || result.getData() == null) {
                    return;
                }
                ArrayList<String> texts = result.getData().getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                if (texts == null || texts.isEmpty()) {
                    return;
                }
                String speechText = texts.get(0);
                if (speechText == null || speechText.trim().isEmpty() || inputEditText == null) {
                    return;
                }
                String current = inputEditText.getText() == null ? "" : inputEditText.getText().toString().trim();
                String merged = current.isEmpty() ? speechText : (current + " " + speechText);
                inputEditText.setText(merged);
                inputEditText.setSelection(merged.length());
                if (helperStatusView != null) {
                    helperStatusView.setText("已识别语音，可继续补充或直接发送。");
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        androidx.core.widget.NestedScrollView scrollView = new androidx.core.widget.NestedScrollView(requireContext());
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.surface_page));

        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scrollView.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(createHeroCard());
        root.addView(createAiSection());
        return scrollView;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(AiChatViewModel.class);
        viewModel.bindRoleScope(requireContext(), "ELDER");
        adapter = new ChatMessageAdapter();
        recyclerView.setAdapter(adapter);
        observeViewModel();
        bindInputState();
        sendButton.setOnClickListener(v -> submitMessage());
        clearButton.setOnClickListener(v -> clearHistory());
        voiceButton.setOnClickListener(v -> startSpeechInput());
        backHomeButton.setOnClickListener(v -> goBackHome());
    }

    private View createHeroCard() {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setRadius(dp(22));
        card.setCardElevation(dp(2));
        card.setCardBackgroundColor(ContextCompat.getColor(requireContext(), R.color.brand_blue));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.bottomMargin = dp(14);
        card.setLayoutParams(params);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        card.addView(content);

        TextView title = new TextView(requireContext());
        title.setText("AI 智能聊天");
        title.setTextSize(23);
        title.setTextColor(ContextCompat.getColor(requireContext(), R.color.white));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        content.addView(title);

        TextView subtitle = new TextView(requireContext());
        subtitle.setText("支持语音、图片、文字多模态聊天。");
        subtitle.setTextSize(15);
        subtitle.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_80));
        subtitle.setPadding(0, dp(10), 0, dp(12));
        content.addView(subtitle);

        helperStatusView = new TextView(requireContext());
        helperStatusView.setText("你可以直接说话、输入文字，或发送图片让 AI 帮你分析。");
        helperStatusView.setTextSize(13);
        helperStatusView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white_80));
        content.addView(helperStatusView);
        return card;
    }

    private View createAiSection() {
        MaterialCardView card = buildSectionCard();
        LinearLayout content = (LinearLayout) card.getChildAt(0);
        content.addView(createSectionTitle("老人端 AI 助手", "语音会先识别为文字后发送给大模型，图片可一并上传。", 18));
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
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
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
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        inputEditText = new TextInputEditText(requireContext());
        inputEditText.setMinLines(2);
        inputEditText.setMaxLines(4);
        inputEditText.setTextSize(17);
        inputEditText.setGravity(Gravity.TOP | Gravity.START);
        inputLayout.addView(inputEditText, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout topRow = new LinearLayout(requireContext());
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams topRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        topRowParams.topMargin = dp(12);
        content.addView(topRow, topRowParams);

        progressBar = new ProgressBar(requireContext());
        progressBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(dp(22), dp(22));
        progressParams.rightMargin = dp(10);
        topRow.addView(progressBar, progressParams);

        TextView tip = new TextView(requireContext());
        tip.setText("支持语音、图片、文字");
        tip.setTextSize(13);
        tip.setTextColor(ContextCompat.getColor(requireContext(), R.color.text_secondary));
        tip.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        topRow.addView(tip);

        clearButton = new MaterialButton(requireContext());
        clearButton.setText("清空");
        clearButton.setAllCaps(false);
        topRow.addView(clearButton);

        voiceButton = new MaterialButton(requireContext());
        voiceButton.setText("语音");
        voiceButton.setAllCaps(false);
        topRow.addView(voiceButton);

        selectImageButton = new MaterialButton(requireContext());
        selectImageButton.setText("图片");
        selectImageButton.setAllCaps(false);
        selectImageButton.setOnClickListener(v -> imagePickerLauncher.launch("image/*"));
        topRow.addView(selectImageButton);

        LinearLayout bottomRow = new LinearLayout(requireContext());
        bottomRow.setOrientation(LinearLayout.HORIZONTAL);
        bottomRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams bottomRowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bottomRowParams.topMargin = dp(8);
        content.addView(bottomRow, bottomRowParams);

        sendButton = new MaterialButton(requireContext());
        sendButton.setText("发送");
        sendButton.setEnabled(false);
        sendButton.setAllCaps(false);
        sendButton.setIconResource(android.R.drawable.ic_menu_send);
        sendButton.setIconGravity(MaterialButton.ICON_GRAVITY_TEXT_START);
        sendButton.setTextSize(16);
        LinearLayout.LayoutParams sendLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sendLp.rightMargin = dp(8);
        bottomRow.addView(sendButton, sendLp);

        backHomeButton = new MaterialButton(requireContext());
        backHomeButton.setText("返回首页");
        backHomeButton.setAllCaps(false);
        bottomRow.addView(backHomeButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
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
            voiceButton.setEnabled(!isLoading);
            selectImageButton.setEnabled(!isLoading);
            helperStatusView.setText(isLoading ? "AI 正在回复，请稍等..." : "继续提问即可，支持语音、图片、文字混合。");
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
        helperStatusView.setText("已清空聊天记录，可以重新开始。");
        Toast.makeText(requireContext(), "聊天记录已清空", Toast.LENGTH_SHORT).show();
    }

    private void startSpeechInput() {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.SIMPLIFIED_CHINESE.toLanguageTag());
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "请开始说话");
        try {
            speechLauncher.launch(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(requireContext(), "当前设备不支持语音识别", Toast.LENGTH_SHORT).show();
        }
    }

    private void goBackHome() {
        requireActivity().getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.elder_content_container, new ElderHomeFragment(), "home")
                .commitAllowingStateLoss();
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
