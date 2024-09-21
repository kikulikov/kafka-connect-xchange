package io.connect.xchange;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.header.ConnectHeaders;
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
      .field("instrument", Schema.STRING_SCHEMA)
      .field("open", Schema.OPTIONAL_FLOAT64_SCHEMA)
      .field("last", Schema.FLOAT64_SCHEMA)
      .field("bid", Schema.FLOAT64_SCHEMA)
      .field("ask", Schema.FLOAT64_SCHEMA)
      .field("high", Schema.FLOAT64_SCHEMA)
      .field("low", Schema.FLOAT64_SCHEMA)
      .field("volume", Schema.FLOAT64_SCHEMA)
      .field("quoteVolume", Schema.FLOAT64_SCHEMA)
      .field("timestamp", Schema.INT64_SCHEMA)
      .field("bidSize", Schema.FLOAT64_SCHEMA)
      .field("askSize", Schema.FLOAT64_SCHEMA)
      .field("percentageChange", Schema.FLOAT64_SCHEMA)
      .build();
  public static final String EXCHANGE_HEADER = "exchange";

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

    // create and init the exchange
    if (exchange == null) {
      final var markerClassName = config.getString(XChangeConfig.MARKET_CLASS_NAME_CONF);
      this.exchange = ExchangeFactory.INSTANCE.createExchange(markerClassName);

      try {
        exchange.remoteInit();
      } catch (IOException e) {
        throw new ConnectException(e);
      }
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
      final var ticker = marketTicker(symbol);
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
        null,
        recordHeaders());
  }

  private ConnectHeaders recordHeaders() {
    final var headers = new ConnectHeaders();
    headers.addString(EXCHANGE_HEADER, exchangeName().toLowerCase());
    return headers;
  }

  private String exchangeName() {
    return exchange.getExchangeSpecification().getExchangeName();
  }

  private static String tickerKey(Ticker ticker) {
    return ticker.getInstrument().toString();
  }

  private Struct tickerValue(Ticker ticker) {
    /*
     * @param instrument       Traded instrument
     * @param open             Open price
     * @param last             Last price
     * @param bid              Bid price
     * @param ask              Ask price
     * @param high             High price
     * @param low              Low price
     * @param volume           24h volume in base currency
     * @param quoteVolume      24h volume in counter currency
     * @param timestamp        The timestamp of the ticker
     * @param bidSize          The instantaneous size at the bid price
     * @param askSize          The instantaneous size at the ask price
     * @param percentageChange Price percentage change
     */
    return new Struct(TICKER_VALUE_SCHEMA)
        .put("instrument", ticker.getInstrument().toString())
        .put("open", ticker.getOpen().doubleValue())
        .put("last", ticker.getLast().doubleValue())
        .put("bid", ticker.getBid().doubleValue())
        .put("ask", ticker.getAsk().doubleValue())
        .put("high", ticker.getHigh().doubleValue())
        .put("low", ticker.getLow().doubleValue())
        .put("volume", ticker.getVolume().doubleValue())
        .put("quoteVolume", ticker.getVolume().doubleValue())
        .put("timestamp", DateUtils.toMillisNullSafe(ticker.getTimestamp()))
        .put("bidSize", ticker.getBidSize().doubleValue())
        .put("askSize", ticker.getAskSize().doubleValue())
        .put("percentageChange", ticker.getPercentageChange().doubleValue());
  }

  private Ticker marketTicker(String symbol) {
    try {
      final Instrument instrument = new CurrencyPair(symbol);
      return exchange.getMarketDataService().getTicker(instrument);
    } catch (IOException e) {
      throw new ConnectException(e);
    }
  }

  @Override
  public void stop() {
  }
}
