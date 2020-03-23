package lambda;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import lambda.dynamodb.Whiteboard;
import logic.MxGraphDocumentMerger;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class WhiteboardHandler extends BaseHandler {
    private static final Logger log = LogManager.getLogger(WhiteboardHandler.class);
    private static final DynamoDBTableMapper<Whiteboard, String, ?> dynamoDbTable =
            Clients.getWhiteboardDynamoDbMapper();

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final TypeReference<GetWhiteboardRequest> getWhiteboardRequestTypeReference =
            new TypeReference<>() {};
    private static final ObjectReader getWhiteboardRequestObjectReader =
            objectMapper.readerFor(getWhiteboardRequestTypeReference);
    private static final TypeReference<GetWhiteboardResponse> getWhiteboardResponseTypeReference =
            new TypeReference<>() {};
    private static final ObjectWriter getWhiteboardResponseWriter =
            objectMapper.writerFor(getWhiteboardResponseTypeReference);

    private static final TypeReference<SetWhiteboardRequest> setWhiteboardRequestTypeReference =
            new TypeReference<>() {};
    private static final ObjectReader setWhiteboardRequestObjectReader =
            objectMapper.readerFor(setWhiteboardRequestTypeReference);
    private static final TypeReference<SetWhiteboardResponse> setWhiteboardResponseTypeReference =
            new TypeReference<>() {};
    private static final ObjectWriter setWhiteboardResponseWriter =
            objectMapper.writerFor(setWhiteboardResponseTypeReference);

    private final MxGraphDocumentMerger merger;
    private final Encoding encoding;

    public WhiteboardHandler() {
        this(new MxGraphDocumentMerger(), new Encoding());
    }

    public WhiteboardHandler(final MxGraphDocumentMerger merger, final Encoding encoding) {
        this.merger = merger;
        this.encoding = encoding;
    }

    @SneakyThrows({JsonProcessingException.class, IOException.class})
    @Override
    public String handleRequestInternal(ApiType apiType, String input) {
        switch (apiType) {
            case GetWhiteboard:
                final GetWhiteboardRequest getWhiteboardRequest = getWhiteboardRequestObjectReader.readValue(input);
                final GetWhiteboardResponse getWhiteboardResponse = handleGetWhiteboard(getWhiteboardRequest);
                return getWhiteboardResponseWriter.writeValueAsString(getWhiteboardResponse);

            case SetWhiteboard:
                final SetWhiteboardRequest setWhiteboardRequest = setWhiteboardRequestObjectReader.readValue(input);
                final SetWhiteboardResponse setWhiteboardResponse = handleSetWhiteboard(setWhiteboardRequest);
                return setWhiteboardResponseWriter.writeValueAsString(setWhiteboardResponse);
        }
        return null;
    }

    private GetWhiteboardResponse handleGetWhiteboard(final GetWhiteboardRequest request) {
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

        return new GetWhiteboardResponse(
                whiteboard.getIdentifier(),
                whiteboard.getContent(),
                whiteboard.getVersion());
    }

    private SetWhiteboardResponse handleSetWhiteboard(final SetWhiteboardRequest request) {
        Whiteboard whiteboard = dynamoDbTable.load(request.getIdentifier());
        Preconditions.checkState(whiteboard != null);

        final String mergedContent;
        if (StringUtils.isBlank(whiteboard.getContent())) {
            mergedContent = request.getContent();
        } else {
            final String decodedOldContent = encoding.decode(whiteboard.getContent());
            final String decodedNewContent = encoding.decode(request.getContent());
            final String decodedMergedContent = merger.merge(decodedOldContent, decodedNewContent);
            mergedContent = encoding.encode(decodedMergedContent);
        }

        whiteboard.setContent(mergedContent);
        dynamoDbTable.save(whiteboard);
        log.info("whiteboard version {}", whiteboard.getVersion());

        return new SetWhiteboardResponse(
                whiteboard.getIdentifier(),
                whiteboard.getContent(),
                whiteboard.getVersion());
    }
}
