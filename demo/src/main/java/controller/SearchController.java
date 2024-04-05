package controller;

import config.ElasticConfiguration;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/search")
public class SearchController {

    public void execute(@RequestParam(value = "필드", required = true) List<String> fieldDesignation,
                        @RequestParam(value = "카테고리 및 카테코리별 최대 출력 건수", required = true) List<Map<String, Long>> categories,
                        @RequestParam(value = "조회 기간", required = true) String period,
                        @RequestParam(value = "고급 검색", required = true) String searchWord,
                        @RequestParam(value = "최대 문서수", required = true) long maxDocument,
                        @RequestParam(value = "현재 페이지", required = true) long nowPage,
                        @RequestParam(value = "정렬", required = true) String sortConfig) {

        try (ElasticConfiguration elasticConfiguration = new ElasticConfiguration("localhost", 9200, "", "http")) {

        } catch (Exception e) {

        }
        checkField(fieldDesignation);

    }

    void checkField(List<String> fieldDesignation) {

    }
}

