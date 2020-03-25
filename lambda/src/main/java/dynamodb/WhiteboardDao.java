package dynamodb;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBSaveExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.QueryResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ConditionalOperator;
import com.amazonaws.services.dynamodbv2.model.ExpectedAttributeValue;
import com.google.common.collect.ImmutableMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

import java.util.Map;

import static com.google.common.base.Preconditions.checkNotNull;

public class WhiteboardDao {
    private static final Logger log = LogManager.getLogger(WhiteboardDao.class);

    private final DynamoDBTableMapper<Whiteboard, String, Long> dynamoDbTable;

    public WhiteboardDao(final DynamoDBTableMapper<Whiteboard, String, Long> dynamoDbTable) {
        this.dynamoDbTable = checkNotNull(dynamoDbTable);
    }

    @Nullable
    public Whiteboard getWhiteboardAtVersion(final String identifier, final Long version) {
        log.info("getWhiteboardAtVersion identifier {} version {}", identifier, version);
        final Whiteboard whiteboardForSearch = new Whiteboard();
        whiteboardForSearch.setIdentifier(identifier);
        whiteboardForSearch.setVersion(version);
        final DynamoDBQueryExpression<Whiteboard> queryExpression = new DynamoDBQueryExpression<Whiteboard>()
                .withHashKeyValues(whiteboardForSearch)
                .withLimit(1);
        final QueryResultPage<Whiteboard> results = dynamoDbTable.queryPage(queryExpression);
        if (results.getCount() == 0) {
            log.info("getWhiteboardAtVersion no whiteboard found for identifier {} version {}",
                    identifier, version);
            return null;
        }
        final Whiteboard result = results.getResults().get(0);
        log.info("getNewestWhiteboard identifier {} found with version {}", identifier, result.getVersion());
        return result;
    }

    @Nullable
    public Whiteboard getNewestWhiteboard(final String identifier) {
        log.info("getNewestWhiteboard identifier {}", identifier);
        final Whiteboard whiteboardForSearch = new Whiteboard();
        whiteboardForSearch.setIdentifier(identifier);
        final DynamoDBQueryExpression<Whiteboard> queryExpression = new DynamoDBQueryExpression<Whiteboard>()
                .withHashKeyValues(whiteboardForSearch)
                .withScanIndexForward(false)
                .withLimit(1);
        final QueryResultPage<Whiteboard> results = dynamoDbTable.queryPage(queryExpression);
        if (results.getCount() == 0) {
            log.info("getNewestWhiteboard no whiteboard found for identifier {}", identifier);
            return null;
        }
        final Whiteboard result = results.getResults().get(0);
        log.info("getNewestWhiteboard identifier {} found with version {}", identifier, result.getVersion());
        return result;
    }

    public void saveCompletelyNewWhiteboard(final Whiteboard whiteboard) {
        dynamoDbTable.saveIfNotExists(whiteboard);
    }

    public void saveNewWhiteboardVersion(final Whiteboard whiteboard) {
        dynamoDbTable.saveIfNotExists(whiteboard);
    }

}
