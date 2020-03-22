'use strict';

// version 8

function guid() {
    function s4() {
        return Math.floor((1 + Math.random()) * 0x10000)
            .toString(16)
            .substring(1);
    }
    return s4() + s4() + '-' + s4() + '-' + s4() + '-' +
        s4() + '-' + s4() + s4() + s4();
}

// See: https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/lambda-examples.html

exports.handler = (event, context, callback) => {
    console.log("REQUEST", JSON.stringify(event));
    const request = event.Records[0].cf.request;
    const headers = request.headers;

    // If this is the root, redirect to a random new whiteboard ID.
    if (request.uri === "/") {
        const newUri = "/w/" + guid();
        console.log("re-writing root request with new whiteboard: " + newUri);
        const response = {
            status: '302',
            statusDescription: 'Found',
            headers: {
                location: [{
                    key: 'Location',
                    value: newUri,
                }],
            },
        };
        callback(null, response);
    }

    // We are only rewriting requests for the /w/ endpoint, so that when a user GETs that we still load
    // index.html at the root of the S3 origin server. Everything else, e.g. static files and /api/
    // endpoint, are left alone.
    if (!request.uri.startsWith("/w/")) {
        console.log("not request for whiteboard, pass-through");
        callback(null, request);
    }

    console.log("re-writing URI to get / from the origin");
    request.uri = "/";
    return callback(null, request);
};
