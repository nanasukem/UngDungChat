package com.cocotalk.application.data.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Conversation {
    private String id; // Chat 1-1
    private String type; // direct / group
    private List<String> members;
    private String groupName;
    private String groupAvatar;
    private List<String> admins;
    private String lastMessage;
    private String lastSenderId;
    private long lastMessageTime;
    private Map<String, Integer> unreadCount;
    private List<String> pinnedBy;

    public Conversation() {
        this.members = new ArrayList<>();
        this.admins = new ArrayList<>();
        this.unreadCount = new HashMap<>();
        this.pinnedBy = new ArrayList<>();
    }

    public Conversation(String id, String type, List<String> members, String lastMessage, long lastMessageTime) {
        this.id = id;
        this.type = type;
        this.members = members;
        this.lastMessage = lastMessage;
        this.lastMessageTime = lastMessageTime;
        this.admins = new ArrayList<>();
        this.unreadCount = new HashMap<>();
        this.pinnedBy = new ArrayList<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public List<String> getMembers() { return members; }
    public void setMembers(List<String> members) { this.members = members; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getGroupAvatar() { return groupAvatar; }
    public void setGroupAvatar(String groupAvatar) { this.groupAvatar = groupAvatar; }

    public List<String> getAdmins() { return admins; }
    public void setAdmins(List<String> admins) { this.admins = admins; }

    public String getLastMessage() { return lastMessage; }
    public void setLastMessage(String lastMessage) { this.lastMessage = lastMessage; }

    public String getLastSenderId() { return lastSenderId; }
    public void setLastSenderId(String lastSenderId) { this.lastSenderId = lastSenderId; }

    public long getLastMessageTime() { return lastMessageTime; }
    public void setLastMessageTime(long lastMessageTime) { this.lastMessageTime = lastMessageTime; }

    public Map<String, Integer> getUnreadCount() { return unreadCount; }
    public void setUnreadCount(Map<String, Integer> unreadCount) { this.unreadCount = unreadCount; }

    public List<String> getPinnedBy() { return pinnedBy; }
    public void setPinnedBy(List<String> pinnedBy) { this.pinnedBy = pinnedBy; }
}