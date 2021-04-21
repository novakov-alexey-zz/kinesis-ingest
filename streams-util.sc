import $ivy.`jp.co.bizreach::aws-kinesis-scala:0.0.12`

import jp.co.bizreach.kinesis.CreateStreamRequest
import jp.co.bizreach.kinesis._
import com.amazonaws.regions.Regions
import com.amazonaws.services.kinesis.AmazonKinesisClientBuilder
import com.amazonaws.services.kinesisanalytics.AmazonKinesisAnalytics
import com.amazonaws.services.kinesisanalytics.AmazonKinesisAnalyticsClientBuilder
import com.amazonaws.services.kinesisanalytics.model.CreateApplicationRequest
import com.amazonaws.services.kinesisanalytics.model.KinesisStreamsInput
import com.amazonaws.services.kinesisanalytics.model.Input
import com.amazonaws.services.kinesisanalytics.model.DiscoverInputSchemaRequest
import com.amazonaws.services.kinesisanalytics.model.SourceSchema
import com.amazonaws.services.kinesisanalytics.model.InputStartingPositionConfiguration
import com.amazonaws.services.kinesisanalytics.model.InputStartingPosition
import com.amazonaws.services.kinesisanalytics.model.DeleteApplicationRequest
import com.amazonaws.services.kinesisanalytics.model.ListApplicationsRequest
import com.amazonaws.services.kinesisanalytics.model.DescribeApplicationRequest
import com.amazonaws.services.kinesisanalytics.model.StartApplicationRequest
import java.util.Date
import scala.util.Using
import scala.jdk.CollectionConverters._
import scala.io.Source
import com.amazonaws.services.kinesisanalytics.model.InputConfiguration
import com.amazonaws.services.kinesisanalytics.model.StopApplicationRequest

val region = Regions.EU_CENTRAL_1
lazy val streams =
  AmazonKinesisClientBuilder.standard().withRegion(region).build()
lazy val apps =
  AmazonKinesisAnalyticsClientBuilder.standard().withRegion(region).build()

@main
def createStream(
    name: String @arg(
      doc = "AWS Kinesis Stream name"
    ),
    shardsCount: Byte @arg(
      doc = "AWS Kinesis number of shards to be used partitin key calculation"
    )
) = {
  val createStreamRequest = CreateStreamRequest(name, shardsCount)
  val res = streams.createStream(createStreamRequest)
  println(res)
}

@main
def deleteStream(
    name: String @arg(
      doc = "AWS Kinesis Stream name"
    )
) =
  println(streams.deleteStream(name))

@main
def createApplication(
    name: String @arg(doc = "AWS Kinesis Analytics Application"),
    inputArn: String @arg(doc = "AWS Kinesis Stream ARN"),
    roleArn: String @arg(doc = "AWS Kinesis Analytics App Role"),
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
  val res = apps.createApplication(appRequest)
  print(res)
}

@main def deleteApplication(
    name: String @arg(doc = "AWS Kinesis Analytics Application")
) = {
  val descReq = new DescribeApplicationRequest()
  val resDesc = apps.describeApplication(descReq.withApplicationName(name))
  val req = new DeleteApplicationRequest()
  req.withCreateTimestamp(resDesc.getApplicationDetail().getCreateTimestamp())
  apps.deleteApplication(req.withApplicationName(name))
}

@main def startApplication(
    name: String @arg(doc = "AWS Kinesis Analytics Application")
) = {
  val req = new StartApplicationRequest()
  req.setApplicationName(name)
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
  name: String @arg(doc = "AWS Kinesis Analytics Application")
) = {
  val req = new StopApplicationRequest()
  req.setApplicationName(name)
  apps.stopApplication(req)
}