package net.eneiluj.moneybuster.model;

public class MenuProject {

    private long id;
    private String label;
    private String name;

    public MenuProject(long id, String name, String label) {
        this.id = id;
        this.name = name;
        this.label = label;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    @Override
    public String toString() {
        return this.label;
    }
}
