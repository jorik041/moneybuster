package net.eneiluj.moneybuster.model;

import android.support.annotation.Nullable;

import java.io.Serializable;

public class Category implements Serializable {

    @Nullable
    public final String memberName;
    @Nullable
    public final Long memberId;

    public Category(@Nullable String memberName, @Nullable Long memberId) {
        this.memberName = memberName;
        this.memberId = memberId;
    }
}
