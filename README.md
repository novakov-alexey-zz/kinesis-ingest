# Kinesis Data Ingestion Example

## Pre-requisites

-   Java 8 or higher
-   Scala [Ammonite](https://ammonite.io/#Ammonite-REPL)
-   GNU Make (optional)

## Setup

### Create IAM roles for Firehose & Analytics

Please create IAM roles manually and put into Makefile variables. Just replace harcoded values.

### Create Data Stream

```bash
make create-stream
```

### Create S3 Bucket for Firehose

```bash
make create-bucket
```

### Create Firehose

```bash
make create-firehose
```

### Start Data Producer

On data producer on your local or any machine that can access your created Data Stream 

```bash
make run-producer
```

### Create and start Analytics Application

```bash
make create-app
make start-app
```

## Cleanup

### Delete Data Stream, Firehose and Analytics Application

```bash
make delete
```

### Delete S3 Bucket

```bash
make delete-bucket
```
