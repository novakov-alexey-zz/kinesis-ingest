include my-aws.env
export

STREAM_NAME=sensor-temperature
SHARDS_COUNT=2
APP_NAME=sensors
delay=2000

aws-login:	
	aws configure
	amm ../aws-mfa-login/aws-mfa-login.sc \
		--arnMfaDevice "$(ARN_MFA_DEVICE)" \
		--mfaToken $(token)

run-producer:
	amm producer.sc --streamName $(STREAM_NAME) --shardsCount $(SHARDS_COUNT) --delay $(delay)

create-stream:
	amm streams-util.sc createStream --name $(STREAM_NAME) --shardsCount $(SHARDS_COUNT)

delete-stream:
	amm streams-util.sc deleteStream --name $(STREAM_NAME)

create-app:
	amm streams-util.sc createApplication \
		--name "$(APP_NAME)" \
		--inputArn "arn:aws:kinesis:eu-central-1:$(AWS_ACCOUNT):stream/$(STREAM_NAME)" \
		--roleArn "arn:aws:iam::$(AWS_ACCOUNT):role/service-role/kinesis-analytics-sensors-eu-central-1" \
		--sqlFilePath "./avgTemperature.sql"

delete-app:
	amm streams-util.sc deleteApplication --name "$(APP_NAME)"

start-app:
	amm streams-util.sc startApplication --name "$(APP_NAME)"

stop-app:
	amm streams-util.sc stopApplication --name "$(APP_NAME)"