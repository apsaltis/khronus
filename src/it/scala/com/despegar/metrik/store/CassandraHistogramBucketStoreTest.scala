package com.despegar.metrik.store

import com.despegar.metrik.model.HistogramBucket
import com.despegar.metrik.util.Config
import com.netflix.astyanax.Keyspace
import org.HdrHistogram.Histogram
import org.scalatest.{ BeforeAndAfterAll, FunSuite }
import scala.collection.JavaConverters._
import scala.concurrent.duration._
import java.util.HashMap
import org.scalatest.Matchers
import scala.util.Random
import com.netflix.astyanax.model.ColumnFamily
import org.scalatest.BeforeAndAfter

class CassandraHistogramBucketStoreTest extends FunSuite with BeforeAndAfter with BeforeAndAfterAll with Config with Matchers {

  override def beforeAll = {
    createKeyspace
    createColumnFamilies
  }
  
  after {
	truncateColumnFamilies
  }

  override def afterAll = dropKeyspace

  test("should store and retrieve buckets properly") {
    val histogram = HistogramBucket.newHistogram
    fill(histogram)
    val buckets = Seq(HistogramBucket(30, 30 seconds, histogram))
    CassandraHistogramBucketStore.store("testMetric", 30 seconds, buckets)
    val bucketsFromCassandra = CassandraHistogramBucketStore.sliceUntilNow("testMetric", 30 seconds)
    val bucketFromCassandra = bucketsFromCassandra(0)
    
    histogram shouldEqual bucketFromCassandra.histogram 
  }
  
  test("should not retrieve buckets from the future") {
    val histogram = HistogramBucket.newHistogram
    val futureBucket = System.currentTimeMillis() + 60000 / (30 seconds).toMillis
    val bucketFromTheFuture = HistogramBucket(futureBucket, 30 seconds, histogram)
    val bucketFromThePast = HistogramBucket(30, 30 seconds, histogram)
    
    val buckets = Seq(bucketFromThePast, bucketFromTheFuture)
    
    CassandraHistogramBucketStore.store("testMetric", 30 seconds, buckets)
    val bucketsFromCassandra = CassandraHistogramBucketStore.sliceUntilNow("testMetric", 30 seconds)
    
    bucketsFromCassandra should have length 1
    bucketsFromCassandra(0) shouldEqual bucketFromThePast
  }
  
  test("should remove buckets") {
    val bucket1 = HistogramBucket(1, 30 seconds, HistogramBucket.newHistogram)
    val bucket2 = HistogramBucket(2, 30 seconds, HistogramBucket.newHistogram)
    
    CassandraHistogramBucketStore.store("testMetric", 30 seconds, Seq(bucket1, bucket2))
    
    CassandraHistogramBucketStore.remove("testMetric", 30 seconds, Seq(bucket1, bucket2))
    
    val bucketsFromCassandra = CassandraHistogramBucketStore.sliceUntilNow("testMetric", 30 seconds)
    
    bucketsFromCassandra should be ('empty)
  }
  
  private def fill(histogram: Histogram) = {
    (1 to 10000) foreach { i => histogram.recordValue(Random.nextInt(200)) }
  }

  private def createKeyspace = {
    val keyspace = Map("strategy_options" -> Map("replication_factor" -> "1").asJava, "strategy_class" -> "SimpleStrategy")
    val result = Cassandra.keyspace.createKeyspaceIfNotExists(keyspace.asJava).getResult();
    result.getSchemaId()
  }
  
  private def createColumnFamilies = foreachColumnFamily {  Cassandra.keyspace.createColumnFamily(_, Map[String,Object]().asJava) }
  
  private def truncateColumnFamilies = foreachColumnFamily { Cassandra.keyspace.truncateColumnFamily(_) }
  
  private def foreachColumnFamily(f: ColumnFamily[String,java.lang.Long] => Unit) = {
    CassandraHistogramBucketStore.columnFamilies.values.foreach{ f }
  }
  
  private def dropKeyspace = Cassandra.keyspace.dropKeyspace()
  
}
