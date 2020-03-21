package com.myorg;


import software.amazon.awscdk.core.CfnOutput;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Duration;
import software.amazon.awscdk.core.RemovalPolicy;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.apigateway.EndpointConfiguration;
import software.amazon.awscdk.services.apigateway.EndpointType;
import software.amazon.awscdk.services.apigateway.LambdaIntegration;
import software.amazon.awscdk.services.apigateway.LambdaIntegrationOptions;
import software.amazon.awscdk.services.apigateway.Resource;
import software.amazon.awscdk.services.apigateway.RestApi;
import software.amazon.awscdk.services.certificatemanager.DnsValidatedCertificate;
import software.amazon.awscdk.services.certificatemanager.ICertificate;
import software.amazon.awscdk.services.cloudfront.Behavior;
import software.amazon.awscdk.services.cloudfront.CloudFrontAllowedMethods;
import software.amazon.awscdk.services.cloudfront.CloudFrontWebDistribution;
import software.amazon.awscdk.services.cloudfront.CloudFrontWebDistributionProps;
import software.amazon.awscdk.services.cloudfront.CustomOriginConfig;
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
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CdkStack extends Stack {
    public CdkStack(final Construct scope, final String id) {
        this(scope, id, null, null);
    }

    public CdkStack(final Construct scope,
                    final String id,
                    final String domainName,
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

        final Function getWhiteboardLambda = Function.Builder.create(this, "GetWhiteboardLambda")
                .runtime(Runtime.JAVA_11)    // execution environment
                .code(Code.fromAsset("../lambda/build/distributions/collaborative_whiteboard.zip"))  // code loaded from the "lambda" directory
                .handler("lambda.GetWhiteboard::handleRequest")
                .memorySize(512)
                .timeout(Duration.seconds(10))
                .environment(lambdaEnvironment)
                .build();
        whiteboardTable.grantReadWriteData(getWhiteboardLambda);

        final Function setWhiteboardLambda = Function.Builder.create(this, "SetWhiteboardLambda")
                .runtime(Runtime.JAVA_11)    // execution environment
                .code(Code.fromAsset("../lambda/build/distributions/collaborative_whiteboard.zip"))  // code loaded from the "lambda" directory
                .handler("lambda.SetWhiteboard::handleRequest")
                .memorySize(512)
                .timeout(Duration.seconds(10))
                .environment(lambdaEnvironment)
                .build();
        whiteboardTable.grantReadWriteData(setWhiteboardLambda);
        // --------------------------------------------------------------------

        // --------------------------------------------------------------------
        //  API gateway.
        // --------------------------------------------------------------------
        final RestApi api = RestApi.Builder.create(this, "WhiteboardApi")
                .endpointConfiguration(EndpointConfiguration.builder()
                        .types(Collections.singletonList(EndpointType.REGIONAL))
                        .build())
                .build();
        final Resource rootResource = api.getRoot().addResource("api");
        final Resource getResource = rootResource.addResource("get");
        final LambdaIntegration getWhiteboardIntegration = new LambdaIntegration(getWhiteboardLambda,
                LambdaIntegrationOptions.builder().proxy(true).build());
        getResource.addMethod("POST", getWhiteboardIntegration);

        final Resource setResource = rootResource.addResource("set");
        final LambdaIntegration setWhiteboardIntegration = new LambdaIntegration(setWhiteboardLambda,
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
        final List<SourceConfiguration> sourceConfigurations = Arrays.asList(
                SourceConfiguration.builder()
                        .customOriginSource(CustomOriginConfig.builder()

                                // TODO FIXME
                                .domainName("f9di5lbvmd.execute-api.us-west-2.amazonaws.com")

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
                        .behaviors(Collections.singletonList(Behavior.builder()
                                .isDefaultBehavior(true)
                                .compress(true)
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
                .build();
        // --------------------------------------------------------------------

        CfnOutput.Builder.create(this, "ApiGatewayDomainNameExport")
                .exportName("ApiGatewayDomainNameExport")
                .value(api.getUrl())
                .build();

        CfnOutput.Builder.create(this, "CloudfrontDomainNameExport")
                .exportName("CloudfrontDomainNameExport")
                .value(distribution.getDomainName())
                .build();
    }
}