package net.eneiluj.moneybuster.model;

import androidx.annotation.Nullable;

import java.io.Serializable;

public class DBCurrency implements Serializable {

    private long id;
    private long remoteId;
    private long projectId;
    private String name;
    private double exchangeRate;
    private int state;

    public DBCurrency(long id, long remoteId, long projectId,
                      @Nullable String name, double exchangeRate, int state) {
        this.id = id;
        this.remoteId = remoteId;
        this.projectId = projectId;
        this.name = name;
        this.exchangeRate = exchangeRate;
        this.state = state;
    }

    public double getExchangeRate() {
        return exchangeRate;
    }

    public void setExchangeRate(double exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    public long getProjectId() {
        return projectId;
    }

    public void setProjectId(long projectId) {
        this.projectId = projectId;
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

    public int getState() {return this.state;}

    @Override
    public String toString() {
        return "#DBCategory" + getId() + "/" + this.remoteId + "," + this.name + " , state: "+this.state;
    }
}
