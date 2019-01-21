package net.eneiluj.moneybuster.model;

import java.io.Serializable;

/**
 * DBLogjob represents a single logjob from the local SQLite database with all attributes.
 */
public class DBProject implements Serializable {

    private long id;
    private String remoteId;
    private String name;
    private String ihmUrl;
    private String email;
    private String password;

    public DBProject(long id, String remoteId, String password, String name, String ihmUrl, String email) {
        this.id = id;
        this.remoteId = remoteId;
        this.name = name;
        this.ihmUrl = ihmUrl;
        this.email = email;
        this.password = password;
    }

    public boolean isLocal() {
        return (ihmUrl == null || ihmUrl.equals(""));
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

    public String getIhmUrl() {
        return ihmUrl;
    }

    public void setIhmUrl(String ihmUrl) {
        this.ihmUrl = ihmUrl;
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

    @Override
    public String toString() {
        return "#DBProject" + getId() + "/" + this.remoteId + "," + this.name + ", " + this.ihmUrl + ", " + this.email;
    }
}
