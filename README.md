## Introduction

`collaborative_whiteboard` is a fork of the GraphEditor example from mxGraph, which forms the core of `draw.io`. This fork alllows very basic collaboration between multiple concurrent editors.

Demo server: https://whiteboard.ihsan.io

## First time CDK setup

Install cdk:

```
npm install -g aws-cdk
```

Bootstrap CDK for specific AWS account and region:

```
cdk bootstrap 'aws://519160639284/us-west-2'
```

## Running

Pre-prod:

```
(cd lambda && ./gradlew build)
(cd cdk && cdk deploy --require-approval never preprod-WhiteboardIhsanIoCdkStack)
```

## Stencils location

https://github.com/jgraph/drawio/tree/master/src/main/webapp/stencils

## License

Licensed under Apache License 2.0

### Dependency licenses

- mxGraph is licensed under Apache License 2.0
- draw.io is licensed under Apache License 2.0

