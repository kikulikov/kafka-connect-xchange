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
  public static final String MARKET_DATA_SYMBOLS_CONF = "xchange.market.data.symbols";
  private static final String MARKET_DATA_SYMBOLS_DOC = "The market data symbols that will be tracked.";
  public static final String MARKET_DATA_EXCHANGE_CONF = "xchange.market.data.exchange";
  private static final String MARKET_DATA_EXCHANGE_DOC = "The exchange from which market data will be retrieved.";

  public static final ConfigDef CONFIG_DEF =
      new ConfigDef()
          .define(KAFKA_TOPIC_CONF, Type.STRING, Importance.HIGH, KAFKA_TOPIC_DOC)
          .define(POLL_INTERVAL_MS_CONF, Type.LONG, 1000, Importance.HIGH, POLL_INTERVAL_MS_DOC)
          .define(MARKET_DATA_SYMBOLS_CONF, Type.STRING, Importance.HIGH, MARKET_DATA_SYMBOLS_DOC)
          .define(MARKET_DATA_EXCHANGE_CONF, Type.STRING, Importance.HIGH, MARKET_DATA_EXCHANGE_DOC);

  public XChangeConfig(Map<String, String> parsedConfig) {
    super(CONFIG_DEF, parsedConfig);
  }
}

