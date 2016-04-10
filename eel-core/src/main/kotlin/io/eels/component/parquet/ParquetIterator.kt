package io.eels.component.parquet

import io.eels.Row
import io.eels.Schema
import io.eels.component.avro.fromRecord
import io.eels.component.avro.toAvro
import io.eels.map
import io.eels.nullTerminatedIterator
import org.apache.avro.generic.GenericRecord
import org.apache.parquet.hadoop.ParquetReader
import java.util.stream.Stream

fun parquetIterator(reader: ParquetReader<GenericRecord>, schema: Schema): Iterator<Row> = object : Iterator<Row> {

  val avroSchema = toAvro(schema)

  val iter = Stream.generate { reader.read() }.nullTerminatedIterator().map { fromRecord(it) }

  override fun hasNext(): Boolean {
    val hasNext = iter.hasNext()
    if (!hasNext) {
      reader.close()
    }
    return hasNext
  }

  override fun next(): Row = iter.next()
}