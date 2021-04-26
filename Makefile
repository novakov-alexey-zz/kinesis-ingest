include my-aws.env
export

# common
region=eu-central-1

# local producer settings
delay=2000

# Streams
STREAM_NAME=sensor-temperature
SHARDS_COUNT=2

# Analytics
APP_NAME=sensors
# value must match with stream name in SQL statement(s)
DESTINATION_STREAM_NAME="DESTINATION_SQL_STREAM" 

# Firehose
DESTINATION_BUCKET="arn:aws:s3:::raw-data-sensors"


# Local commands

# AWS MFA login script is there: https//github.com/novakov-alexey/aws-mfa-login.git
#
# example usage: make aws-login token="your value here"
aws-login:	
	aws configure
	amm ../aws-mfa-login/aws-mfa-login.sc \
		--arnMfaDevice "$(ARN_MFA_DEVICE)" \
		--mfaToken $(token)

run-producer:
	amm producer.sc --streamName $(STREAM_NAME) --shardsCount $(SHARDS_COUNT) --delay $(delay)

################################

# Streams
create-stream:
	amm kinesis-api.sc createStream --name $(STREAM_NAME) --shardsCount $(SHARDS_COUNT)

delete-stream:
	amm kinesis-api.sc deleteStream --name $(STREAM_NAME)

create-app:
	amm kinesis-api.sc createApplication \
		--name "$(APP_NAME)" \
		--inputArn "arn:aws:kinesis:$(region):$(AWS_ACCOUNT):stream/$(STREAM_NAME)" \
		--roleArn "arn:aws:iam::$(AWS_ACCOUNT):role/service-role/kinesis-analytics-sensors-eu-central-1" \
		--sqlFilePath "./avgTemperature.sql" \
		--outputFirehoseArn "arn:aws:firehose:$(region):$(AWS_ACCOUNT):deliverystream/avg-temperature" \
		--destStreamName $(DESTINATION_STREAM_NAME)

################################

# Analytics
delete-app:
	amm kinesis-api.sc deleteApplication --name "$(APP_NAME)"

start-app:
	amm kinesis-api.sc startApplication --name "$(APP_NAME)"

stop-app:
	amm kinesis-api.sc stopApplication --name "$(APP_NAME)"
################################

# Firehose
create-firehose:
	amm kinesis-api.sc createFirehose \
		--name "avg-temperature" \
		--destBucketArn $(DESTINATION_BUCKET) \
		--iamRole "arn:aws:iam::$(AWS_ACCOUNT):role/service-role/KinesisFirehoseServiceRole-avg-temper-eu-central-1-1618996440107"

delete-firehose:
	amm kinesis-api.sc deleteFirehose \
		--name "avg-temperature"

# S3
create-bucket:
	amm s3-api.sc createBucket --name "raw-data-sensors"
delete-bucket:
	amm s3-api.sc deleteBucket --name "raw-data-sensors"

# Common

delete: delete-app delete-stream delete-firehose

create: create-stream create-firehose