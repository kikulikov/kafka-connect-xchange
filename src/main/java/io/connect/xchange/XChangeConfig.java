package io.connect.xchange;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;

import java.util.Map;

public class XChangeConfig extends AbstractConfig {

  public static final String KAFKA_TOPIC_CONF = "kafka.topic";
  private static final String KAFKA_TOPIC_DOC = "The Kafka topic to which data will be written.";
  public static final String POLL_INTERVAL_MS_CONF = "xchange.poll.interval.ms";
  private static final String POLL_INTERVAL_MS_DOC = "The frequency, in milliseconds, at which polling will occur.";
  public static final String MARKET_INSTRUMENTS_CONF = "xchange.market.instruments";
  private static final String MARKET_INSTRUMENTS_DOC = "The market data instruments that will be tracked.";
  public static final String MARKET_CLASS_NAME_CONF = "xchange.market.class.name";
  private static final String MARKET_CLASS_NAME_DOC = "The exchange from which data will be retrieved.";

  public static final ConfigDef CONFIG_DEF =
      new ConfigDef()
          .define(KAFKA_TOPIC_CONF, Type.STRING, Importance.HIGH, KAFKA_TOPIC_DOC)
          .define(POLL_INTERVAL_MS_CONF, Type.LONG, 5000, Importance.HIGH, POLL_INTERVAL_MS_DOC)
          .define(MARKET_INSTRUMENTS_CONF, Type.LIST, Importance.HIGH, MARKET_INSTRUMENTS_DOC)
          .define(MARKET_CLASS_NAME_CONF, Type.STRING, Importance.HIGH, MARKET_CLASS_NAME_DOC);

  public XChangeConfig(Map<String, String> parsedConfig) {
    super(CONFIG_DEF, parsedConfig);
  }
}
