#!/bin/bash
set -e

confluent local destroy || true
mvn clean package || exit 1
rm -fr "$CONFLUENT_HOME"/share/confluent-hub-components/confluent-kafka-connect-xchange
confluent-hub install --no-prompt target/components/packages/confluent-kafka-connect-xchange-*.zip
confluent local services connect start
sleep 10

confluent local services connect status

#connectors="binance bybit"
connectors="binance"

for connector in $connectors; do
    confluent local services connect connector config xchange-$connector --config config/xchange-${connector}.config
done

confluent local services connect connector status
echo

for connector in $connectors; do
    echo
    echo $connector:
    confluent local services kafka consume $connector --max-messages 10 --property print.key=true --property key.deserializer=org.apache.kafka.common.serialization.StringDeserializer --from-beginning
done

confluent local destroy
