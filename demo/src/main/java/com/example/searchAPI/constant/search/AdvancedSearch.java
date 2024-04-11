package com.example.searchAPI.constant.search;

public enum AdvancedSearch {
    INCLUDE("+"),
    EXCLUDE("-"),
    EQUAL("\"");
    private String keyword;

    AdvancedSearch(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
