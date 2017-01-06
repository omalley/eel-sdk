package io.eels.component.parquet

import io.eels.schema._
import org.apache.parquet.schema.PrimitiveType.PrimitiveTypeName
import org.apache.parquet.schema.Type.Repetition
import org.apache.parquet.schema._
import scala.collection.JavaConverters._

/**
  * See parquet formats at https://github.com/Parquet/parquet-format/blob/master/LogicalTypes.md
  */
object ParquetSchemaFns {

  def fromParquetPrimitiveType(`type`: PrimitiveType): DataType = {
    `type`.getPrimitiveTypeName match {
      case PrimitiveTypeName.BINARY =>
        `type`.getOriginalType match {
          case OriginalType.ENUM => EnumType(`type`.getName, Nil)
          case OriginalType.UTF8 => StringType
          case _ => BinaryType
        }
      case PrimitiveTypeName.BOOLEAN => BooleanType
      case PrimitiveTypeName.DOUBLE => DoubleType
      case PrimitiveTypeName.FLOAT => FloatType
      case PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY =>
        `type`.getOriginalType match {
          case OriginalType.DECIMAL =>
            val meta = `type`.getDecimalMetadata
            DecimalType(Precision(meta.getPrecision), Scale(meta.getScale))
          case _ => BinaryType
        }
      case PrimitiveTypeName.INT32 =>
        `type`.getOriginalType match {
          case OriginalType.UINT_32 => IntType.Unsigned
          case OriginalType.UINT_16 => ShortType.Unsigned
          case OriginalType.UINT_8 => ShortType.Unsigned
          case OriginalType.INT_16 => ShortType.Signed
          case OriginalType.INT_8 => ShortType.Signed
          case OriginalType.TIME_MILLIS => TimeMillisType
          case OriginalType.DATE => DateType
          case OriginalType.DECIMAL =>
            val meta = `type`.getDecimalMetadata
            DecimalType(Precision(meta.getPrecision), Scale(meta.getScale))
          case _ => IntType.Signed
        }
      case PrimitiveTypeName.INT64 if `type`.getOriginalType == OriginalType.UINT_64 => IntType.Unsigned
      case PrimitiveTypeName.INT64 if `type`.getOriginalType == OriginalType.TIME_MICROS => TimeMicrosType
      case PrimitiveTypeName.INT64 if `type`.getOriginalType == OriginalType.TIMESTAMP_MILLIS => TimestampMillisType
      case PrimitiveTypeName.INT64 if `type`.getOriginalType == OriginalType.TIMESTAMP_MICROS => TimestampMicrosType
      case PrimitiveTypeName.INT64 if `type`.getOriginalType == OriginalType.DECIMAL => DecimalType(Precision(18), Scale(2))
      case PrimitiveTypeName.INT64 => LongType.Signed
      // https://github.com/Parquet/parquet-mr/issues/218
      case PrimitiveTypeName.INT96 => TimestampMillisType
      case other => sys.error("Unsupported type " + other)
    }
  }

  def fromParquetGroupType(gt: GroupType): StructType = {
    val fields = gt.getFields.asScala.map { field =>
      val datatype = if (field.isPrimitive)
        fromParquetPrimitiveType(field.asPrimitiveType())
      else
        fromParquetGroupType(field.asGroupType)
      Field(field.getName, datatype, field.getRepetition == Repetition.OPTIONAL)
    }
    StructType(fields)
  }

  def byteSizeForPrecision(precision: Precision): Int = {
    var fixedArrayLength = 0
    var base10Digits = 0
    while (base10Digits < precision.value) {
      fixedArrayLength = fixedArrayLength + 1
      base10Digits = Math.floor(Math.log10(Math.pow(2, 8 * fixedArrayLength - 1) - 1)).toInt
    }
    fixedArrayLength
  }

  def toParquetType(field: Field): Type = {
    val repetition = if (field.nullable) Repetition.OPTIONAL else Repetition.REQUIRED
    field.dataType match {
      case BigIntType =>
        val metadata = new DecimalMetadata(38, 0)
        new PrimitiveType(repetition, PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY,
          20,
          field.name, OriginalType.DECIMAL, metadata, new Type.ID(1))
      case BinaryType => new PrimitiveType(repetition, PrimitiveTypeName.BINARY, field.name)
      case BooleanType => new PrimitiveType(repetition, PrimitiveTypeName.BOOLEAN, field.name)
      case CharType(size) => new PrimitiveType(repetition, PrimitiveTypeName.BINARY, field.name, OriginalType.UTF8)
      case DateType => new PrimitiveType(repetition, PrimitiveTypeName.INT32, field.name, OriginalType.DATE)
      // https://github.com/Parquet/parquet-format/blob/master/LogicalTypes.md#decimal
      // The scale stores the number of digits of that value that are to the right of the decimal point,
      // and the precision stores the maximum number of digits supported in the unscaled value.
      case DecimalType(precision, scale) =>
        val metadata = new DecimalMetadata(precision.value, scale.value)
        new PrimitiveType(repetition,
          PrimitiveTypeName.FIXED_LEN_BYTE_ARRAY,
          byteSizeForPrecision(precision),
          field.name,
          OriginalType.DECIMAL, metadata,
          new Type.ID(1)
        )
      case DoubleType => new PrimitiveType(repetition, PrimitiveTypeName.DOUBLE, field.name)
      case EnumType(name, values) => new PrimitiveType(repetition, PrimitiveTypeName.BINARY, name, OriginalType.ENUM)
      case FloatType => new PrimitiveType(repetition, PrimitiveTypeName.FLOAT, field.name)
      case IntType(true) => new PrimitiveType(repetition, PrimitiveTypeName.INT32, field.name)
      case IntType(false) => new PrimitiveType(repetition, PrimitiveTypeName.INT32, field.name, OriginalType.UINT_32)
      case LongType(true) => new PrimitiveType(repetition, PrimitiveTypeName.INT64, field.name)
      case LongType(false) => new PrimitiveType(repetition, PrimitiveTypeName.INT64, field.name, OriginalType.UINT_64)
      case ShortType(true) => new PrimitiveType(repetition, PrimitiveTypeName.INT32, field.name, OriginalType.INT_16)
      case ShortType(false) => new PrimitiveType(repetition, PrimitiveTypeName.INT32, field.name, OriginalType.UINT_16)
      case StructType(fields) => new GroupType(repetition, field.name, fields.map(toParquetType): _*)
      case StringType => new PrimitiveType(repetition, PrimitiveTypeName.BINARY, field.name, OriginalType.UTF8)
      case TimeMillisType => new PrimitiveType(repetition, PrimitiveTypeName.INT32, field.name, OriginalType.TIME_MILLIS)
      case TimeMicrosType => new PrimitiveType(repetition, PrimitiveTypeName.INT64, field.name, OriginalType.TIME_MICROS)
      // spark doesn't annotate timestamps, just uses int96, so same here
      case TimestampMillisType => new PrimitiveType(repetition, PrimitiveTypeName.INT96, field.name)
      case TimestampMicrosType => new PrimitiveType(repetition, PrimitiveTypeName.INT64, field.name, OriginalType.TIMESTAMP_MICROS)
      case VarcharType(size) => new PrimitiveType(repetition, PrimitiveTypeName.BINARY, field.name, OriginalType.UTF8)
    }
  }

  def toParquetSchema(schema: StructType, name: String = "row"): MessageType = {
    val types = schema.fields.map(toParquetType)
    new MessageType(name, types: _*)
  }
}
