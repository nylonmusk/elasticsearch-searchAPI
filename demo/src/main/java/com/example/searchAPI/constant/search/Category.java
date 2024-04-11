package com.example.searchAPI.constant.search;

public enum Category {
    CATEGORY("ctgry");

    private String keyword;

    Category(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
