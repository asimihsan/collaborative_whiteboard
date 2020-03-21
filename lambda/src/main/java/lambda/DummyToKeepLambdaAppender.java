package lambda;

import com.amazonaws.services.lambda.runtime.log4j2.LambdaAppender;

public class DummyToKeepLambdaAppender {
    // We need LambdaAppender in the JAR file for logging to work, but the Gradle dependency plugin
    // can't find it. So we depend on it here.
    private final static Class<LambdaAppender> dummy = LambdaAppender.class;
}
