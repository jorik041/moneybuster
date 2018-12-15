package net.eneiluj.nextcloud.phonetrack.model;

/**
 * DBSession represents a single session from the local SQLite database with all attributes.
 * It extends CloudSession with attributes required for local data management.
 */
public class DBSession extends CloudSession {

    private long id;
    private String name = "";
    private String token = "";
    private String nextURL = "";

    public DBSession(long id, String token, String name, String nextURL) {
        super(name, token, nextURL);
        this.id = id;
    }

    public long getId() {
        return id;
    }

    @Override
    public String toString() {
        return "#" + this.id + "/" + super.toString();
    }
}
