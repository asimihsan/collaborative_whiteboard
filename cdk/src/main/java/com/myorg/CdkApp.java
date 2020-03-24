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

        final Environment prodLambdaEdge = Environment.builder()
                .account("519160639284")
                .region("us-east-1")
                .build();

        final String rewriteLambdaCode = Resources.toString(
                Resources.getResource("rewrite.js"), Charsets.UTF_8);
        final String rewriteLambdaCodeHash = Hashing.sha256()
                .hashString(rewriteLambdaCode, StandardCharsets.UTF_8).toString();

        // --------------------------------------------------------------------
        //  Pre-prod.
        //
        //  Lambda@Edge functions must be in us-east-1. Hence separate stack.
        // --------------------------------------------------------------------
        final String preprodRewriteLambdaStackName = "preprod-WhiteboardIhsanIoRewriteLambdaCdkStack";
        final String preprodShortStackName = "preprod";
        final String preprodRewriteLambdaVersionNumber = "000011";
        final String preprodRewriteLambdaOutputName = "PreprodRewriteLambdaName";
        final String preprodLambdaVersion = "000012";
        final String preprodStackName = "preprod-WhiteboardIhsanIoCdkStack";
        final String preprodDomainName = "whiteboard-preprod.ihsan.io";
        final Integer preprodLambdaProvisionedConcurrency = 0;

        final RewriteLambdaEdgeStack preprodRewriteLambdaEdgeStack = new RewriteLambdaEdgeStack(
                app,
                preprodRewriteLambdaStackName,
                preprodRewriteLambdaOutputName,
                preprodRewriteLambdaVersionNumber,
                StackProps.builder()
                        .env(preprodLambdaEdge)
                        .description("Whiteboard pre-prod Lambda@Edge stack")
                        .build());
        new CdkStack(app, preprodStackName,
                preprodShortStackName,
                preprodDomainName,
                preprodRewriteLambdaStackName,
                preprodRewriteLambdaOutputName,
                rewriteLambdaCodeHash,
                preprodLambdaVersion,
                preprodLambdaProvisionedConcurrency,
                StackProps.builder()
                        .env(preprod)
                        .description("Whiteboard pre-prod environment")
                        .build()).addDependency(preprodRewriteLambdaEdgeStack);
        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  Prod.
        //
        //  Lambda@Edge functions must be in us-east-1. Hence separate stack.
        // --------------------------------------------------------------------
        final String prodRewriteLambdaStackName = "prod-WhiteboardIhsanIoRewriteLambdaCdkStack";
        final String prodRewriteLambdaVersionNumber = "000003";
        final String prodRewriteLambdaOutputName = "ProdRewriteLambdaName";
        final String prodLambdaVersion = "000002";
        final String prodStackName = "prod-WhiteboardIhsanIoCdkStack";
        final String prodShortStackName = "prod";
        final String prodDomainName = "whiteboard.ihsan.io";
        final Integer prodLambdaProvisionedConcurrency = 2;

        final RewriteLambdaEdgeStack prodRewriteLambdaEdgeStack = new RewriteLambdaEdgeStack(
                app,
                prodRewriteLambdaStackName,
                prodRewriteLambdaOutputName,
                prodRewriteLambdaVersionNumber,
                StackProps.builder()
                        .env(prodLambdaEdge)
                        .description("Whiteboard prod Lambda@Edge stack")
                        .build());
        new CdkStack(app, prodStackName,
                prodShortStackName,
                prodDomainName,
                prodRewriteLambdaStackName,
                prodRewriteLambdaOutputName,
                rewriteLambdaCodeHash,
                prodLambdaVersion,
                prodLambdaProvisionedConcurrency,
                StackProps.builder()
                        .env(prod)
                        .description("Whiteboard prod environment")
                        .build()).addDependency(prodRewriteLambdaEdgeStack);
        // --------------------------------------------------------------------

        app.synth();
    }
}
