package lambda;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.datamodeling.ConversionSchemas;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.ConsistentReads;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.DefaultBatchLoadRetryStrategy;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.DefaultBatchWriteRetryStrategy;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.PaginationLoadingStrategy;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig.SaveBehavior;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverterFactory;
import lambda.dynamodb.Whiteboard;

public class Clients {
    private static final AmazonDynamoDB dynamoDb = AmazonDynamoDBClientBuilder.defaultClient();
    private static final String WHITEBOARD_TABLE_NAME = System.getenv("WHITEBOARD_TABLE_NAME");
    private static final DynamoDBTableMapper<Whiteboard, String, ?> whiteboardDynamoDbMapper
            = Clients.createWhiteboardDynamoDbMapper();
    static {
        whiteboardDynamoDbMapper.load("12345");
    }

    private static DynamoDBTableMapper<Whiteboard, String, ?> createWhiteboardDynamoDbMapper() {
        final DynamoDBMapperConfig config = DynamoDBMapperConfig.builder()
                .withSaveBehavior(SaveBehavior.UPDATE)
                .withConsistentReads(ConsistentReads.EVENTUAL)
                .withPaginationLoadingStrategy(PaginationLoadingStrategy.LAZY_LOADING)
                .withTableNameOverride(
                        DynamoDBMapperConfig.TableNameOverride.withTableNameReplacement(WHITEBOARD_TABLE_NAME))
                .withBatchWriteRetryStrategy(DefaultBatchWriteRetryStrategy.INSTANCE)
                .withBatchLoadRetryStrategy(DefaultBatchLoadRetryStrategy.INSTANCE)
                .withTypeConverterFactory(DynamoDBTypeConverterFactory.standard())
                .withConversionSchema(ConversionSchemas.V2)
                .build();

        final DynamoDBMapper dynamoDbMapper = new DynamoDBMapper(dynamoDb, config);
        return dynamoDbMapper.newTableMapper(Whiteboard.class);
    }

    public static DynamoDBTableMapper<Whiteboard, String, ?> getWhiteboardDynamoDbMapper() {
        return whiteboardDynamoDbMapper;
    }


}
