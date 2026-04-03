package com.shanyangcode.infintechatagent.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@SuppressWarnings("unchecked")
public class QwenRerankClient {

    @Value("${langchain4j.community.dashscope.rerank-model.api-key}")
    private String apiKey;

    @Value("${langchain4j.community.dashscope.rerank-model.model-name}")
    private String modelName;

    private static final String RERANK_API_URL =
            "https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public QwenRerankClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public List<Integer> rerank(String query, List<String> documents, int topN) {
        try {
            List<Map<String, Object>> results = rerankInternal(query, documents, topN);
            if (results == null || results.isEmpty()) {
                return null;
            }

            List<Integer> rankedIndices = results.stream()
                    .sorted((left, right) -> Double.compare(
                            Double.parseDouble(right.get("relevance_score").toString()),
                            Double.parseDouble(left.get("relevance_score").toString())
                    ))
                    .map(result -> Integer.parseInt(result.get("index").toString()))
                    .collect(Collectors.toList());

            log.info("[Rerank] success: {} docs -> {}", documents.size(), rankedIndices);
            return rankedIndices;
        } catch (Exception e) {
            log.error("[Rerank] failed", e);
            return null;
        }
    }

    public List<Double> scoreAll(String query, List<String> documents) {
        try {
            List<Map<String, Object>> results = rerankInternal(
                    query,
                    documents,
                    documents == null ? 0 : documents.size()
            );
            if (results == null || documents == null || documents.isEmpty()) {
                return Collections.emptyList();
            }

            List<Double> scores = new ArrayList<>(Collections.nCopies(documents.size(), 0.0D));
            for (Map<String, Object> result : results) {
                int index = Integer.parseInt(result.get("index").toString());
                double score = Double.parseDouble(result.get("relevance_score").toString());
                if (index >= 0 && index < scores.size()) {
                    scores.set(index, score);
                }
            }
            return scores;
        } catch (Exception e) {
            log.error("[Rerank] scoreAll failed", e);
            return Collections.emptyList();
        }
    }

    private List<Map<String, Object>> rerankInternal(String query, List<String> documents, int topN) throws Exception {
        if (query == null || query.isBlank()) {
            log.error("[Rerank] query is blank");
            return null;
        }
        if (documents == null || documents.isEmpty()) {
            log.error("[Rerank] documents are empty");
            return null;
        }
        if (topN <= 0 || topN > documents.size()) {
            topN = documents.size();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(apiKey);

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName);
        requestBody.put("input", Map.of(
                "query", query,
                "documents", documents
        ));
        requestBody.put("parameters", Map.of("top_n", topN));

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);
        ResponseEntity<String> responseEntity =
                restTemplate.postForEntity(RERANK_API_URL, requestEntity, String.class);

        if (responseEntity.getStatusCode() != HttpStatus.OK) {
            log.error("[Rerank] http error: status={}, body={}",
                    responseEntity.getStatusCode(), responseEntity.getBody());
            return null;
        }

        Map<String, Object> responseMap = objectMapper.readValue(responseEntity.getBody(), Map.class);
        Map<String, Object> output = (Map<String, Object>) responseMap.get("output");
        return output == null ? null : (List<Map<String, Object>>) output.get("results");
    }
}
