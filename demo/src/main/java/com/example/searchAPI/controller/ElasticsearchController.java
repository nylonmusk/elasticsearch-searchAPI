package com.example.searchAPI.controller;

import com.example.searchAPI.model.SearchCriteria;
import com.example.searchAPI.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/search")
public class ElasticsearchController {

    @Autowired
    private SearchService searchService;

    @GetMapping("/")
    public List<String> search(@ModelAttribute SearchCriteria criteria) {
        return searchService.search(criteria);
    }
}
