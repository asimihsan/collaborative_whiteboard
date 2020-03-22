package lambda;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import lambda.dynamodb.Whiteboard;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class GetWhiteboard extends BaseHandler {
    private static final Logger log = LogManager.getLogger(GetWhiteboard.class);
    private static final DynamoDBTableMapper<Whiteboard, String, ?> dynamoDbTable =
            Clients.getWhiteboardDynamoDbMapper();

    private static final TypeReference<GetWhiteboardRequest> getWhiteboardRequestTypeReference =
            new TypeReference<>() {};
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final ObjectReader getWhiteboardRequestObjectReader =
            objectMapper.readerFor(getWhiteboardRequestTypeReference);
    private static final TypeReference<GetWhiteboardResponse> getWhiteboardResponseTypeReference =
            new TypeReference<>() {};
    private static final ObjectWriter getWhiteboardResponseWriter =
            objectMapper.writerFor(getWhiteboardResponseTypeReference);

    @SneakyThrows({JsonProcessingException.class, IOException.class})
    @Override
    public String handleRequestInternal(String input) {
        final GetWhiteboardRequest request = getWhiteboardRequestObjectReader.readValue(input);

        Whiteboard whiteboard = dynamoDbTable.load(request.getIdentifier());
        if (whiteboard == null) {
            log.info("Whiteboard does not exist");
            final Whiteboard newWhiteboard = new Whiteboard();
            newWhiteboard.setIdentifier(request.getIdentifier());
            newWhiteboard.setContent("");
            dynamoDbTable.saveIfNotExists(newWhiteboard);
            whiteboard = newWhiteboard;
        }
        log.info("whiteboard version {}", whiteboard.getVersion());

        final GetWhiteboardResponse response = new GetWhiteboardResponse(
                whiteboard.getIdentifier(),
                whiteboard.getContent(),
                whiteboard.getVersion());
        return getWhiteboardResponseWriter.writeValueAsString(response);
    }
}
