package com.example.searchAPI.controller;

import com.example.searchAPI.service.AutoCompleteService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/autocomplete")
public class AutoCompleteController {

    @Autowired
    private AutoCompleteService autoCompleteService;

    @GetMapping("/")
    public List<String> autoComplete(@RequestParam(value = "keyword") String keyword,
                                     @RequestParam(value = "option") String option) {
        return autoCompleteService.autoComplete(keyword, option);
    }
}