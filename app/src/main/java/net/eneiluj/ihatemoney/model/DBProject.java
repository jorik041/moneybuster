package net.eneiluj.ihatemoney.model;

import java.io.Serializable;

/**
 * DBLogjob represents a single logjob from the local SQLite database with all attributes.
 */
public class DBProject implements Serializable {

    private long id;
    private String remoteId;
    private String name;
    private String IHMurl;
    private String email;

    public DBProject(long id, String remoteId, String name, String IHMurl, String email) {
        this.id = id;
        this.remoteId = remoteId;
        this.name = name;
        this.IHMurl = IHMurl;
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
        return "#DBProject" + getId() + "/" + this.remoteId + "," + this.name + ", " + this.IHMurl + ", " + this.email;
    }
}
