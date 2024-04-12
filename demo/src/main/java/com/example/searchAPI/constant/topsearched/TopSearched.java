package com.example.searchAPI.constant.topsearched;

public enum TopSearched {
    ALL("all"),

    DATE_FORMAT("yyyy.MM.dd"),

    DELIMETER("~");

    private String keyword;

    TopSearched(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
