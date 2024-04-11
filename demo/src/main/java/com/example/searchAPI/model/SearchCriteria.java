package com.example.searchAPI.model;

import lombok.Data;
import java.util.List;


@Data
public class SearchCriteria {
    private final List<String> fieldDesignation;
    private final String period;
    private final String keyword;
    private final int maxDocument;
    private final int nowPage;
    private final String sortOption;
    private final List<String> categories;
    private final List<Integer> categoryMaxCounts;
}
