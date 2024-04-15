package com.example.searchAPI.constant.search;

public enum Period {
    ALL("all"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),

    TARGET("writeDate"),

    DATE_FORMAT("yyyy.MM.dd"),

    DELIMETER("~");

    private String keyword;

    Period(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
