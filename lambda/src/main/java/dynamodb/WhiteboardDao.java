package dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Value;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.time.Duration;

import static com.google.common.base.Preconditions.checkNotNull;

public class WhiteboardDao {
    private static final Logger log = LogManager.getLogger(WhiteboardDao.class);

    private final DynamoDBTableMapper<Whiteboard, String, Long> dynamoDbTable;

    @Value
    private static class WhiteboardCacheKey {
        String identifier;
        Long version;
    }
    private final Cache<WhiteboardCacheKey, Whiteboard> whiteboardCache;

    public WhiteboardDao(final DynamoDBTableMapper<Whiteboard, String, Long> dynamoDbTable) {
        this(dynamoDbTable,
                CacheBuilder.newBuilder()
                        .maximumSize(100_000)
                        .expireAfterAccess(Duration.ofMinutes(1))
                        .build());
    }

    public WhiteboardDao(final DynamoDBTableMapper<Whiteboard, String, Long> dynamoDbTable,
                         final Cache<WhiteboardCacheKey, Whiteboard> whiteboardCache) {
        this.dynamoDbTable = checkNotNull(dynamoDbTable);
        this.whiteboardCache = checkNotNull(whiteboardCache);
    }

    @Nullable
    public Whiteboard getWhiteboardAtVersion(final String identifier, final Long version) {
        log.info("getWhiteboardAtVersion identifier {} version {}", identifier, version);

        final WhiteboardCacheKey whiteboardCacheKey = new WhiteboardCacheKey(identifier, version);
        final Whiteboard cachedWhiteboard = whiteboardCache.getIfPresent(whiteboardCacheKey);
        if (cachedWhiteboard != null) {
            log.info("returning cached whiteboard identifier {} version {}",
                    cachedWhiteboard.getIdentifier(), cachedWhiteboard.getVersion());
            return cachedWhiteboard;
        }

        final Whiteboard whiteboardForSearch = new Whiteboard();
        whiteboardForSearch.setIdentifier(identifier);
        final Condition versionRangeKeyCondition = new Condition()
                        .withComparisonOperator(ComparisonOperator.EQ)
                        .withAttributeValueList(new AttributeValue().withN(version.toString()));

        final DynamoDBQueryExpression<Whiteboard> queryExpression = new DynamoDBQueryExpression<Whiteboard>()
                .withHashKeyValues(whiteboardForSearch)
                .withRangeKeyCondition("version", versionRangeKeyCondition)
                .withLimit(1);
        final QueryResultPage<Whiteboard> results = dynamoDbTable.queryPage(queryExpression);
        if (results.getCount() == 0) {
            log.info("getWhiteboardAtVersion no whiteboard found for identifier {} version {}",
                    identifier, version);
            return null;
        }
        final Whiteboard result = results.getResults().get(0);
        log.info("getWhiteboardAtVersion identifier {} found with version {}", identifier, result.getVersion());
        whiteboardCache.put(new WhiteboardCacheKey(identifier, result.getVersion()), result);
        return result;
    }

    @Nullable
    public Whiteboard getNewestWhiteboard(final String identifier, final boolean consistentRead) {
        log.info("getNewestWhiteboard identifier {}, consistentRead {}", identifier, consistentRead);
        final Whiteboard whiteboardForSearch = new Whiteboard();
        whiteboardForSearch.setIdentifier(identifier);
        final DynamoDBQueryExpression<Whiteboard> queryExpression = new DynamoDBQueryExpression<Whiteboard>()
                .withHashKeyValues(whiteboardForSearch)
                .withScanIndexForward(false)
                .withLimit(1)
                .withConsistentRead(consistentRead);
        final QueryResultPage<Whiteboard> results = dynamoDbTable.queryPage(queryExpression);
        if (results.getCount() == 0) {
            log.info("getNewestWhiteboard no whiteboard found for identifier {}", identifier);
            return null;
        }
        final Whiteboard result = results.getResults().get(0);
        log.info("getNewestWhiteboard identifier {} found with version {}", identifier, result.getVersion());
        whiteboardCache.put(new WhiteboardCacheKey(identifier, result.getVersion()), result);
        return result;
    }

    public void saveCompletelyNewWhiteboard(final Whiteboard whiteboard) {
        dynamoDbTable.saveIfNotExists(whiteboard);
    }

    public void saveNewWhiteboardVersion(final Whiteboard whiteboard) {
        dynamoDbTable.saveIfNotExists(whiteboard);
    }

}
