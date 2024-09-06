package io.connect.xchange;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigException;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class XChangeConnector extends SourceConnector {

  private static final Logger log = LoggerFactory.getLogger(XChangeConnector.class);
  private static final String CONNECTOR_NAME = XChangeConnector.class.getSimpleName();
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

    // TODO implement multi tasking to share the workload
    // for (int i = 0; i < maxTasks; i++) {
    final Map<String, String> taskConfig = this.config.values().entrySet().stream()
        .filter(s -> s.getValue() != null)
        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));

    // System.out.println(">>> task configs");
    // taskConfig.put(TASK_ID, Integer.toString(0));
    configs.add(taskConfig);
    // }

    return configs;
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
