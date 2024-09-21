package io.connect.xchange;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static io.connect.xchange.XChangeConfig.MARKET_INSTRUMENTS_CONF;

public class XChangeConnector extends SourceConnector {

  private static final Logger log = LoggerFactory.getLogger(XChangeConnector.class);
  private static final String CONNECTOR_NAME = XChangeConnector.class.getSimpleName();
  public static final String TASK_ID = "task.id";
  private XChangeConfig config;

  @Override
  public String version() {
    return VersionUtil.getVersion();
  }

  /**
   * This will be executed once per connector. This can be used to handle connector level setup.
   */
  @Override
  public void start(Map<String, String> properties) {
    log.info("The {} has been started.", CONNECTOR_NAME);
    try {
      this.config = new XChangeConfig(properties);
    } catch (ConfigException e) {
      throw new ConfigException("Could not start because of an error in the configuration: ", e);
    }
  }

  /**
   * Returns your task implementation.
   */
  @Override
  public Class<? extends Task> taskClass() {
    return XChangeTask.class;
  }

  /**
   * Defines the individual task configurations that will be executed. The connector doesn't support
   * multitasking. One task only.
   */
  @Override
  public List<Map<String, String>> taskConfigs(int maxTasks) {
    final List<Map<String, String>> configs = new ArrayList<>();

    final Map<String, String> taskConfig = this.config.values().entrySet().stream()
        .filter(s -> !MARKET_INSTRUMENTS_CONF.equals(s.getKey()) && s.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));

    final var symbols = config.getList(MARKET_INSTRUMENTS_CONF);
    final var evenTasks = distributeItems(symbols, maxTasks);

    for (int i = 0; i < evenTasks.size(); i++) {
      taskConfig.put(MARKET_INSTRUMENTS_CONF, String.join(",", evenTasks.get(i)));
      taskConfig.put(TASK_ID, Integer.toString(i));
      configs.add(new HashMap<>(taskConfig));
    }

    System.out.println(">>> " + configs);

    return configs;
  }

  private static List<List<String>> distributeItems(List<String> items, int numTasks) {
    final List<List<String>> tasks = new ArrayList<>();

    // Initialize each task with an empty list
    for (int i = 0; i < numTasks; i++) {
      tasks.add(new ArrayList<>());
    }

    int itemsPerTask = items.size() / numTasks; // Base number of items per task
    int remainder = items.size() % numTasks;    // Remaining items to distribute

    int itemIndex = 0;

    // Distribute the items among tasks
    for (int i = 0; i < numTasks; i++) {
      // Each task gets `itemsPerTask` items
      for (int j = 0; j < itemsPerTask; j++) {
        tasks.get(i).add(items.get(itemIndex++));
      }

      // Distribute the remainder (one extra item for the first `remainder` tasks)
      if (i < remainder) {
        tasks.get(i).add(items.get(itemIndex++));
      }
    }

    return tasks;
  }

  @Override
  public void stop() {
    log.info("The {} has been stopped.", CONNECTOR_NAME);
  }

  @Override
  public ConfigDef config() {
    return XChangeConfig.CONFIG_DEF;
  }
}
