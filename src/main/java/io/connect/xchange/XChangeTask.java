package io.connect.xchange;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.knowm.xchange.ExchangeFactory;
import org.knowm.xchange.binance.BinanceExchange;
import org.knowm.xchange.binance.dto.marketdata.BinancePrice;
import org.knowm.xchange.binance.service.BinanceMarketDataServiceRaw;
import org.knowm.xchange.currency.CurrencyPair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class XChangeTask extends SourceTask {

  public static final String TASK_ID = "task.id";
  private static final Logger log = LoggerFactory.getLogger(XChangeTask.class);

  private static final Map<String, Long> SOURCE_OFFSET = null; // Collections.singletonMap("offset", 0L);
  private static final Map<String, Object> SOURCE_PARTITION = null; // Collections.singletonMap(TASK_ID, 0);

  private String kafkaTopic;
  private long pollIntervalMs;
  private Set<String> marketDataSymbols;
  private BinanceExchange exchange;

  @Override
  public String version() {
    return VersionUtil.getVersion();
  }

  public XChangeTask() {
    this.exchange = ExchangeFactory.INSTANCE.createExchange(BinanceExchange.class);
  }

  public XChangeTask(BinanceExchange exchange) {
    this.exchange = exchange;
  }

  @Override
  public void start(Map<String, String> props) {
    final var config = new XChangeConfig(props);
    this.kafkaTopic = config.getString(XChangeConfig.KAFKA_TOPIC_CONF);
    this.pollIntervalMs = config.getLong(XChangeConfig.POLL_INTERVAL_MS_CONF);
    this.marketDataSymbols = Arrays.stream(splitSymbols(config))
        .filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet());

    // Calling Remote Init
    exchange.remoteInit();
  }

  private static String[] splitSymbols(XChangeConfig config) {
    return config.getString(XChangeConfig.MARKET_DATA_SYMBOLS_CONF).split(",");
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

    final long now = Instant.now().getEpochSecond();
    final var binancePrices = binancePriceList();

    final var schema = SchemaBuilder.struct().version(1)
        .field("symbol", Schema.STRING_SCHEMA)
        .field("price", Schema.FLOAT64_SCHEMA)
        .field("timestamp", Schema.INT64_SCHEMA)
        .build();

    final List<SourceRecord> records = binancePrices.stream()
        .filter(p -> p.getCurrencyPair() != null)
        .filter(p -> marketDataSymbols.contains(p.getCurrencyPair().toString()))
        .flatMap(p -> {
          final var currency = new CurrencyPair(p.getCurrencyPair().counter, p.getCurrencyPair().base);
          final var price = BigDecimal.ONE.divide(p.getPrice(), MathContext.DECIMAL64);
          final var inverse = BinancePrice.builder().currencyPair(currency).price(price).build();
          return Stream.of(p, inverse);
        })
        // .map(p -> new XChangeBinancePrice(p.getCurrencyPair().toString(), p.getPrice(), now))
        .map(price -> new SourceRecord(
            SOURCE_PARTITION,
            SOURCE_OFFSET,
            kafkaTopic,
            null,
            Schema.STRING_SCHEMA,
            price.getCurrencyPair().toString(),
            schema,
            valueStruct(schema, price, now),
            now,
            null
        )).toList();

    log.debug("Producing {}", records);

    return records;
  }

  private static Struct valueStruct(Schema schema, BinancePrice price, long now) {
    return new Struct(schema)
        .put("symbol", price.getCurrencyPair().toString())
        .put("price", price.getPrice().doubleValue())
        .put("timestamp", now);
  }

  private List<BinancePrice> binancePriceList() {
    try {
      return ((BinanceMarketDataServiceRaw) exchange.getMarketDataService()).tickerAllPrices();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void stop() {
  }
}
