package com.example.searchAPI.controller;

import com.example.searchAPI.service.TopSearchedService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/topsearched")
public class TopSearchedController {

    @Autowired
    private TopSearchedService topSearchedService;

    @GetMapping("/")
    public List<String> topSearched(@RequestParam(required = false) String period,
                                    @RequestParam(required = false) Integer N) {
        return topSearchedService.topSearched(period, N);
    }
}