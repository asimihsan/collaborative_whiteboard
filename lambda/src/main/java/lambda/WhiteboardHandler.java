package lambda;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.base.Preconditions;
import dynamodb.Whiteboard;
import dynamodb.WhiteboardDao;
import logic.MxGraphDocumentMerger;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Objects;

import static com.google.common.base.Preconditions.checkNotNull;

public class WhiteboardHandler extends BaseHandler {
    private static final Logger log = LogManager.getLogger(WhiteboardHandler.class);
    private static final DynamoDBTableMapper<Whiteboard, String, Long> dynamoDbTable =
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

    private final WhiteboardDao whiteboardDao;
    private final MxGraphDocumentMerger merger;
    private final Encoding encoding;

    public WhiteboardHandler() {
        this(new WhiteboardDao(dynamoDbTable), new MxGraphDocumentMerger(), new Encoding());
    }

    public WhiteboardHandler(final WhiteboardDao whiteboardDao,
                             final MxGraphDocumentMerger merger,
                             final Encoding encoding) {
        this.whiteboardDao = checkNotNull(whiteboardDao);
        this.merger = checkNotNull(merger);
        this.encoding = checkNotNull(encoding);
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
        log.info("handleGetWhiteboard request: {}", request);
        final Whiteboard newestWhiteboard = whiteboardDao.getNewestWhiteboard(request.getIdentifier());
        final Whiteboard whiteboardResult;
        if (newestWhiteboard == null) {
            log.info("Whiteboard does not exist");
            final Whiteboard newWhiteboard = new Whiteboard();
            newWhiteboard.setIdentifier(request.getIdentifier());
            newWhiteboard.setVersion(1L);
            newWhiteboard.setContent("");
            whiteboardDao.saveCompletelyNewWhiteboard(newWhiteboard);
            whiteboardResult = newWhiteboard;
        } else {
            whiteboardResult = newestWhiteboard;
        }

        log.info("whiteboard identifier {} version {}",
                whiteboardResult.getIdentifier(), whiteboardResult.getVersion());

        return new GetWhiteboardResponse(
                whiteboardResult.getIdentifier(),
                whiteboardResult.getContent(),
                whiteboardResult.getVersion());
    }

    private SetWhiteboardResponse handleSetWhiteboard(final SetWhiteboardRequest request) {
        log.info("handleSetWhiteboard identifier: {}", request.getIdentifier());

        final Whiteboard newestWhiteboard = whiteboardDao.getNewestWhiteboard(request.getIdentifier());
        Preconditions.checkState(newestWhiteboard != null);
        final Long existingNewestWhiteboardVersion = newestWhiteboard.getVersion();

        final Whiteboard sourceWhiteboard = whiteboardDao.getWhiteboardAtVersion(
                request.getIdentifier(), request.getSourceWhiteboardVersion());
        Preconditions.checkState(sourceWhiteboard != null);
        log.info("handleSetWhiteboard newest whiteboard version {}, source whiteboard version {}",
                newestWhiteboard.getVersion(), sourceWhiteboard.getVersion());

        final String mergedContent;
        if (Objects.equals(sourceWhiteboard.getVersion(), newestWhiteboard.getVersion())) {
            // If the source whiteboard we've used is still the newest whiteboard, we've won and don't need to do any
            // merging. We get to clobber the whiteboard.
            log.info("handleSetWhiteboard source whiteboard is already newest, we win and can clobber");
            mergedContent = request.getContent();
        } else {
            // The source whiteboard we used is no longer the newest version. Someone else made edits while this request
            // was in progress. If we clobber we will upset them, despite our write being newer. Let's try merging and
            // conflict resolution!
            log.info("handleSetWhiteboard source whiteboard is no longer newest whiteboard, requires merging");
            final String decodedCommonAncestor = encoding.decode(sourceWhiteboard.getContent());
            final String decodedOldContent = encoding.decode(newestWhiteboard.getContent());
            final String decodedNewContent = encoding.decode(request.getContent());
            final String decodedMergedContent = merger.merge(
                    decodedCommonAncestor, decodedOldContent, decodedNewContent);
            mergedContent = encoding.encode(decodedMergedContent);
        }

        newestWhiteboard.setContent(mergedContent);
        newestWhiteboard.setVersion(newestWhiteboard.getVersion() + 1);
        whiteboardDao.saveNewWhiteboardVersion(newestWhiteboard);
        log.info("whiteboard version {}", newestWhiteboard.getVersion());

        return new SetWhiteboardResponse(
                newestWhiteboard.getIdentifier(),
                newestWhiteboard.getContent(),
                request.getSourceWhiteboardVersion(), /*requestSourceWhiteboardVersion*/
                existingNewestWhiteboardVersion, /*existingNewestWhiteboardVersion*/
                newestWhiteboard.getVersion() /*currentNewestWhiteboardVersion*/
        );
    }
}
