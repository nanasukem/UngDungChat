package com.cocotalk.application.data.model;

public class FriendRequest {
    private String friendUid;
    private String senderUid;
    private String status;    // pending / accepted / blocked
    private String nickname;
    private long createdAt;

    public FriendRequest() {}

    public FriendRequest(String friendUid, String senderUid, String status, String nickname, long createdAt) {
        this.friendUid = friendUid;
        this.senderUid = senderUid;
        this.status = status;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public String getFriendUid() { return friendUid; }
    public void setFriendUid(String friendUid) { this.friendUid = friendUid; }

    public String getSenderUid() { return senderUid; }
    public void setSenderUid(String senderUid) { this.senderUid = senderUid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}