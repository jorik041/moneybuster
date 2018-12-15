package net.eneiluj.nextcloud.phonetrack.model;

import java.io.Serializable;

/**
 * CloudSession represents a remote logjob from an OwnCloud server.
 * It can be directly generated from the JSON answer from the server.
 */
public class CloudSession implements Serializable {
    private String name;
    private String token;
    private String nextURL;

    public CloudSession(String name, String token, String nextURL) {
        this.name = name;
        this.token = token;
        this.nextURL = nextURL;
    }

    public String getToken() {
        return this.token;
    }

    public String getName() {
        return this.name;
    }

    public String getNextURL() {
        return this.nextURL;
    }

    @Override
    public String toString() {
        return "Session(" + this.name + ", " + this.token + ", " + this.nextURL + ")";
    }
}