package co.movio.sparksqlprometheusexporter

import atto._, Atto._, atto.ParseResult._
import cats._
import cats.data._
import cats.implicits._
import com.monovore.decline._

sealed trait Result

case class Succeeded(
    labels: List[PrometheusName],
    rows: List[SingleMetric]
) extends Result

case class Errored(
    message: String
) extends Result

case class SingleMetric(
    labels: List[String],
    value: Double
)

////////////////////////////////////////////////////////////////////////////////

case class Query(val toSQLString: String)
object Query {
  val parser: Parser[Query] = many(anyChar).map(_.mkString).map(Query.apply)
  implicit val argument = Utils.parserToArgument(parser, "sql")
}

case class Metric[T](name: PrometheusName, value: T)
object Metric {
  val parser: Parser[Metric[Query]] = {
    for {
      name <- PrometheusName.parser
      _ <- char('=')
      query <- Query.parser
    } yield Metric[Query](name, query)
  }
  implicit val functor: Functor[Metric] = new Functor[Metric] {
    def map[A, B](fa: Metric[A])(f: A => B): Metric[B] =
      fa match { case Metric(n, a) => Metric(n, f(a)) }
  }
  implicit val argument = Utils.parserToArgument(parser, "metric")
}

class PrometheusName(val toPlainString: String) extends AnyVal
object PrometheusName {
  val parser: Parser[PrometheusName] =
    for {
      head <- elem(c => c.isLetter || c == '_')
      tail <- many(elem(c => c.isLetterOrDigit || c == '_'))
    } yield new PrometheusName((head +: tail).mkString)

  def apply(value: String): Option[PrometheusName] =
    Utils.runParser(parser, value).toOption

  def applyOrThrow(value: String): PrometheusName = {
    apply(value).getOrElse {
      throw new Exception(s"'${value}' contains invalid characters.")
    }
  }

  implicit val argument = Utils.parserToArgument(parser, "name")
}

case class Args(
    pushgateway: String,
    jobName: PrometheusName,
    metrics: List[Metric[Query]]
)
object Args {
  val opts: Opts[Args] = {
    val metricHelp =
      """
      Metrics to export. Format: <metric_name>=<SQL>
      SQL statement should have a column named "value" with a numeric type.
      Rest of the columns will be stringified and assigned as labels.
    """.stripMargin
    (Opts.option[String]("pushgateway", help = "URL to Prometheus Pushgateway")
      |@| Opts.option[PrometheusName]("job", help = "Job Name")
      |@| Opts
        .options[Metric[Query]]("metric", short = "m", help = metricHelp)
        .map(_.toList)).map(Args(_, _, _))
  }
}

////////////////////////////////////////////////////////////////////////////////

object Utils {
  def parserToArgument[T](parser: Parser[T], metavar: String): Argument[T] =
    new Argument[T] {
      override def read(str: String): ValidatedNel[String, T] =
        runParser(parser, str).toValidatedNel
      override def defaultMetavar = metavar
    }

  def runParser[T](parser: Parser[T], input: String): Either[String, T] =
    parser.parseOnly(input) match {
      case Done("", result) => Right(result)
      case Done(xs, result) =>
        Left(s"Error parsing '${input}', leftovers: ${xs}")
      case err => Left(err.toString)
    }
}
