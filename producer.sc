import $ivy.`jp.co.bizreach::aws-kinesis-scala:0.0.12`
import $ivy.`io.circe::circe-generic:0.13.0`
import $ivy.`org.scalacheck::scalacheck:1.14.3`

import io.circe.generic.auto._, io.circe.syntax._
import com.amazonaws.regions.Regions
import jp.co.bizreach.kinesis._
import org.scalacheck.Gen

import scala.util.Random
import scala.collection.mutable.ArrayBuffer

implicit val region = Regions.EU_CENTRAL_1

val client = AmazonKinesis()

case class Sensor(number: Int, lat: Double, lon: Double, address: String)
case class Event(time: Long, temperature: Float, sensor: Sensor)

val sensor1 = Sensor(1, -75.5712, -130.5355, "123 Main St, LAX, CA")
val sensor2 = Sensor(2, -48.8712, -151.6866, "456 Side St, SFO, CA")

val sensors = Gen.oneOf(sensor1, sensor2)
val temperature = Gen.choose(21f, 25f)

def genEvent =
  for {
    s <- sensors
    t <- temperature
  } yield Event(System.currentTimeMillis(), t, s)

def genBatch(size: Int = 10) =
  for {
    events <- Gen.listOfN(size, genEvent)
    dataBatch = events.map(_.asJson.noSpaces)
  } yield dataBatch

def putEntry(key: Long, data: String) =
  PutRecordsEntry(
    partitionKey = s"$key",
    data = data.getBytes("UTF-8")
  )

def genRequest(streamName: String, shards: Byte) = {
  val time = System.currentTimeMillis()
  val entries =
    genBatch(5).sample
      .foldLeft(ArrayBuffer.empty[PutRecordsEntry]) { case (acc, events) =>
        println(events.mkString("\n"))
        acc ++ events.map(e => putEntry((time % shards).toInt, e))
      }
      .toList
  val request = PutRecordsRequest(streamName, entries)
  client.putRecords(request)
}

@main
def main(
    streamName: String @arg(
      doc = "AWS Kinesis Stream name"
    ),
    shardsCount: Byte @arg(
      doc = "AWS Kinesis number of shards to be used partitin key calculation"
    ),
    delay: Long @arg(doc = "Delay between each batch request in milliseconds") =
      1000
) = {
  Iterator
    .continually(genRequest(streamName, shardsCount))
    .takeWhile(_ => true)
    .foreach(_ => Thread.sleep(delay))
}
