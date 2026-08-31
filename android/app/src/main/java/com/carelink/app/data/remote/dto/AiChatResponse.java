package com.carelink.app.data.remote.dto;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.List;

public class AiChatResponse {

    private List<Choice> choices;
    private Usage usage;
    private String id;
    private String model;

    public List<Choice> getChoices() {
        return choices;
    }

    public void setChoices(List<Choice> choices) {
        this.choices = choices;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public static class Choice {
        private Integer index;
        private Message message;
        private String finish_reason;

        public Integer getIndex() {
            return index;
        }

        public void setIndex(Integer index) {
            this.index = index;
        }

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public String getFinish_reason() {
            return finish_reason;
        }

        public void setFinish_reason(String finish_reason) {
            this.finish_reason = finish_reason;
        }
    }

    public static class Message {
        private String role;
        private JsonElement content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public JsonElement getContent() {
            return content;
        }

        public void setContent(JsonElement content) {
            this.content = content;
        }

        public String extractTextContent() {
            if (content == null || content.isJsonNull()) {
                return null;
            }
            if (content.isJsonPrimitive()) {
                return content.getAsString();
            }
            if (content.isJsonArray()) {
                StringBuilder builder = new StringBuilder();
                JsonArray array = content.getAsJsonArray();
                for (JsonElement element : array) {
                    if (element == null || !element.isJsonObject()) {
                        continue;
                    }
                    JsonObject object = element.getAsJsonObject();
                    if (!object.has("type")) {
                        continue;
                    }
                    String type = object.get("type").getAsString();
                    if ("text".equals(type) && object.has("text")) {
                        if (builder.length() > 0) {
                            builder.append('\n');
                        }
                        builder.append(object.get("text").getAsString());
                    }
                }
                return builder.length() == 0 ? null : builder.toString();
            }
            return null;
        }
    }

    public static class Usage {
        private Integer prompt_tokens;
        private Integer completion_tokens;
        private Integer total_tokens;

        public Integer getPrompt_tokens() {
            return prompt_tokens;
        }

        public void setPrompt_tokens(Integer prompt_tokens) {
            this.prompt_tokens = prompt_tokens;
        }

        public Integer getCompletion_tokens() {
            return completion_tokens;
        }

        public void setCompletion_tokens(Integer completion_tokens) {
            this.completion_tokens = completion_tokens;
        }

        public Integer getTotal_tokens() {
            return total_tokens;
        }

        public void setTotal_tokens(Integer total_tokens) {
            this.total_tokens = total_tokens;
        }
    }
}
