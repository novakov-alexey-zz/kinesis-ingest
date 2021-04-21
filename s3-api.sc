import $ivy.`com.github.seratch::awscala-s3:0.9.0`

import awscala._
import s3._

implicit val client = S3.at(Region.EU_CENTRAL_1)

@main def createBucket(
    name: String @arg(
      doc = "Unique S3 bucket name"
    )
) = {
  val bucket = client.createBucket(name)
  println(s"Bucket with name '$name' has been created")
}

@main def deleteBucket(
    name: String @arg(
      doc = "S3 bucket name"
    )
) = {
  val bucket = client
    .bucket(name)
    .getOrElse(sys.error(s"Bucket with name '$name' is not found"))
  val summaries = bucket.objectSummaries().toList
  summaries foreach { o =>
    println(s"deleting ${o.getKey}")
    client.deleteObject(bucket.name, o.getKey)
  }
  bucket.destroy()
  println(s"S3 bucket with name '$name' has been deleted")
}
