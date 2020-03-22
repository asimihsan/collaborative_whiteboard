package lambda;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTableMapper;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.google.common.collect.ImmutableMap;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import exception.UnrecognizedApiResourceException;
import lambda.dynamodb.Whiteboard;
import lombok.SneakyThrows;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public abstract class BaseHandler implements RequestStreamHandler {
    private static final Logger log = LogManager.getLogger(BaseHandler.class);

    private static final int OK_STATUS_CODE = 200;
    private static final int SERVER_ERROR_STATUS_CODE = 500;
    private static final String STATUS_CODE = "statusCode";
    private static final String BODY = "body";
    private static final String HEADERS = "headers";

    private final ObjectReader stringObjectMapObjectReader;
    private final ObjectWriter stringObjectMapObjectWriter;

    BaseHandler() {
        final TypeReference<Map<String, Object>> stringObjectMapTypeReference =
                new TypeReference<>() {};
        final ObjectMapper objectMapper = new ObjectMapper();
        this.stringObjectMapObjectReader = objectMapper.readerFor(stringObjectMapTypeReference);
        this.stringObjectMapObjectWriter = objectMapper.writerFor(stringObjectMapTypeReference);
    }

    /**
     * Handle the request.
     */
    @SneakyThrows(IOException.class)
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED",
            justification = "Spotbugs doesn't support @CanIgnoreReturnValue")
    @SuppressWarnings("unchecked")
    @Override
    public void handleRequest(final InputStream input,
                              final OutputStream output,
                              final Context context) {
        final Map<String, Object> inputMap;
        try (final BufferedInputStream bis = new BufferedInputStream(input)) {
            inputMap = stringObjectMapObjectReader.readValue(bis);
        }
        log.info("entry. input: {}", Objects.toString(inputMap));
        final ImmutableMap.Builder<String, String> outputHeaders = ImmutableMap.<String, String>builder()
                .put("Content-Type", "application/json")
                .put("Access-Control-Allow-Origin", "*")
                .put("Access-Control-Allow-Methods", "POST, OPTIONS")
                .put("Access-Control-Allow-Credentials", "true")
                .put("Access-Control-Allow-Headers", "Content-Type,X-Amz-Date,Authorization,X-Api-Key,X-Amz-Security-Token,X-Amz-User-Agent")
                .put("Access-Control-Max-Age", "86400");
        final ImmutableMap.Builder<String, Object> responseBuilder = ImmutableMap.<String, Object>builder()
                .put("isBase64Encoded", false);
        final Map<String, String> inputHeaders = getHeaders(inputMap);
        log.info("inputHeaders: {}", Objects.toString(inputHeaders));

        final String resource = (String) inputMap.get("resource");
        final ApiType apiType;
        switch (resource) {
            case "/api/get":
                apiType = ApiType.GetWhiteboard;
                break;
            case "/api/set":
                apiType = ApiType.SetWhiteboard;
                break;
            default:
                final String message = String.format("Unrecognized API resource: %s", resource);
                throw new UnrecognizedApiResourceException(message);
        }

        String outputBody = "";
        final String inputBody = (String) inputMap.get(BODY);
        try {
            outputBody = handleRequestInternal(apiType, inputBody);
            responseBuilder.put(STATUS_CODE, OK_STATUS_CODE);
        } catch (final Exception e) {
            log.error("Uncaught exception: ", e);
            log.error("Exception message: {}", e.getMessage());
            responseBuilder.put(STATUS_CODE, SERVER_ERROR_STATUS_CODE);
        }
        finishHandleRequest(outputHeaders, outputBody, responseBuilder, output);
    }

    @SneakyThrows({JsonProcessingException.class, IOException.class})
    @SuppressFBWarnings(value = "RV_RETURN_VALUE_IGNORED",
            justification = "Spotbugs doesn't support @CanIgnoreReturnValue")
    private void finishHandleRequest(
            final ImmutableMap.Builder<String, String> outputHeaders,
            final String body,
            final ImmutableMap.Builder<String, Object> responseBuilder,
            final OutputStream output) {
        responseBuilder.put(HEADERS, outputHeaders.build());
        responseBuilder.put(BODY, body);
        final Map<String, Object> responseMap = responseBuilder.build();
        log.info("Returning response: {}", Objects.toString(responseMap));
        try (final OutputStreamWriter osw = new OutputStreamWriter(output, StandardCharsets.UTF_8);
             final BufferedWriter bw = new BufferedWriter(osw)) {
            bw.write(stringObjectMapObjectWriter.writeValueAsString(responseMap));
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> getHeaders(final Map<String, Object> inputMap) {
        return (Map<String, String>) inputMap.get("headers");
    }

    public abstract String handleRequestInternal(final ApiType apiType, final String input);
}