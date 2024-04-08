package com.example.searchAPI.constant;

public enum Sort {
    ACCURACY("accuracy"),
    LATEST("latest"),
    EARLIEST("earliest");

    private String keyword;

    Sort(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
