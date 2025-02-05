# XCHANGE

## Running in development

### Run unit and integration tests. Build the project.

```bash
mvn clean test failsafe:integration-test package
```

### Docker Clean Up

```bash
docker system df
docker system prune -a
docker volume prune
```

### Run standalone connect and submit the connector

```bash
export CLASSPATH="$(find target/components -type f -name '*.jar' | grep -v 'tests' | tr '\n' ':')"
export CONFLUENT_HOME='/Users/kikulikov/confluent/confluent-7.7.1'
export PATH="$PATH:$CONFLUENT_HOME/bin"

kafka-topics --bootstrap-server=localhost:9092 --list
kafka-topics --bootstrap-server=localhost:9092 --create --topic=prices --replication-factor=1 --partitions=3
kafka-topics --bootstrap-server=localhost:9092 --describe --topic=prices

# Run the connector
${CONFLUENT_HOME}/bin/connect-standalone config/standalone-worker.properties config/standalone-connector-bybit.properties

# Check the topic
kafka-console-consumer --bootstrap-server localhost:9092 --topic prices --from-beginning
```

### Run standalone connect and submit the connector

```bash
# check the connectors
curl -s -XGET -H "Content-Type: application/json; charset=UTF-8" http://localhost:8083/connectors/

# submit the connector
curl -s -XPOST -H "Content-Type: application/json; charset=UTF-8" http://localhost:8083/connectors/ -d @config/xchange-binance.config
```