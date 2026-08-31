package com.carelink.app.data.remote.dto;

import java.util.List;

public class AiChatRequest {

    private String model;
    private Message[] messages;
    private Float temperature;
    private Integer max_tokens;

    public AiChatRequest() {
    }

    public AiChatRequest(String model, Message[] messages, Float temperature, Integer max_tokens) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.max_tokens = max_tokens;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Message[] getMessages() {
        return messages;
    }

    public void setMessages(Message[] messages) {
        this.messages = messages;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Integer getMax_tokens() {
        return max_tokens;
    }

    public void setMax_tokens(Integer max_tokens) {
        this.max_tokens = max_tokens;
    }

    public static class Message {
        private String role;
        private Object content;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public Message(String role, List<ContentItem> content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public Object getContent() {
            return content;
        }

        public void setContent(Object content) {
            this.content = content;
        }
    }

    public static class ContentItem {
        private String type;
        private String text;
        private ImageUrl image_url;

        public ContentItem() {
        }

        public static ContentItem text(String value) {
            ContentItem item = new ContentItem();
            item.type = "text";
            item.text = value;
            return item;
        }

        public static ContentItem imageDataUrl(String dataUrl) {
            ContentItem item = new ContentItem();
            item.type = "image_url";
            item.image_url = new ImageUrl(dataUrl);
            return item;
        }

        public String getType() {
            return type;
        }

        public String getText() {
            return text;
        }

        public ImageUrl getImage_url() {
            return image_url;
        }
    }

    public static class ImageUrl {
        private String url;

        public ImageUrl() {
        }

        public ImageUrl(String url) {
            this.url = url;
        }

        public String getUrl() {
            return url;
        }
    }
}
