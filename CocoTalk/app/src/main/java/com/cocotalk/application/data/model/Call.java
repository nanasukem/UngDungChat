package com.cocotalk.application.data.model;

import java.util.ArrayList;
import java.util.List;

public class Call {
    private String callId;
    private String callerId;
    private List<String> calleeIds;
    private String type;   // voice / video
    private String status; // ringing / accepted / ended / missed
    private long createdAt;

    public Call() {
        this.calleeIds = new ArrayList<>();
    }

    public Call(String callId, String callerId, List<String> calleeIds, String type, String status, long createdAt) {
        this.callId = callId;
        this.callerId = callerId;
        this.calleeIds = calleeIds;
        this.type = type;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getCallId() { return callId; }
    public void setCallId(String callId) { this.callId = callId; }

    public String getCallerId() { return callerId; }
    public void setCallerId(String callerId) { this.callerId = callerId; }

    public List<String> getCalleeIds() { return calleeIds; }
    public void setCalleeIds(List<String> calleeIds) { this.calleeIds = calleeIds; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}