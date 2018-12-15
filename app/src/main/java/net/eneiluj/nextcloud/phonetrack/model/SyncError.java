package net.eneiluj.nextcloud.phonetrack.model;

public class SyncError {
    private long timestamp;
    private String message;

    public SyncError(long timestamp, String message) {
        this.timestamp = timestamp;
        this.message = message;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }
}
