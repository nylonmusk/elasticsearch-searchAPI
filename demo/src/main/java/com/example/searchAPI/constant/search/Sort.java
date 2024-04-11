package com.example.searchAPI.constant.search;

public enum Sort {
    ACCURACY("accuracy"),
    LATEST("latest"),
    EARLIEST("earliest"),

    TARGET("writeDate");

    private String keyword;

    Sort(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
