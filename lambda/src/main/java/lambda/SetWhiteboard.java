package lambda;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import lambda.dynamodb.Whiteboard;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class SetWhiteboard extends BaseHandler {
    private static final Logger log = LogManager.getLogger(SetWhiteboard.class);
    private static final DynamoDBTableMapper<Whiteboard, String, ?> dynamoDbTable =
            Clients.getWhiteboardDynamoDbMapper();

    private static final TypeReference<SetWhiteboardRequest> setWhiteboardRequestTypeReference =
            new TypeReference<>() {};
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ObjectReader setWhiteboardRequestObjectReader =
            objectMapper.readerFor(setWhiteboardRequestTypeReference);
    private static final TypeReference<SetWhiteboardResponse> setWhiteboardResponseTypeReference =
            new TypeReference<>() {};
    private static final ObjectWriter setWhiteboardResponseWriter =
            objectMapper.writerFor(setWhiteboardResponseTypeReference);

    @SneakyThrows({JsonProcessingException.class, IOException.class})
    @Override
    public String handleRequestInternal(String input) {
        final SetWhiteboardRequest request = setWhiteboardRequestObjectReader.readValue(input);

        Whiteboard whiteboard = dynamoDbTable.load(request.getIdentifier());
        Preconditions.checkState(whiteboard != null);
        whiteboard.setContent(request.getContent());
        dynamoDbTable.saveIfExists(whiteboard);
        log.info("whiteboard version {}", whiteboard.getVersion());

        final SetWhiteboardResponse response = new SetWhiteboardResponse(
                whiteboard.getIdentifier(),
                whiteboard.getContent(),
                whiteboard.getVersion());
        return setWhiteboardResponseWriter.writeValueAsString(response);
    }
}
