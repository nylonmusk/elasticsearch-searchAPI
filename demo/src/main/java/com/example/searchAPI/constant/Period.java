package com.example.searchAPI.constant;

public enum Period {
    ALL("all"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year");

    private String keyword;

    Period(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
