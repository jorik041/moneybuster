package net.eneiluj.moneybuster.model;

import androidx.annotation.Nullable;

import java.io.Serializable;

public class DBCategory implements Serializable {

    private long id;
    private long remoteId;
    private long projectId;
    private String name;
    private String icon;
    private String color;

    public DBCategory(long id, long remoteId, long projectId,
                      @Nullable String name, String icon, String color) {
        this.id = id;
        this.remoteId = remoteId;
        this.projectId = projectId;
        this.name = name;
        this.icon = icon;
        this.color = color;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getRemoteId() {
        return remoteId;
    }

    public void setRemoteId(long remoteId) {
        this.remoteId = remoteId;
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

    @Override
    public String toString() {
        return "#DBCategory" + getId() + "/" + this.remoteId + "," + this.name;
    }
}
