package net.eneiluj.ihatemoney.model;


/**
 * DBLogjob represents a single logjob from the local SQLite database with all attributes.
 */
public class MenuProject {

    private long id;
    private String name;

    public MenuProject(long id, String name) {
        this.id = id;
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }
}
