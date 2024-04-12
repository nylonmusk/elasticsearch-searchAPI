package com.example.searchAPI.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public class SearchCriteria {
    private final List<String> fieldDesignation;
    private final String period;
    private final String keyword;
    private final Integer maxDocument;
    private final Integer nowPage;
    private final String sortOption;
    private final List<String> categories;
    private final List<Integer> categoryMaxCounts;
}
