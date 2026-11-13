package com.cocotalk.application.data.model;

import java.util.ArrayList;
import java.util.List;

public class Message {
    private String messageId;
    private String conversationId;
    private String senderId;
    private String type; // text / image / video / file / audio / call / location
    private String text;
    private List<Attachment> attachments;
    private List<String> seenBy;
    private boolean isRecalled;
    private boolean isPinned;
    private long createdAt;

    public Message() {
        this.attachments = new ArrayList<>();
        this.seenBy = new ArrayList<>();
    }

    public Message(String messageId, String conversationId, String senderId, String type, String text, long createdAt) {
        this.messageId = messageId;
        this.conversationId = conversationId;
        this.senderId = senderId;
        this.type = type;
        this.text = text;
        this.createdAt = createdAt;
        this.isRecalled = false;
        this.isPinned = false;
        this.attachments = new ArrayList<>();
        this.seenBy = new ArrayList<>();
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public List<Attachment> getAttachments() { return attachments; }
    public void setAttachments(List<Attachment> attachments) { this.attachments = attachments; }

    public List<String> getSeenBy() { return seenBy; }
    public void setSeenBy(List<String> seenBy) { this.seenBy = seenBy; }

    public boolean isRecalled() { return isRecalled; }
    public void setRecalled(boolean recalled) { isRecalled = recalled; }

    public boolean isPinned() { return isPinned; }
    public void setPinned(boolean pinned) { isPinned = pinned; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}