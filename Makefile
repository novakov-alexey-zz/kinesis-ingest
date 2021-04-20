include $(my-aws.env)

STREAM_NAME=sensor-temperature

aws-login:
	aws configure
	amm ../aws-mfa-login/aws-mfa-login.sc \
		--arnMfaDevice "$(ARN_MFA_DEVICE)" \
		--mfaToken $(token)

run-producer:
	amm producer.sc --streamName $(STREAM_NAME) --shardsCount 2 --delay $(delay)

create-app:
	amm streams-util.sc createApplication \
		--name "sensors" \
		--inputArn "arn:aws:kinesis:eu-central-1:$(AWS_ACCOUNT):stream/$($(STREAM_NAME))" \
		--roleArn "arn:aws:iam::$(AWS_ACCOUNT):role/service-role/kinesis-analytics-sensors-eu-central-1" \
		--sqlFilePath "./avgTemperature.sql"

delete-app:
	amm streams-util.sc deleteApplication --name "sensors"