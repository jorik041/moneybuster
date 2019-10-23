package net.eneiluj.moneybuster.model;

import androidx.annotation.Nullable;

import java.io.Serializable;

/**
 *
 */
public class DBMember implements Serializable {

    private long id;
    private long remoteId;
    private String name;
    private long projectId;
    private double weight;
    private boolean activated;
    private int state;
    private Integer r;
    private Integer g;
    private Integer b;

    public DBMember(long id, long remoteId, long projectId, String name, boolean activated,
                    double weight, int state,
                    @Nullable Integer r, @Nullable Integer g, @Nullable Integer b) {
        // key_id, key_remoteId, key_projectid, key_name, key_activated, key_weight
        this.id = id;
        this.remoteId = remoteId;
        this.name = name;
        this.projectId = projectId;
        this.weight = weight;
        this.activated = activated;
        this.state = state;
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public Integer getR() {
        return r;
    }

    public void setR(Integer r) {
        this.r = r;
    }

    public Integer getG() {
        return g;
    }

    public void setG(Integer g) {
        this.g = g;
    }

    public Integer getB() {
        return b;
    }

    public void setB(Integer b) {
        this.b = b;
    }

    public long getId() {
        return id;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
    }

    public double getWeight() {
        return weight;
    }

    public void setWeight(double weight) {
        this.weight = weight;
    }

    public boolean isActivated() {
        return activated;
    }

    public void setActivated(boolean activated) {
        this.activated = activated;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    @Override
    public String toString() {
        return "#DBMember" + getId() + "/" + this.remoteId + "," + this.name + ", p" + this.projectId
                + ", " + this.weight + ", "+ this.activated;
    }
}
