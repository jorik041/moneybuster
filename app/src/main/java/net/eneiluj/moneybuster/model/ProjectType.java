package net.eneiluj.moneybuster.model;

import java.util.HashMap;
import java.util.Map;

public enum ProjectType {
    LOCAL("l"), COSPEND("c"), IHATEMONEY("i");

    private static Map<String, ProjectType> reverseMap = new HashMap<>();

    static {
        reverseMap.put(LOCAL.id, LOCAL);
        reverseMap.put(COSPEND.id, COSPEND);
        reverseMap.put(IHATEMONEY.id, IHATEMONEY);
    }

    private String id;

    ProjectType(String id) {
        this.id = id;
    }

    public static ProjectType getTypeById(String id) {
        return reverseMap.get(id);
    }

    public String getId() {
        return id;
    }
}
