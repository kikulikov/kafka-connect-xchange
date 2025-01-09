# XCHANGE

## TODO

1. image
2. order number / priority
3. network
4. 



## Running in development

### Run unit and integration tests. Build the project.

```bash
mvn clean test failsafe:integration-test package
```

### Run standalone connect and submit the connector

```bash
export CLASSPATH="$(find target -type f -name '*.jar' | grep '\-with-dependencies' | tr '\n' ':')"
export CLASSPATH="$(find target -type f -name '*.jar' | grep -v 'tests' | tr '\n' ':')"
export CONFLUENT_HOME="/PATH"

${CONFLUENT_HOME}/bin/connect-standalone config/standalone-worker.properties config/standalone-connector-binance.properties

kafka-topics --bootstrap-server=localhost:9092 --list
kafka-topics --bootstrap-server=localhost:9092 --create --topic=prices --replication-factor=1 --partitions=2
kafka-topics --bootstrap-server=localhost:9092 --describe --topic=prices

kafka-console-consumer --bootstrap-server localhost:9092 --topic testing --from-beginning

curl -s -XGET -H "Content-Type: application/json; charset=UTF-8" http://localhost:8083/connectors/

curl -s -XPOST -H "Content-Type: application/json; charset=UTF-8" http://localhost:8083/connectors/ -d '
{
    "name": "solitary-file-source",
    "config": {
      "connector.class":"io.michelin.connect.SolitaryFileSourceConnector",
      "tasks.max":"1",
      "topic":"testing",
      "input.path":"/tmp/input",
      "input.file.pattern":"oom.*\\.txt",
      "finished.path":"/tmp/finished",
      "file.poll.interval.ms":"1000"
    }
}'

SHOW CREATE TABLE `market-data`;

ALTER TABLE `market-data` ADD headers MAP<STRING, STRING> METADATA;

SELECT `instrument`, `last`, `timestamp`, `percentageChange`, headers['exchange'] AS exchange FROM `market-data`;

CREATE TABLE `default`.`start`.`market-prices` (
  `key` STRING,
  `instrument` STRING,
  `last` DOUBLE,
  `timestamp` BIGINT,
  `percentageChange` DOUBLE,
  `exchange` STRING,
  PRIMARY KEY (`key`) NOT ENFORCED
) 
DISTRIBUTED BY HASH(`key`) INTO 6 BUCKETS
WITH (
  'key.format' = 'raw',
  'value.format' = 'json-registry',
  'scan.startup.mode' = 'latest-offset',
) AS SELECT `instrument` as `key`, `instrument`, `last`, `timestamp`, `percentageChange`, headers['exchange'] AS exchange from `market-data`;
```