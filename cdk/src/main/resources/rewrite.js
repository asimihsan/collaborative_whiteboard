'use strict';

// version 6

// See: https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/lambda-examples.html

exports.handler = (event, context, callback) => {
//  console.log("REQUEST", JSON.stringify(event));
    const request = event.Records[0].cf.request;
    const headers = request.headers;

    // We are only rewriting requests for the /w/ endpoint, so that when a user GETs that we still load
    // index.html at the root of the S3 origin server. Everything else, e.g. static files and /api/
    // endpoint, are left alone.
    if (!request.uri.startsWith("/w/")) {
        callback(null, request);
    }

    request.uri = "/";
    return callback(null, request);
};
