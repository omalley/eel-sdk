package io.eels.component

import java.util.UUID

import com.typesafe.config.ConfigFactory
import io.eels.Frame
import io.eels.component.jdbc.{JdbcSink, JdbcSource}

class SqlContext {
  Class.forName("org.h2.Driver")

  val config = ConfigFactory.load()
  val disk = config.getBoolean("eel.sqlContext.writeToDisk")
  val dataDirectory = config.getString("eel.sqlContext.dataDirectory")
  val ignoreCase = config.getBoolean("eel.sqlContext.ignoreCase").toString().toUpperCase()

  val uri = if (disk) {
    s"jdbc:h2:$dataDirectory/sqlcontext${UUID.randomUUID().toString().replace("-", "")};IGNORECASE=$ignoreCase;DB_CLOSE_DELAY=-1"
  } else {
    s"jdbc:h2:mem:sqlcontext${UUID.randomUUID().toString().replace("-", "")};IGNORECASE=$ignoreCase;DB_CLOSE_DELAY=-1"
  }

  def registerFrame(name: String, frame: Frame): Unit = {
    frame.to(JdbcSink(uri, name).withCreateTable(true))
  }

  def sql(query: String): Frame = JdbcSource(uri, query).toFrame()
}