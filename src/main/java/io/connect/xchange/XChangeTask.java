package io.connect.xchange;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.knowm.xchange.Exchange;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.bybit.BybitExchange;
import org.knowm.xchange.currency.CurrencyPair;
import org.knowm.xchange.dto.marketdata.Ticker;
import org.knowm.xchange.instrument.Instrument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.stream.Collectors;

public class XChangeTask extends SourceTask {

  private static final Logger log = LoggerFactory.getLogger(XChangeTask.class);
  private static final String BINANCE_EXCHANGE = "binance";
  private static final String BYBIT_EXCHANGE = "bybit";
  private static final Map<String, Object> SOURCE_PARTITION = Collections.emptyMap();
  private static final Map<String, Long> SOURCE_OFFSET = null; // Collections.singletonMap("offset", 0L);

  private static final Schema TICKER_SCHEMA = SchemaBuilder.struct().version(1)
      .field("symbol", Schema.STRING_SCHEMA)
      .field("market", Schema.STRING_SCHEMA)
      .field("last", Schema.FLOAT64_SCHEMA)
      // .field("open", Schema.FLOAT64_SCHEMA)
      // .field("high", Schema.FLOAT64_SCHEMA)
      // .field("low", Schema.FLOAT64_SCHEMA)
      // .field("volume", Schema.FLOAT64_SCHEMA)
      // .field("percentageChange", Schema.OPTIONAL_INT64_SCHEMA)
      .field("timestamp", Schema.INT64_SCHEMA)
      .build();

  private String kafkaTopic;
  private long pollIntervalMs;
  private Set<String> dataSymbols;
  private String dataMarket;
  private Exchange exchange;

  @Override
  public String version() {
    return VersionUtil.getVersion();
  }

  public XChangeTask() {
    // TODO inject exchange for unit testing
  }

  public XChangeTask(BinanceExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public void start(Map<String, String> props) {
    final var config = new XChangeConfig(props);
    this.kafkaTopic = config.getString(XChangeConfig.KAFKA_TOPIC_CONF);
    this.pollIntervalMs = config.getLong(XChangeConfig.POLL_INTERVAL_MS_CONF);
    this.dataSymbols = Arrays.stream(splitSymbols(config))
        .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());
    this.dataMarket = config.getString(XChangeConfig.DATA_MARKET_CONF);

    if (BINANCE_EXCHANGE.equalsIgnoreCase(this.dataMarket)) {
      this.exchange = ExchangeFactory.INSTANCE.createExchange(BinanceExchange.class);
    }

    if (BYBIT_EXCHANGE.equalsIgnoreCase(this.dataMarket)) {
      this.exchange = ExchangeFactory.INSTANCE.createExchange(BybitExchange.class);
    }

    try {
      exchange.remoteInit();
    } catch (IOException e) {
      throw new ConnectException(e);
    }
  }

  private static String[] splitSymbols(XChangeConfig config) {
    return config.getString(XChangeConfig.DATA_SYMBOLS_CONF).split(",");
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
      records.add(buildSourceRecord(inverseTicker(ticker)));
    }

    log.debug("Producing {}", records);

    return records;
  }

  private static Ticker inverseTicker(Ticker ticker) {
    final Instrument instrument = new CurrencyPair(ticker.getInstrument().getCounter(), ticker.getInstrument().getBase());
    final var inverted = BigDecimal.ONE.divide(ticker.getLast(), MathContext.DECIMAL64);
    return new Ticker.Builder().instrument(instrument).last(inverted).timestamp(ticker.getTimestamp()).build();
  }

  private SourceRecord buildSourceRecord(Ticker ticker) {
    return new SourceRecord(
        SOURCE_PARTITION,
        SOURCE_OFFSET,
        kafkaTopic,
        null,
        Schema.STRING_SCHEMA,
        currencyPairName(ticker.getInstrument()),
        TICKER_SCHEMA,
        tickerStruct(ticker),
        null,
        null
    );
  }

  private Struct tickerStruct(Ticker ticker) {
    final var pair = currencyPairName(ticker.getInstrument());
    return new Struct(TICKER_SCHEMA)
        .put("symbol", pair)
        .put("market", this.dataMarket)
        .put("last", ticker.getLast().doubleValue())
        // .put("open", ticker.getOpen())
        // .put("high", ticker.getHigh())
        // .put("low", ticker.getLow())
        // .put("volume", ticker.getVolume())
        // .put("percentageChange", ticker.getPercentageChange());
        .put("timestamp", ticker.getTimestamp().getTime());
  }

  private static String currencyPairName(Instrument instrument) {
    return instrument.getBase().getSymbol() + "-" + instrument.getCounter();
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
