package com.example.searchAPI.constant.autocomplete;

public enum Option {
    PREFIX("prefix"),
    SUFFIX("suffix"),
    CONTAINS("contains");

    private String keyword;

    Option(String keyword) {
        this.keyword = keyword;
    }

    public String get() {
        return keyword;
    }
}
