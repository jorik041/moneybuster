package net.eneiluj.moneybuster.model;

import androidx.annotation.Nullable;

import java.io.Serializable;

public class DBAccountProject implements Serializable {

    private long id;
    private String remoteId;
    private String name;
    private String ncUrl;
    private String password;

    public DBAccountProject(long id, String remoteId, @Nullable String password, String name, String ncUrl) {
        this.id = id;
        this.remoteId = remoteId;
        this.name = name;
        this.ncUrl = ncUrl;
        this.password = password;
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

    public String getncUrl() {
        return ncUrl;
    }

    public void setNcUrl(String ncUrl) {
        this.ncUrl = ncUrl;
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
        return "#DBAccountProject" + getId() + "/" + this.remoteId + "," + this.name + ", " + this.ncUrl + ", " + this.password;
    }
}
