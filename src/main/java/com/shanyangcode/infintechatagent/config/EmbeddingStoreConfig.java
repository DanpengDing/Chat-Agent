package com.shanyangcode.infintechatagent.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.milvus.MilvusEmbeddingStore;
import io.milvus.client.MilvusServiceClient;
import io.milvus.common.clientenum.ConsistencyLevelEnum;
import io.milvus.param.ConnectParam;
import io.milvus.param.IndexType;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.DropCollectionParam;
import io.milvus.param.collection.HasCollectionParam;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class EmbeddingStoreConfig {

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.collection-name}")
    private String collectionName;

    @Value("${milvus.username:}")
    private String username;

    @Value("${milvus.password:}")
    private String password;

    @Value("${milvus.dimension:1024}")
    private int dimension;

    @Bean
    public EmbeddingStore<TextSegment> initEmbeddingStore() {
        MilvusServiceClient milvusClient = createMilvusClient();
        recreateCollectionIfNeeded(milvusClient);

        return MilvusEmbeddingStore.builder()
                .milvusClient(milvusClient)
                .collectionName(collectionName)
                .dimension(dimension)
                .indexType(IndexType.FLAT)
                .metricType(MetricType.COSINE)
                .username(username)
                .password(password)
                .consistencyLevel(ConsistencyLevelEnum.EVENTUALLY)
                .autoFlushOnInsert(true)
                .idFieldName("id")
                .textFieldName("text")
                .metadataFieldName("metadata")
                .vectorFieldName("vector")
                .build();
    }

    private MilvusServiceClient createMilvusClient() {
        ConnectParam.Builder builder = ConnectParam.newBuilder()
                .withHost(host)
                .withPort(port);

        if (username != null && !username.isBlank()) {
            builder.withAuthorization(username, password == null ? "" : password);
        }

        return new MilvusServiceClient(builder.build());
    }

    private void recreateCollectionIfNeeded(MilvusServiceClient milvusClient) {
        R<Boolean> hasCollection = milvusClient.hasCollection(
                HasCollectionParam.newBuilder()
                        .withCollectionName(collectionName)
                        .build()
        );

        if (Boolean.TRUE.equals(hasCollection.getData())) {
            R<RpcStatus> dropResponse = milvusClient.dropCollection(
                    DropCollectionParam.newBuilder()
                            .withCollectionName(collectionName)
                            .build()
            );
            if (dropResponse.getStatus() != 0) {
                throw new IllegalStateException("删除已存在的Milvus collection失败: " + dropResponse.getMessage());
            }
        }
    }
}

