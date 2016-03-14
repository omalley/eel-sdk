package io.eels.component.hive

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.slf4j.StrictLogging
import io.eels.{Schema, Sink, SinkWriter}
import org.apache.hadoop.fs.FileSystem
import org.apache.hadoop.hive.metastore.IMetaStoreClient

case class HiveSink(private val dbName: String,
                    private val tableName: String,
                    private val ioThreads: Int = 4,
                    private val dynamicPartitioning: Option[Boolean] = None,
                    private val schemaEvolution: Option[Boolean] = None)
                   (implicit fs: FileSystem, client: IMetaStoreClient) extends Sink with StrictLogging {

  val config = ConfigFactory.load()
  val includePartitionsInData = config.getBoolean("eel.hive.includePartitionsInData")
  val bufferSize = config.getInt("eel.hive.bufferSize")
  val SchemaEvolutionDefault = config.getBoolean("eel.hive.sink.schemaEvolution")
  val DynamicPartitioningDefault = config.getBoolean("eel.hive.sink.dynamicPartitioning")

  def withIOThreads(ioThreads: Int): HiveSink = copy(ioThreads = ioThreads)
  def withDynamicPartitioning(partitioning: Boolean): HiveSink = copy(dynamicPartitioning = Some(partitioning))
  def withSchemaEvolution(schemaEvolution: Boolean): HiveSink = copy(schemaEvolution = Some(schemaEvolution))

  private def dialect(implicit client: IMetaStoreClient): HiveDialect = {
    val format = HiveOps.tableFormat(dbName, tableName)
    logger.debug(s"Table format is $format")
    HiveDialect(format)
  }

  override def writer(schema: Schema): SinkWriter = {

    if (schemaEvolution.getOrElse(SchemaEvolutionDefault)) {
      HiveSchemaEvolve(dbName, tableName, schema)
    }

    new HiveSinkWriter(
      schema,
      HiveOps.schema(dbName, tableName),
      dbName,
      tableName,
      ioThreads,
      dialect,
      dynamicPartitioning.getOrElse(DynamicPartitioningDefault),
      includePartitionsInData,
      bufferSize
    )
  }
}