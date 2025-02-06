package io.connect.xchange;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.data.Timestamp;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class XChangeTask extends SourceTask {

  private static final Logger log = LoggerFactory.getLogger(XChangeTask.class);
  private static final Map<String, Object> SOURCE_PARTITION = Collections.emptyMap();
  private static final Map<String, Long> SOURCE_OFFSET = null; // Collections.singletonMap("offset", 0L);

  public static final Schema TICKER_KEY_SCHEMA = Schema.STRING_SCHEMA;
  public static final String TIMESTAMP_CLASS_NAME = Timestamp.class.getName();
  public static final Schema TIMESTAMP_SCHEMA = SchemaBuilder.int64().name(TIMESTAMP_CLASS_NAME).build();

  private static final Schema TICKER_VALUE_SCHEMA = SchemaBuilder.struct().version(1)
      .field("instrument", Schema.STRING_SCHEMA) // The tradable identifier (e.g. ETH/BTC)
      .field("base_symbol", Schema.STRING_SCHEMA) // The tradable identifier (e.g. ETH/BTC)
      .field("counter_symbol", Schema.STRING_SCHEMA) // The tradable identifier (e.g. ETH/BTC)
      .field("open_price", Schema.FLOAT64_SCHEMA) // Open price
      .field("last_price", Schema.FLOAT64_SCHEMA) // Last price
      .field("bid_price", Schema.FLOAT64_SCHEMA) // Bid price
      .field("ask_price", Schema.FLOAT64_SCHEMA) // Ask price
      .field("high_price", Schema.FLOAT64_SCHEMA) // High price
      .field("low_price", Schema.FLOAT64_SCHEMA) // Low price
      .field("volume", Schema.FLOAT64_SCHEMA) // 24h volume in base currency
      .field("quote_volume", Schema.FLOAT64_SCHEMA) // 24h volume in counter currency
      .field("rate_timestamp", TIMESTAMP_SCHEMA) // The timestamp of the ticker
      .field("bid_size", Schema.FLOAT64_SCHEMA) //  The instantaneous size at the bid price
      .field("ask_size", Schema.FLOAT64_SCHEMA) // The instantaneous size at the ask price
      .field("percentage_change", Schema.FLOAT64_SCHEMA) // Price percentage change
      .build();

  private String kafkaTopic;
  private long pollIntervalMs;
  private List<String> dataSymbols;
  private Exchange exchange;

  @Override
  public String version() {
    return VersionUtil.getVersion();
  }

  public XChangeTask() {
    // gets exchange from the config
  }

  public XChangeTask(Exchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public void start(Map<String, String> props) {
    final var config = new XChangeConfig(props);
    this.kafkaTopic = config.getString(XChangeConfig.KAFKA_TOPIC_CONF);
    this.pollIntervalMs = config.getLong(XChangeConfig.POLL_INTERVAL_MS_CONF);
    this.dataSymbols = config.getList(XChangeConfig.MARKET_INSTRUMENTS_CONF);

    // create the exchange
    final var marketClassName = config.getString(XChangeConfig.MARKET_CLASS_NAME_CONF);
    this.exchange = ExchangeFactory.INSTANCE.createExchange(marketClassName);

    // init the exchange
    try {
      exchange.remoteInit();
    } catch (IOException e) {
      throw new ConnectException(e);
    }
  }

  @Override
  @SuppressWarnings("ResultOfMethodCallIgnored")
  public List<SourceRecord> poll() {

    if (pollIntervalMs > 0) {
      try {
        Thread.sleep(pollIntervalMs);
      } catch (InterruptedException e) {
        Thread.interrupted();
        return null;
      }
    }

    final List<SourceRecord> records = new ArrayList<>();

    for (String symbol : dataSymbols) {
      final var ticker = fetchMarketTicker(symbol);
      records.add(buildSourceRecord(ticker));
    }

    log.debug("Producing {}", records);

    return records;
  }

  private SourceRecord buildSourceRecord(Ticker ticker) {
    return new SourceRecord(
        SOURCE_PARTITION,
        SOURCE_OFFSET,
        kafkaTopic,
        null,
        TICKER_KEY_SCHEMA,
        tickerKey(ticker),
        TICKER_VALUE_SCHEMA,
        tickerValue(ticker),
        null);
  }

  private static String tickerKey(Ticker ticker) {
    return ticker.getInstrument().toString();
  }

  private Struct tickerValue(Ticker ticker) {
    return new Struct(TICKER_VALUE_SCHEMA)
        .put("instrument", ticker.getInstrument().toString())
        .put("base_symbol", ticker.getInstrument().getBase().getSymbol())
        .put("counter_symbol", ticker.getInstrument().getCounter().getSymbol())
        .put("open_price", ticker.getOpen().doubleValue())
        .put("last_price", ticker.getLast().doubleValue())
        .put("bid_price", ticker.getBid().doubleValue())
        .put("ask_price", ticker.getAsk().doubleValue())
        .put("high_price", ticker.getHigh().doubleValue())
        .put("low_price", ticker.getLow().doubleValue())
        .put("volume", ticker.getVolume().doubleValue())
        .put("quote_volume", ticker.getQuoteVolume().doubleValue())
        .put("rate_timestamp", ticker.getTimestamp())
        .put("bid_size", ticker.getBidSize().doubleValue())
        .put("ask_size", ticker.getAskSize().doubleValue())
        .put("percentage_change", ticker.getPercentageChange().doubleValue());
  }

  private Ticker fetchMarketTicker(String symbol) {
    try {
      final Instrument instrument = new CurrencyPair(symbol);
      final String exchangeName = exchange.getDefaultExchangeSpecification().getExchangeName();
      log.info("Fetching the ticker for '{}' from '{}' exchange", symbol, exchangeName);
      return exchange.getMarketDataService().getTicker(instrument);
    } catch (IOException e) {
      throw new ConnectException(e);
    }
  }

  @Override
  public void stop() {
  }
}
