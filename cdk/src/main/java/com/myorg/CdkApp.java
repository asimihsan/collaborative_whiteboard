package com.myorg;

import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

public class CdkApp {
    public static void main(final String[] args) {
        App app = new App();

        final Environment preprod = Environment.builder()
                .account("519160639284")
                .region("us-west-2")
                .build();

        new CdkStack(app, "preprod-WhiteboardIhsanIoCdkStack",
                "whiteboard-preprod.ihsan.io",
                StackProps.builder()
                        .env(preprod)
                        .description("Whiteboard pre-prod environment")
                        .build());

        new CdkStack(app, "prod-WhiteboardAsimIhsanIoCdkStack",
                "whiteboard.ihsan.io",
                StackProps.builder()
                        .env(preprod)
                        .description("Whiteboard prod environment")
                        .build());

        app.synth();
    }
}
