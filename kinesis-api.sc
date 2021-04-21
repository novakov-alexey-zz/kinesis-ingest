import $ivy.`jp.co.bizreach::aws-kinesis-scala:0.0.12`

import jp.co.bizreach.kinesis.CreateStreamRequest
import jp.co.bizreach.kinesis._

import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder

import com.amazonaws.services.kinesisanalytics.AmazonKinesisAnalytics
import com.amazonaws.services.kinesisanalytics.AmazonKinesisAnalyticsClientBuilder
import com.amazonaws.services.kinesisanalytics.model._

import com.amazonaws.services.kinesisfirehose.AmazonKinesisFirehoseClientBuilder
import com.amazonaws.services.kinesisfirehose.model._

import scala.util.Using
import scala.jdk.CollectionConverters._
import scala.io.Source

import java.util.Date

val region = Regions.EU_CENTRAL_1
lazy val streams =
  AmazonKinesisClientBuilder.standard().withRegion(region).build()
lazy val apps =
  AmazonKinesisAnalyticsClientBuilder.standard().withRegion(region).build()
lazy val firehoses =
  AmazonKinesisFirehoseClientBuilder.standard().withRegion(region).build()

@main
def createStream(
    name: String @arg(
      doc = "Kinesis Stream name"
    ),
    shardsCount: Byte @arg(
      doc = "Kinesis number of shards to be used partitin key calculation"
    )
) = {
  val createStreamRequest = CreateStreamRequest(name, shardsCount)
  val res = streams.createStream(createStreamRequest)
  println(res)
}

@main
def deleteStream(
    name: String @arg(
      doc = "Kinesis Stream name"
    )
) =
  println(streams.deleteStream(name))

@main
def createApplication(
    name: String @arg(doc = "Kinesis Analytics Application"),
    inputArn: String @arg(doc = "Kinesis Stream ARN"),
    roleArn: String @arg(doc = "Kinesis Analytics App Role"),
    outputFirehoseArn: String @arg(doc = "Kinesis Firehose ARN"),
    destStreamName: String @arg(
      doc = "SQL in-application stream name for destination"
    ),
    sqlFilePath: String @arg(
      doc = "Path to a file with Kinesis Analytics SQL statements"
    )
) = {
  val discover = new DiscoverInputSchemaRequest()
  discover.setResourceARN(inputArn)
  discover.setRoleARN(roleArn)
  discover.setInputStartingPositionConfiguration(
    new InputStartingPositionConfiguration().withInputStartingPosition(
      InputStartingPosition.TRIM_HORIZON
    )
  )
  val schemaResult = apps.discoverInputSchema(discover)

  val streamInput = new KinesisStreamsInput()
  streamInput.setResourceARN(inputArn)
  streamInput.setRoleARN(roleArn)

  val input = new Input()
  input.setKinesisStreamsInput(streamInput)
  input.setInputSchema(schemaResult.getInputSchema())
  input.setNamePrefix(inputArn.split("/").lastOption.getOrElse("source"))

  val appRequest = new CreateApplicationRequest()
  appRequest.withInputs(input).withApplicationName(name)
  Using.resource(Source.fromFile(sqlFilePath)) { f =>
    appRequest.withApplicationCode(f.getLines().toList.mkString("\n"))
  }

  val output = new Output()
  val firehoseOutput = new KinesisFirehoseOutput()
    .withResourceARN(outputFirehoseArn)
    .withRoleARN(roleArn)

  output.setKinesisFirehoseOutput(firehoseOutput)
  output.setName(destStreamName)

  val destSchema = new DestinationSchema()
  output.setDestinationSchema(
    destSchema.withRecordFormatType(RecordFormatType.JSON)
  )

  appRequest.withOutputs(output)

  val res = apps.createApplication(appRequest)
  print(res)
}

@main def deleteApplication(
    name: String @arg(doc = "Kinesis Analytics Application")
) = {
  val descReq = new DescribeApplicationRequest()
  val resDesc = apps.describeApplication(descReq.withApplicationName(name))
  val req = new DeleteApplicationRequest()
  req.withCreateTimestamp(resDesc.getApplicationDetail().getCreateTimestamp())
  apps.deleteApplication(req.withApplicationName(name))
}

@main def startApplication(
    name: String @arg(doc = "Kinesis Analytics Application")
) = {
  val req = new StartApplicationRequest().withApplicationName(name)
  val inputCfg = new InputConfiguration()
  inputCfg.setInputStartingPositionConfiguration(
    new InputStartingPositionConfiguration().withInputStartingPosition(
      InputStartingPosition.TRIM_HORIZON
    )
  )
  val descReq = new DescribeApplicationRequest()
  val resDesc = apps.describeApplication(descReq.withApplicationName(name))
  val id = resDesc
    .getApplicationDetail()
    .getInputDescriptions()
    .asScala
    .headOption
    .map(_.getInputId())
    .getOrElse(
      sys.error(
        s"There are no inputs to take an id to start an aplication ${name}"
      )
    )
  inputCfg.setId(id)
  req.setInputConfigurations(List(inputCfg).asJava)
  apps.startApplication(req)
}

@main def stopApplication(
    name: String @arg(doc = "Kinesis Analytics Application")
) = {
  val req = new StopApplicationRequest().withApplicationName(name)
  apps.stopApplication(req)
}

@main def createFirehose(
    name: String @arg(doc = "Kinesis Firehose"),
    destBucketArn: String @arg(doc = "S3 destinaton bucket ARN"),
    iamRole: String @arg(doc = "ARN of S3 IAM role for Firehose instance")
) = {
  val req = new CreateDeliveryStreamRequest()
    .withDeliveryStreamName(name)
    .withDeliveryStreamType("DirectPut")
  val s3 = new ExtendedS3DestinationConfiguration()
    .withBucketARN(destBucketArn)
    .withRoleARN(iamRole)
    .withBufferingHints(
      new BufferingHints()
        .withIntervalInSeconds(60)
        .withSizeInMBs(1)
    )
  req.setExtendedS3DestinationConfiguration(s3)
  val res = firehoses.createDeliveryStream(req)
  println(res)
}

@main def deleteFirehose(name: String @arg(doc = "Kinesis Firehose")) = {
  val req = new DeleteDeliveryStreamRequest().withDeliveryStreamName(name)
  val res = firehoses.deleteDeliveryStream(req)
  println(res)
}
