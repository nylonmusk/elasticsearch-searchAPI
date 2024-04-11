//package com.example.searchAPI.controller;
//
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RequestParam;
//import org.springframework.web.bind.annotation.RestController;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@RestController
//@RequestMapping("/api/topsearched")
//public class TopSearchedController {
//
//    @Value("${search.index}")
//    private String index;
//
//    @Value("${search.host}")
//    private String host;
//
//    @Value("${search.port}")
//    private int port;
//
//    @Value("${search.username}")
//    private String username;
//
//    @Value("${search.protocol}")
//    private String protocol;
//
//    @GetMapping("/")
//    public List<String> topSearched(@RequestParam(required = true) String period,
//                                    @RequestParam(required = true) int N) {
//        // 여기에 인기 검색어 로직 구현
//        List<String> list = new ArrayList<>();
//
//        // 예시 데이터
//        list.add("Example Search Term 1");
//        list.add("Example Search Term 2");
//        return list;
//    }
//}
