import $ivy.`jp.co.bizreach::aws-kinesis-scala:0.0.12`
import $ivy.`io.circe::circe-generic:0.13.0`
import $ivy.`org.scalacheck::scalacheck:1.14.3`

import io.circe.generic.auto._, io.circe.syntax._
import com.amazonaws.regions.Regions
import jp.co.bizreach.kinesis._
import org.scalacheck.Gen

import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import java.util.Date

implicit val region = Regions.EU_CENTRAL_1

val client = AmazonKinesis()

case class Sensor(number: Int, lat: Double, lon: Double, address: String)
case class Event(time: Long, temperature: Float, sensor: Sensor)

val sensor1 = Sensor(1, -75.5712, -130.5355, "123 Main St, LAX, CA")
val sensor2 = Sensor(2, -48.8712, -151.6866, "456 Side St, SFO, CA")

val sensors = Gen.oneOf(sensor1, sensor2)
val temperature = Gen.choose(21f, 25f)
val requests = Gen.choose(1, 10)

def genEvent =
  for {
    s <- sensors
    t <- temperature
  } yield Event(System.currentTimeMillis(), t, s)

def genBatch =
  for {
    size <- requests
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
    genBatch.sample
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
      doc = "Kinesis Stream name"
    ),
    shardsCount: Byte @arg(
      doc =
        "Number of shards for Kinesis Stream to be used in partitin key calculation"
    ),
    delay: Long @arg(doc = "Delay between each batch request in milliseconds") =
      1000
) = {
  Iterator
    .continually(genRequest(streamName, shardsCount))
    .takeWhile(_ => true)
    .foreach { _ =>
      val currentSeconds = System.currentTimeMillis() / 1000
      if (currentSeconds % 60 == 0) {
        println(s"New window at: ${new Date()}")
      }
      Thread.sleep(delay)
    }
}
