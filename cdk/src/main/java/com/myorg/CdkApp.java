package com.myorg;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.io.Resources;
import lombok.SneakyThrows;
import software.amazon.awscdk.core.App;
import software.amazon.awscdk.core.Environment;
import software.amazon.awscdk.core.StackProps;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class CdkApp {
    @SneakyThrows(IOException.class)
    public static void main(final String[] args) {
        App app = new App();

        final Environment preprod = Environment.builder()
                .account("519160639284")
                .region("us-west-2")
                .build();

        final Environment preprodLambdaEdge = Environment.builder()
                .account("519160639284")
                .region("us-east-1")
                .build();

        final Environment prod = Environment.builder()
                .account("519160639284")
                .region("us-west-2")
                .build();

        // --------------------------------------------------------------------
        //  Lambda@Edge functions must be in us-east-1, so make pre-prod / prod ones here.
        // --------------------------------------------------------------------
        final String rewriteLambdaStackName = "preprod-WhiteboardIhsanIoRewriteLambdaCdkStack";
        final String rewriteLambdaOutputName = "RewriteLambdaName";
        final String rewriteLambdaCode = Resources.toString(
                Resources.getResource("cfn_stack_lookup.js"), Charsets.UTF_8);
        final String rewriteLambdaCodeHash = Hashing.sha256()
                .hashString(rewriteLambdaCode, StandardCharsets.UTF_8).toString();
        final RewriteLambdaEdgeStack preprodRewriteLambdaEdgeStack = new RewriteLambdaEdgeStack(
                app,
                rewriteLambdaStackName,
                rewriteLambdaOutputName,
                StackProps.builder()
                        .env(preprodLambdaEdge)
                        .description("Whiteboard pre-prod Lambda@Edge stack")
                        .build());
        // --------------------------------------------------------------------

        final String preprodLambdaVersion = "000009";
        new CdkStack(app, "preprod-WhiteboardIhsanIoCdkStack",
                "whiteboard-preprod.ihsan.io",
                rewriteLambdaStackName,
                rewriteLambdaOutputName,
                rewriteLambdaCodeHash,
                preprodLambdaVersion,
                StackProps.builder()
                        .env(preprod)
                        .description("Whiteboard pre-prod environment")
                        .build()).addDependency(preprodRewriteLambdaEdgeStack);

//        new CdkStack(app, "prod-WhiteboardAsimIhsanIoCdkStack",
//                "whiteboard.ihsan.io",
//                StackProps.builder()
//                        .env(prod)
//                        .description("Whiteboard prod environment")
//                        .build());

        app.synth();
    }
}
