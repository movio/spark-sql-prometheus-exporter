package co.movio.sparksqlprometheusexporter

import java.net.URL

import scala.util._

import cats.implicits._
import com.monovore.decline._

import org.apache.spark.sql._
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions._

import io.prometheus.client.exporter.PushGateway
import io.prometheus.client.{Gauge, CollectorRegistry}

object Main {
  def main(argv: Array[String]): Unit = {
    val args = Command(
      name = "spark-sql-prometheus-exporter",
      header = "Run spark.sql queries and push results to Pushgateway."
    )(Args.opts).parse(argv) match {
      case Left(err) => {
        System.err.println(err)
        System.exit(1)
        null
      }
      case Right(xs) => xs
    }

    val spark =
      SparkSession.builder
        .appName(args.jobName.toString)
        .getOrCreate()

    val results = args.metrics.map(_.map(run(spark)))
    val (errs, succs) = separate(results)

    val registry = new CollectorRegistry

    succs.foreach(register(registry))

    Gauge
      .build()
      .name("spark_sql_prometheus_exporter_errors")
      .help("errors")
      .register(registry)
      .set(errs.size.toDouble)

    errs.foreach(report)

    new PushGateway(new URL(args.pushgateway))
      .pushAdd(registry, args.jobName.toPlainString)

    if (errs.nonEmpty)
      System.exit(1)
  }

  def prepareDF(df: DataFrame): (List[String], Dataset[SingleMetric]) = {
    import df.sparkSession.implicits._

    val valueName = "value"
    val labels = df.columns.filter(_ != valueName).toList

    val cols = labels.map(c => col(c).cast(StringType))

    val metrics = df
      .select(array(cols: _*).alias("labels"), col(valueName))
      .as[SingleMetric]

    (labels, metrics)
  }

  def run(spark: SparkSession)(query: Query): Result =
    Try {
      val (labels, df) = prepareDF(spark.sql(query.toSQLString))
      df.collect.toList match {
        case xxs @ (x :: _) =>
          Succeeded(labels.map(PrometheusName.applyOrThrow), xxs)
        case Nil =>
          Succeeded(List.empty, List.empty)
      }
    } match {
      case Failure(err) => Errored(err.toString)
      case Success(r) => r
    }

  def separate(xs: List[Metric[Result]])
    : (List[Metric[Errored]], List[Metric[Succeeded]]) =
    xs.foldRight((List.empty[Metric[Errored]], List.empty[Metric[Succeeded]])) {
      case (Metric(n, v: Errored), (fs, ss)) => (Metric(n, v) :: fs, ss)
      case (Metric(n, v: Succeeded), (fs, ss)) => (fs, Metric(n, v) :: ss)
    }

  def register(registry: CollectorRegistry)(metric: Metric[Succeeded]): Unit = {
    val gauge = Gauge.build
      .name(metric.name.toPlainString)
      .labelNames(metric.value.labels.map(_.toPlainString): _*)
      .help("gauge")
      .register(registry)
    metric.value.rows.foreach {
      case SingleMetric(labels, value) =>
        gauge.labels(labels: _*).set(value)
    }
  }

  def report(metric: Metric[Errored]): Unit = {
    System.err.println(s"Error on '${metric.name}: ${metric.value.message}'")
  }
}
