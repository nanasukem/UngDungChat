package com.cocotalk.application.data.model;

public class User {
    private String uid;
    private String phone;
    private String displayName;
    private String nameLower;
    private String avatarUrl;
    private String bio;
    private boolean isOnline;
    private long lastSeen;
    private String fcmToken;
    private String language;

    public User() {}

    public User(String uid, String phone, String displayName, String avatarUrl) {
        this.uid = uid;
        this.phone = phone;
        this.displayName = displayName;
        this.nameLower = displayName != null ? displayName.toLowerCase() : "";
        this.avatarUrl = avatarUrl;
        this.bio = "";
        this.isOnline = true;
        this.lastSeen = System.currentTimeMillis();
        this.language = "vi";
    }

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        this.nameLower = displayName != null ? displayName.toLowerCase() : "";
    }

    public String getNameLower() { return nameLower; }
    public void setNameLower(String nameLower) { this.nameLower = nameLower; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public long getLastSeen() { return lastSeen; }
    public void setLastSeen(long lastSeen) { this.lastSeen = lastSeen; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}