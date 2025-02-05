package io.connect.xchange;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.knowm.xchange.utils.DateUtils;
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
  private static final Schema TICKER_VALUE_SCHEMA = SchemaBuilder.struct().version(1)
      .field("instrument", Schema.STRING_SCHEMA) // The tradable identifier (e.g. ETH/BTC)
      .field("baseSymbol", Schema.STRING_SCHEMA) // The tradable identifier (e.g. ETH/BTC)
      .field("counterSymbol", Schema.STRING_SCHEMA) // The tradable identifier (e.g. ETH/BTC)
      .field("openPrice", Schema.FLOAT64_SCHEMA) // Open price
      .field("lastPrice", Schema.FLOAT64_SCHEMA) // Last price
      .field("bidPrice", Schema.FLOAT64_SCHEMA) // Bid price
      .field("askPrice", Schema.FLOAT64_SCHEMA) // Ask price
      .field("highPrice", Schema.FLOAT64_SCHEMA) // High price
      .field("lowPrice", Schema.FLOAT64_SCHEMA) // Low price
      .field("volume", Schema.FLOAT64_SCHEMA) // 24h volume in base currency
      .field("quoteVolume", Schema.FLOAT64_SCHEMA) // 24h volume in counter currency
      .field("timestamp", Schema.INT64_SCHEMA) // The timestamp of the ticker
      .field("bidSize", Schema.FLOAT64_SCHEMA) //  The instantaneous size at the bid price
      .field("askSize", Schema.FLOAT64_SCHEMA) // The instantaneous size at the ask price
      .field("percentageChange", Schema.FLOAT64_SCHEMA) // Price percentage change
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
        .put("baseSymbol", ticker.getInstrument().getBase().getSymbol())
        .put("counterSymbol", ticker.getInstrument().getCounter().getSymbol())
        .put("openPrice", ticker.getOpen().doubleValue())
        .put("lastPrice", ticker.getLast().doubleValue())
        .put("bidPrice", ticker.getBid().doubleValue())
        .put("askPrice", ticker.getAsk().doubleValue())
        .put("highPrice", ticker.getHigh().doubleValue())
        .put("lowPrice", ticker.getLow().doubleValue())
        .put("volume", ticker.getVolume().doubleValue())
        .put("quoteVolume", ticker.getQuoteVolume().doubleValue())
        .put("timestamp", DateUtils.toMillisNullSafe(ticker.getTimestamp()))
        .put("bidSize", ticker.getBidSize().doubleValue())
        .put("askSize", ticker.getAskSize().doubleValue())
        .put("percentageChange", ticker.getPercentageChange().doubleValue());
  }

  private Ticker fetchMarketTicker(String symbol) {
    try {
      final Instrument instrument = new CurrencyPair(symbol);
      final String exchangeName = exchange.getDefaultExchangeSpecification().getExchangeName();
      log.info("Fetching the ticker for '{}' from exchange '{}'", symbol, exchangeName);
      return exchange.getMarketDataService().getTicker(instrument);
    } catch (IOException e) {
      throw new ConnectException(e);
    }
  }

  @Override
  public void stop() {
  }
}
