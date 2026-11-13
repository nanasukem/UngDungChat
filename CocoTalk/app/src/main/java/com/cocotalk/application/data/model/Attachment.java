package com.cocotalk.application.data.model;

public class Attachment {
    private String url;
    private String fileName;
    private String fileType;
    private long fileSize;
    private long duration; // cho audio/video

    public Attachment() {}

    public Attachment(String url, String fileName, String fileType, long fileSize) {
        this.url = url;
        this.fileName = fileName;
        this.fileType = fileType;
        this.fileSize = fileSize;
    }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }

    public long getDuration() { return duration; }
    public void setDuration(long duration) { this.duration = duration; }
}