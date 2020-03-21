package lambda;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.google.common.base.Preconditions;
import lambda.dynamodb.Whiteboard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

public class SetWhiteboard implements RequestHandler<SetWhiteboardRequest, SetWhiteboardResponse> {
    private static final Logger log = LogManager.getLogger(SetWhiteboard.class);
    private static final DynamoDBTableMapper<Whiteboard, String, ?> dynamoDbTable =
            Clients.createWhiteboardDynamoDbMapper();

    @Override
    public SetWhiteboardResponse handleRequest(SetWhiteboardRequest input, Context context) {
        log.info("SetWhiteboard entry. input.identifier: {}", input.getIdentifier());

        final Whiteboard existingWhiteboard = dynamoDbTable.load(input.getIdentifier());
        Preconditions.checkArgument(existingWhiteboard != null, "Could not find whiteboard");

        final Whiteboard newWhiteboard = mergeWhiteboards(existingWhiteboard, input.getContent());
        if (newWhiteboard == null) {
            log.info("Could not merge whiteboards");
            return new SetWhiteboardResponse(input.getIdentifier(), false, existingWhiteboard.getContent());
        }

        dynamoDbTable.saveIfExists(newWhiteboard);

        return new SetWhiteboardResponse(newWhiteboard.getIdentifier(), true, newWhiteboard.getContent());
    }

    @Nullable
    private Whiteboard mergeWhiteboards(final Whiteboard existingWhiteboard, final String proposedContent) {
        final Whiteboard newWhiteboard = new Whiteboard();
        newWhiteboard.setIdentifier(existingWhiteboard.getIdentifier());

        // TODO do something more clever here like a merge, which could fail.
        newWhiteboard.setContent(proposedContent);

        return newWhiteboard;
    }
}
