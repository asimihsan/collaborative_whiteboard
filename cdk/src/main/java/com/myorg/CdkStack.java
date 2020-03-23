package com.myorg;


import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import lombok.SneakyThrows;
import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigateway.Cors;
import software.amazon.awscdk.services.apigateway.CorsOptions;
import software.amazon.awscdk.services.apigateway.EndpointConfiguration;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.LambdaIntegrationOptions;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.certificatemanager.DnsValidatedCertificate;
import software.amazon.awscdk.services.certificatemanager.ICertificate;
import software.amazon.awscdk.services.cloudformation.CustomResource;
import software.amazon.awscdk.services.cloudformation.CustomResourceProvider;
import software.amazon.awscdk.services.cloudfront.Behavior;
import software.amazon.awscdk.services.cloudfront.CloudFrontAllowedMethods;
import software.amazon.awscdk.services.cloudfront.CloudFrontWebDistribution;
import software.amazon.awscdk.services.cloudfront.CloudFrontWebDistributionProps;
import software.amazon.awscdk.services.cloudfront.CustomOriginConfig;
import software.amazon.awscdk.services.cloudfront.LambdaEdgeEventType;
import software.amazon.awscdk.services.cloudfront.LambdaFunctionAssociation;
import software.amazon.awscdk.services.cloudfront.PriceClass;
import software.amazon.awscdk.services.cloudfront.S3OriginConfig;
import software.amazon.awscdk.services.cloudfront.SSLMethod;
import software.amazon.awscdk.services.cloudfront.SecurityPolicyProtocol;
import software.amazon.awscdk.services.cloudfront.SourceConfiguration;
import software.amazon.awscdk.services.cloudfront.ViewerCertificate;
import software.amazon.awscdk.services.cloudfront.ViewerCertificateOptions;
import software.amazon.awscdk.services.cloudfront.ViewerProtocolPolicy;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.lambda.Alias;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.IAlias;
import software.amazon.awscdk.services.lambda.IVersion;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.SingletonFunction;
import software.amazon.awscdk.services.lambda.Version;
import software.amazon.awscdk.services.route53.ARecord;
import software.amazon.awscdk.services.route53.HostedZone;
import software.amazon.awscdk.services.route53.HostedZoneProviderProps;
import software.amazon.awscdk.services.route53.IHostedZone;
import software.amazon.awscdk.services.route53.RecordTarget;
import software.amazon.awscdk.services.route53.targets.CloudFrontTarget;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.BucketProps;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.ISource;
import software.amazon.awscdk.services.s3.deployment.Source;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CdkStack extends Stack {
    @SneakyThrows(IOException.class)
    public CdkStack(final Construct scope,
                    final String id,
                    final String domainName,
                    final String rewriteLambdaStackName,
                    final String rewriteLambdaOutputName,
                    final String rewriteLambdaCodeHash,
                    final String lambdaVersion,
                    final StackProps props) {
        super(scope, id, props);

        // --------------------------------------------------------------------
        //  S3 bucket for static whiteboard assets.
        // --------------------------------------------------------------------
        final Bucket bucket = new Bucket(this, "WhiteboardStaticAssetsBucket", BucketProps.builder()
                .publicReadAccess(true)
//                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .removalPolicy(RemovalPolicy.DESTROY)
                .versioned(false)
                .encryption(BucketEncryption.S3_MANAGED)
                .build());
        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  Lambda function and other resources for CRUD of whiteboards.
        //
        //  Note that you must change the identifier of the Lambda versions to re-deploy Lambda code. Lambda
        //  versions are immutable, so even if you update the underlying Lambda the version stays the same.
        //
        //  TODO use CodeDeploy to deploy Lambdas.
        // --------------------------------------------------------------------
        final Table whiteboardTable = Table.Builder.create(this, "WhiteboardTable")
                .partitionKey(Attribute.builder()
                        .name("identifier")
                        .type(AttributeType.STRING)
                        .build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .build();
        final Map<String, String> lambdaEnvironment = new HashMap<>();
        lambdaEnvironment.put("WHITEBOARD_TABLE_NAME", whiteboardTable.getTableName());

        final Function whiteboardLambda = Function.Builder.create(this, "WhiteboardLambda")
                .runtime(Runtime.JAVA_11)    // execution environment
                .code(Code.fromAsset("../lambda/build/distributions/collaborative_whiteboard.zip"))  // code loaded from the "lambda" directory
                .handler("lambda.WhiteboardHandler::handleRequest")
                .memorySize(1024)
                .timeout(Duration.seconds(10))
                .environment(lambdaEnvironment)
                .build();
        final IVersion whiteboardLambdaVersion = Version.Builder.create(this, String.format("WhiteboardLambdaVersion_%s_", lambdaVersion))
                .lambda(whiteboardLambda)
                .provisionedConcurrentExecutions(0)
                .build();
        final IAlias whiteboardLambdaLatest = Alias.Builder.create(this, "WhiteboardLambdaAlias")
                .version(whiteboardLambdaVersion)
                .aliasName("LATEST")
                .build();
        whiteboardTable.grantReadWriteData(whiteboardLambda);
        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  Custom resource to get the Lambda@Edge name from the us-east-1 stack. CDK does not support this because
        //  it's in a different region, and Lambda@Edge only supports functions created in us-east-1.
        //
        //  See: https://github.com/aws/aws-cdk/issues/1575#issuecomment-480738659
        // --------------------------------------------------------------------
        final String stackLookupLambdaCode = Resources.toString(
                Resources.getResource("cfn_stack_lookup.js"), Charsets.UTF_8);
        final SingletonFunction stackLookupLambda = SingletonFunction.Builder.create(this, "StackLookupLambda")
                .uuid("f7d4f730-4ee1-11e8-9c2d-fa7ae01bbebc")
                .handler("index.handler")
                .runtime(Runtime.NODEJS_12_X)
                .code(Code.fromInline(stackLookupLambdaCode))
                .timeout(Duration.seconds(60))
                .build();

        final CustomResourceProvider stackLookupProvider = CustomResourceProvider.fromLambda(stackLookupLambda);
        stackLookupLambda.addToRolePolicy(PolicyStatement.Builder.create()
                .effect(Effect.ALLOW)
                .actions(Collections.singletonList("cloudformation:DescribeStacks"))
                .resources(Collections.singletonList(
                        String.format("arn:aws:cloudformation:*:*:stack/%s/*", rewriteLambdaStackName)))
                .build());
        final CustomResource stackLookup = CustomResource.Builder.create(this, "CfnStackLookupOutput")
                .provider(stackLookupProvider)
                .properties(ImmutableMap.of(
                        "StackName", rewriteLambdaStackName,
                        "OutputKey", rewriteLambdaOutputName,

                        // Need a key that changes when the rewrite Lambda code changes, or else we never re-deploy it.
                        "LambdaHash", rewriteLambdaCodeHash

                ))
                .build();
        final String rewriteLambdaArn = stackLookup.getAttString("Output");
        final IVersion rewriteLambdaVersion = Version.fromVersionArn(this, "RewriteLambda", rewriteLambdaArn);
        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  API gateway.
        // --------------------------------------------------------------------
        final RestApi api = RestApi.Builder.create(this, "WhiteboardApi")
                .endpointConfiguration(EndpointConfiguration.builder()
                        .types(Collections.singletonList(EndpointType.REGIONAL))
                        .build())
                .defaultCorsPreflightOptions(CorsOptions.builder()
                        .allowOrigins(Cors.ALL_ORIGINS)
                        .allowCredentials(true)
                        .allowHeaders(Cors.DEFAULT_HEADERS)
                        .allowMethods(ImmutableList.of("POST", "OPTIONS"))
                        .maxAge(Duration.seconds(86400))
                        .build())
                .build();
        final Resource rootResource = api.getRoot().addResource("api");

        final Resource getResource = rootResource.addResource("get");
        final LambdaIntegration getWhiteboardIntegration = new LambdaIntegration(whiteboardLambdaLatest,
                LambdaIntegrationOptions.builder().proxy(true).build());
        getResource.addMethod("POST", getWhiteboardIntegration);

        final Resource setResource = rootResource.addResource("set");
        final LambdaIntegration setWhiteboardIntegration = new LambdaIntegration(whiteboardLambdaLatest,
                LambdaIntegrationOptions.builder().proxy(true).build());
        setResource.addMethod("POST", setWhiteboardIntegration);
        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  Create the certificate that CloudFront will use.
        // --------------------------------------------------------------------
        final IHostedZone hostedZone = HostedZone.fromLookup(this, "HostedZone",
                HostedZoneProviderProps.builder()
                        .domainName("ihsan.io")
                        .privateZone(false)
                        .build());
        final ICertificate certificate = DnsValidatedCertificate.Builder.create(
                this,
                "WhiteboardCertificate"
        )
                .domainName(domainName)

                // CloudFront requires ACM certificates be in us-east-1
                .region("us-east-1")

                .hostedZone(hostedZone)
                .build();

        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  CloudFront distribution for static assets and backend.
        // --------------------------------------------------------------------
        final String apiGatewayDomainName = String.format("%s.execute-api.%s.amazonaws.com",
                api.getRestApiId(),
                props.getEnv().getRegion());
        final List<SourceConfiguration> sourceConfigurations = Arrays.asList(
                SourceConfiguration.builder()
                        .customOriginSource(CustomOriginConfig.builder()
                                .domainName(apiGatewayDomainName)
                                .originKeepaliveTimeout(Duration.seconds(60))
                                .build())
                        .originPath("/prod")
                        .behaviors(Collections.singletonList(Behavior.builder()
                                .pathPattern("/api/*")
                                .allowedMethods(CloudFrontAllowedMethods.ALL)
                                .build()))
                        .build(),

                SourceConfiguration.builder()
                        .s3OriginSource(S3OriginConfig.builder()
                                .s3BucketSource(bucket)
                                .build())
                        .behaviors(ImmutableList.of(
                                // When a user browses to e.g. /w/12345, we want to just load the same static assets as
                                // from root but keep the path URI for the JavaScript to parse out the whiteboard identifier.
                                Behavior.builder()
                                        .pathPattern("/w/*")
                                        .compress(true)
                                        .lambdaFunctionAssociations(Collections.singletonList(LambdaFunctionAssociation.builder()
                                                .eventType(LambdaEdgeEventType.VIEWER_REQUEST)
                                                .lambdaFunction(rewriteLambdaVersion)
                                                .build()))
                                        .allowedMethods(CloudFrontAllowedMethods.GET_HEAD_OPTIONS)
                                        .build(),

                                // When a user browses to the root path /, redirect them to /w/ and a random new whiteboard
                                // identifier.
                                Behavior.builder()
                                        .pathPattern("/")
                                        .compress(true)
                                        .lambdaFunctionAssociations(Collections.singletonList(LambdaFunctionAssociation.builder()
                                                .eventType(LambdaEdgeEventType.VIEWER_REQUEST)
                                                .lambdaFunction(rewriteLambdaVersion)
                                                .build()))
                                        .allowedMethods(CloudFrontAllowedMethods.GET_HEAD_OPTIONS)
                                        .build(),

                                // Static files
                                Behavior.builder()
                                        .isDefaultBehavior(true)
                                        .compress(true)
                                        .allowedMethods(CloudFrontAllowedMethods.GET_HEAD_OPTIONS)
                                        .build()))
                        .build()

        );
        final CloudFrontWebDistribution distribution = new CloudFrontWebDistribution(this, "CloudFront",
                CloudFrontWebDistributionProps.builder()
                        .originConfigs(sourceConfigurations)
                        .viewerCertificate(ViewerCertificate.fromAcmCertificate(certificate,
                                ViewerCertificateOptions.builder()
                                        .aliases(Collections.singletonList(domainName))
                                        .securityPolicy(SecurityPolicyProtocol.TLS_V1_1_2016)
                                        .sslMethod(SSLMethod.SNI)
                                        .build()))
                        .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                        .priceClass(PriceClass.PRICE_CLASS_ALL)
                        .build());
        // --------------------------------------------------------------------

        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  The CloudFront distribution is using the domain as a CNAME, but you
        //  need the DNS A record from the domain name to CloudFront.
        // --------------------------------------------------------------------
        ARecord.Builder.create(this, "WhiteboardDnsAlias")
                .zone(hostedZone)
                .recordName(domainName + ".")
                .target(RecordTarget.fromAlias(new CloudFrontTarget(distribution)))
                .build();
        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  Deploy web static assets to the S3 bucket, and invalidate the
        //  CloudFront distribution
        // --------------------------------------------------------------------
        final List<ISource> bucketDeploymentSources = Collections.singletonList(
                Source.asset("./../web")
        );
        BucketDeployment.Builder.create(this, "DeployWhiteboardWebsite")
                .sources(bucketDeploymentSources)
                .destinationBucket(bucket)
                .distribution(distribution)
                .memoryLimit(1024)
                .build();
        // --------------------------------------------------------------------

        CfnOutput.Builder.create(this, "ApiGatewayDomainNameExport")
                .exportName("ApiGatewayDomainNameExport")
                .value(apiGatewayDomainName)
                .build();

        CfnOutput.Builder.create(this, "CloudfrontDomainNameExport")
                .exportName("CloudfrontDomainNameExport")
                .value(distribution.getDomainName())
                .build();

        CfnOutput.Builder.create(this, "RewriteLambdaArn")
                .exportName("RewriteLambdaArn")
                .value(rewriteLambdaArn)
                .build();
    }
}