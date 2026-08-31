package com.carelink.app.ui.elder;

import android.content.Context;

import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.carelink.app.base.BaseViewModel;
import com.carelink.app.data.repository.AiRepository;
import com.carelink.app.ui.common.RoleScopedAiChatStore;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.lifecycle.HiltViewModel;

@HiltViewModel
public class AiChatViewModel extends BaseViewModel {

    private final AiRepository aiRepository;
    private final MutableLiveData<List<ChatMessage>> messagesLiveData = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loadingLiveData = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorLiveData = new MutableLiveData<>();
    private final List<ChatMessage> messages = new ArrayList<>();
    private RoleScopedAiChatStore chatStore;
    private String roleScope = RoleScopedAiChatStore.ROLE_ELDER;

    @Inject
    public AiChatViewModel(AiRepository aiRepository) {
        this.aiRepository = aiRepository;
    }

    public void bindRoleScope(Context context, String roleScope) {
        String resolvedRole = RoleScopedAiChatStore.ROLE_FAMILY.equalsIgnoreCase(roleScope)
                ? RoleScopedAiChatStore.ROLE_FAMILY
                : RoleScopedAiChatStore.ROLE_ELDER;
        if (chatStore != null && this.roleScope.equals(resolvedRole)) {
            publishMessages();
            return;
        }
        this.roleScope = resolvedRole;
        this.chatStore = new RoleScopedAiChatStore(context, resolvedRole);
        messages.clear();
        messages.addAll(chatStore.loadMessages());
        publishMessages();
    }

    public LiveData<List<ChatMessage>> getMessages() {
        return messagesLiveData;
    }

    public LiveData<Boolean> getLoading() {
        return loadingLiveData;
    }

    public LiveData<String> getError() {
        return errorLiveData;
    }

    public void sendMessage(String userInput) {
        sendMessage(userInput, null);
    }

    public void sendMessage(String userInput, @Nullable String imageUri) {
        ensureStoreReady();
        String normalized = userInput == null ? "" : userInput.trim();
        boolean hasImage = imageUri != null && !imageUri.trim().isEmpty();
        if (normalized.isEmpty() && !hasImage) {
            errorLiveData.setValue("请输入内容或选择图片");
            return;
        }

        String displayText = normalized;
        if (displayText.isEmpty()) {
            displayText = "[图片]";
        } else if (hasImage) {
            displayText = displayText + "\n[已附带图片]";
        }
        ChatMessage userMessage = new ChatMessage(ChatMessage.TYPE_USER, displayText, now());
        messages.add(userMessage);
        persistAndPublishMessages();
        loadingLiveData.setValue(true);

        aiRepository.sendMessage(buildHistoryForRequest(), normalized, imageUri, new AiRepository.ResultCallback<String>() {
            @Override
            public void onSuccess(String data) {
                loadingLiveData.postValue(false);
                messages.add(new ChatMessage(ChatMessage.TYPE_AI, data, now()));
                persistAndPublishMessages();
            }

            @Override
            public void onError(String message) {
                loadingLiveData.postValue(false);
                errorLiveData.postValue(message);
            }
        });
    }

    public void clearHistory() {
        ensureStoreReady();
        messages.clear();
        messages.addAll(chatStore.clearMessages());
        publishMessages();
    }

    private List<String[]> buildHistoryForRequest() {
        List<String[]> history = new ArrayList<>();
        for (ChatMessage message : messages) {
            history.add(new String[]{
                    message.getType() == ChatMessage.TYPE_USER ? "user" : "assistant",
                    message.getContent()
            });
        }
        return history;
    }

    private void persistAndPublishMessages() {
        if (chatStore != null) {
            chatStore.saveMessages(messages);
        }
        publishMessages();
    }

    private void publishMessages() {
        messagesLiveData.postValue(new ArrayList<>(messages));
    }

    private void ensureStoreReady() {
        if (chatStore != null) {
            return;
        }
        messages.clear();
        messages.add(RoleScopedAiChatStore.buildWelcomeMessage(roleScope));
        publishMessages();
    }

    private String now() {
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(new Date());
    }
}
