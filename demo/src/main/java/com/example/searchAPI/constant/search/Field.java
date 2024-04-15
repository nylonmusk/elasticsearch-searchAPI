package com.example.searchAPI.constant.search;

public enum Field {

    ALL("all");

    private String keyword;

    Field(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
