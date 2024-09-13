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
  public static final String DATA_SYMBOLS_CONF = "xchange.data.symbols";
  private static final String DATA_SYMBOLS_DOC = "The market data symbols that will be tracked.";
  public static final String DATA_MARKET_CONF = "xchange.data.market";
  private static final String DATA_MARKET_DOC = "The exchange from which data will be retrieved.";

  public static final ConfigDef CONFIG_DEF =
      new ConfigDef()
          .define(KAFKA_TOPIC_CONF, Type.STRING, Importance.HIGH, KAFKA_TOPIC_DOC)
          .define(POLL_INTERVAL_MS_CONF, Type.LONG, 5000, Importance.HIGH, POLL_INTERVAL_MS_DOC)
          .define(DATA_SYMBOLS_CONF, Type.STRING, Importance.HIGH, DATA_SYMBOLS_DOC)
          .define(DATA_MARKET_CONF, Type.STRING, Importance.HIGH, DATA_MARKET_DOC);

  public XChangeConfig(Map<String, String> parsedConfig) {
    super(CONFIG_DEF, parsedConfig);
  }
}

