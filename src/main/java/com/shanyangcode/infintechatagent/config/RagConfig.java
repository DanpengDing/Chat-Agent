package com.shanyangcode.infintechatagent.config;

import com.shanyangcode.infintechatagent.rag.QwenScoringModel;
import com.shanyangcode.infintechatagent.rag.QueryPreprocessor;
import com.shanyangcode.infintechatagent.rag.RecursiveDocumentSplitter;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.aggregator.ReRankingContentAggregator;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@Slf4j
@SuppressWarnings({"all"})
public class RagConfig {

    @Resource
    private EmbeddingModel embeddingModel;

    @Resource
    private EmbeddingStore<TextSegment> embeddingStore;

    @Resource
    private QueryPreprocessor queryPreprocessor;

    @Bean
    public EmbeddingStoreIngestor embeddingStoreIngestor() {
        log.info("[RAG] create EmbeddingStoreIngestor, chunkSize=800, overlap=200");
        RecursiveDocumentSplitter splitter = new RecursiveDocumentSplitter(800, 200);

        return EmbeddingStoreIngestor.builder()
                .documentSplitter(splitter)
                .textSegmentTransformer(textSegment -> TextSegment.from(
                        textSegment.metadata().getString("file_name") + "\n" + textSegment.text(),
                        textSegment.metadata()
                ))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();
    }

    @Bean
    public ContentRetriever contentRetriever() {
        log.info("[RAG] init base content retriever");
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(30)
                .minScore(0.55)
                .build();
    }

    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever,
                                                 QwenScoringModel qwenScoringModel) {
        ReRankingContentAggregator contentAggregator = ReRankingContentAggregator.builder()
                //rerank模型
                .scoringModel(qwenScoringModel)
                .minScore(0.55)
                .maxResults(5)
                .build();

        RetrievalAugmentor retrievalAugmentor = DefaultRetrievalAugmentor.builder()
                .queryTransformer(query -> {
                    String processedQuery = queryPreprocessor == null
                            ? query.text()
                            : queryPreprocessor.preprocess(query.text());
                    return List.of(Query.from(processedQuery, query.metadata()));
                })
                .contentRetriever(contentRetriever)
                .contentAggregator(contentAggregator)
                .build();

        log.info("[RAG] init retrieval augmentor with official rerank aggregator");
        return retrievalAugmentor;
    }
}
