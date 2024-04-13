package com.example.searchAPI.constant.topsearched;

public enum TopSearched {
    ALL("all"),

    DATE_FORMAT("yyyy.MM.dd"),

    DELIMETER("~"),

    DATE_PATTERN("\\d{4}\\.\\d{2}\\.\\d{2}~\\d{4}\\.\\d{2}\\.\\d{2}");

    private String keyword;

    TopSearched(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
