package lambda;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import lambda.dynamodb.Whiteboard;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class GetWhiteboard implements RequestStreamHandler {
    private static final Logger log = LogManager.getLogger(GetWhiteboard.class);
    private static final DynamoDBTableMapper<Whiteboard, String, ?> dynamoDbTable =
            Clients.createWhiteboardDynamoDbMapper();
    private static final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void handleRequest(InputStream input, OutputStream output, Context context) throws IOException {
        final GetWhiteboardRequest request;
        try (final InputStreamReader isr = new InputStreamReader(input);
             final BufferedReader br = new BufferedReader(isr)) {

            final Map<String, String> inputRoot = mapper.readValue(br, new TypeReference<>() {});
            Preconditions.checkArgument(inputRoot.containsKey("body"));
            final String body = inputRoot.get("body");

            request = mapper.readValue(body, GetWhiteboardRequest.class);
        }

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


        final GetWhiteboardResponse response = new GetWhiteboardResponse(whiteboard.getIdentifier(),
                whiteboard.getContent());
        final Map<String, Object> responseJson = ImmutableMap.of(
            "statusCode", 200,
            "body", mapper.writeValueAsString(response)
        );

        try (final OutputStreamWriter osw = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            osw.write(mapper.writeValueAsString(responseJson));
        }
    }
}
