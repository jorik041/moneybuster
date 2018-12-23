package net.eneiluj.ihatemoney.model;

import android.support.annotation.Nullable;

import java.io.Serializable;

public class Category implements Serializable {

    @Nullable
    public final String memberName;
    @Nullable
    public final Long memberRemoteId;

    public Category(@Nullable String memberName, @Nullable Long memberRemoteId) {
        this.memberName = memberName;
        this.memberRemoteId = memberRemoteId;
    }
}
