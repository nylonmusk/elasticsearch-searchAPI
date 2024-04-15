package com.example.searchAPI.constant.topsearched;

public enum TopSearched {
    ALL("all"),

    DATE_FORMAT("yyyy.MM.dd"),

    DELIMETER("~"),

    DATE_PATTERN("^\\d{4}\\.(0[1-9]|1[012])\\.(0[1-9]|[12][0-9]|3[01])$");

    private String keyword;

    TopSearched(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
