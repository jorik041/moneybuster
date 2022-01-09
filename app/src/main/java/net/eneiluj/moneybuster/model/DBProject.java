package net.eneiluj.moneybuster.model;

import androidx.annotation.Nullable;

import java.io.Serializable;

public class DBProject implements Serializable {

    private long id;
    private String remoteId;
    private String name;
    private String serverUrl;
    private String email;
    private String password;
    private Long lastPayerId;
    private ProjectType type;
    private Long lastSyncedTimestamp;
    private String currencyName;
    private boolean deletionDisabled;
    private int myAccessLevel;

    public static final int ACCESS_LEVEL_UNKNOWN = -1;
    public static final int ACCESS_LEVEL_NONE = 0;
    public static final int ACCESS_LEVEL_VIEWER = 1;
    public static final int ACCESS_LEVEL_PARTICIPANT = 2;
    public static final int ACCESS_LEVEL_MAINTAINER = 3;
    public static final int ACCESS_LEVEL_ADMIN = 4;

    public DBProject(long id, String remoteId, String password, String name, String serverUrl,
                     String email, Long lastPayerId, ProjectType type, Long lastSyncedTimestamp,
                     @Nullable String currencyName, boolean deletionDisabled, int myAccessLevel) {
        this.id = id;
        this.remoteId = remoteId;
        this.name = name;
        this.serverUrl = serverUrl;
        this.email = email;
        this.password = password;
        this.lastPayerId = lastPayerId;
        this.lastSyncedTimestamp = lastSyncedTimestamp;
        this.type = type;
        this.currencyName = currencyName;
        this.deletionDisabled = deletionDisabled;
        this.myAccessLevel = myAccessLevel;
    }

    public int getMyAccessLevel() {
        return myAccessLevel;
    }

    public void setMyAccessLevel(int myAccessLevel) {
        this.myAccessLevel = myAccessLevel;
    }

    public boolean isDeletionDisabled() {
        return deletionDisabled;
    }

    public void setDeletionDisabled(boolean deletionDisabled) {
        this.deletionDisabled = deletionDisabled;
    }

    public String getCurrencyName() {
        return currencyName;
    }

    public void setCurrencyName(String currencyName) {
        this.currencyName = currencyName;
    }

    public Long getLastSyncedTimestamp() {
        return lastSyncedTimestamp;
    }

    public void setLastSyncedTimestamp(Long lastSyncedTimestamp) {
        this.lastSyncedTimestamp = lastSyncedTimestamp;
    }

    public Long getLastPayerId() {
        return lastPayerId;
    }

    public void setLastPayerId(Long lastPayerId) {
        this.lastPayerId = lastPayerId;
    }

    public boolean isLocal() {
        return ProjectType.LOCAL.equals(type);
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(String remoteId) {
        this.remoteId = remoteId;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public void setServerUrl(String serverUrl) {
        this.serverUrl = serverUrl;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ProjectType getType() {
        return type;
    }

    public void setType(ProjectType type) {
        this.type = type;
    }

    @Override
    public String toString() {
        return "#DBProject" + getId() + "/" + this.remoteId + "," + this.name + ", " + this.serverUrl + ", " + this.email;
    }
}
