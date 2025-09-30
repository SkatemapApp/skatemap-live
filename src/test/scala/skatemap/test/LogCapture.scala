package skatemap.test

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory

import scala.jdk.CollectionConverters._

class LogCapture private (loggerContext: LoggerContext, loggerName: String) {
  private val logger       = loggerContext.getLogger(loggerName)
  private val listAppender = new ListAppender[ILoggingEvent]()

  listAppender.setContext(loggerContext)
  listAppender.start()

  def start(): Unit =
    logger.addAppender(listAppender)

  def stop(): Unit = {
    logger.detachAppender(listAppender)
    listAppender.stop()
  }

  def clear(): Unit =
    listAppender.list.clear()

  def events: List[ILoggingEvent] =
    listAppender.list.asScala.toList

  def messages: List[String] =
    events.map(_.getFormattedMessage)

  def messagesContaining(text: String): List[String] =
    messages.filter(_.contains(text))

  def hasMessageContaining(text: String): Boolean =
    messages.exists(_.contains(text))

  def getMdcValue(key: String): Option[String] =
    events.reverse.headOption.flatMap { event =>
      Option(event.getMDCPropertyMap.get(key))
    }

  def getMdcValueFromEvent(key: String, messageContaining: String): Option[String] =
    events
      .find(_.getFormattedMessage.contains(messageContaining))
      .flatMap(event => Option(event.getMDCPropertyMap.get(key)))

  def getAllMdcValues(key: String): List[String] =
    events.flatMap { event =>
      Option(event.getMDCPropertyMap.get(key))
    }
}

object LogCapture {
  def apply(loggerName: String): Option[LogCapture] =
    LoggerFactory.getILoggerFactory match {
      case ctx: LoggerContext => Some(new LogCapture(ctx, loggerName))
      case _                  => None
    }

  def withCapture[T](loggerName: String)(fn: LogCapture => T): Option[T] =
    apply(loggerName).map { capture =>
      capture.start()
      try
        fn(capture)
      finally
        capture.stop()
    }
}
