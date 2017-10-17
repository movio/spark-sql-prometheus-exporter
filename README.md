# spark-sql-prometheus-exporter

[![Build Status](https://travis-ci.org/movio/spark-sql-prometheus-exporter.svg?branch=master)](https://travis-ci.org/movio/spark-sql-prometheus-exporter)

Run a spark-sql query and export results to [Prometheus Pushgateway](https://github.com/prometheus/pushgateway).

Usage:

```bash
spark-submit \
  --deploy-mode=cluster \
  https://github.com/movio/spark-sql-prometheus-exporter/releases/download/0.0.1/spark-sql-prometheus-exporter.jar \
  --pushgateway https://url-to-pushgateway/ \
  --job member_dataset_exporter \
  --metric 'member_count=SELECT COUNT(*) AS value FROM parquet.`hdfs:///etl/members`' \
  --metric 'members_by_country=SELECT country, COUNT(*) AS value FROM parquet.`hdfs:///etl/members` GROUP BY country'
```