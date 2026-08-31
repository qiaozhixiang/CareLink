package com.carelink.app.ui.elder;

public class ChatMessage {

    public static final int TYPE_AI = 0;
    public static final int TYPE_USER = 1;

    private final int type;
    private final String content;
    private final String time;

    public ChatMessage(int type, String content, String time) {
        this.type = type;
        this.content = content;
        this.time = time;
    }

    public int getType() {
        return type;
    }

    public String getContent() {
        return content;
    }

    public String getTime() {
        return time;
    }
}
