package com.example.searchAPI.controller;

import com.example.searchAPI.model.SearchCriteria;
import com.example.searchAPI.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class ElasticsearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping("/")
    public List<String> search(@RequestBody SearchCriteria criteria) {
        return searchService.search(criteria);
    }
}
