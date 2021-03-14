package com.scalasci.hypersmac.implemented

import com.scalasci.hypersmac.api.RendersConfig
import com.scalasci.hypersmac.model.Trial

import smile.data.`type`.{StructField, StructType}
import smile.data.{DataFrame, Tuple}
import smile.data.formula._

import scala.collection.JavaConverters._

object XGSMAC {
  case class XGSMACConfig(takeTopPercent: Double = 0.2, minNTrain: Int = 30)
  def apply[ConfigSample](config: XGSMACConfig)(
    implicit renders: RendersConfig[ConfigSample]
  ): Seq[Trial[ConfigSample]] => ConfigSample => Boolean =
    (history: Seq[Trial[ConfigSample]]) => {

      if (history.length >= config.minNTrain) {
        //train smac classifier
        println("Training XGBoost SMAC model")
        val fvvLength = renders.render(history.head.config).length

        val schema = new StructType(
          Seq
            .range(0, fvvLength)
            .map(
              i =>
                new StructField(
                  "feature_" + i,
                  smile.data.`type`.DataTypes.DoubleType
              )
            )
            .:+(
              new StructField("target", smile.data.`type`.DataTypes.IntegerType)
            ): _*
        )

        def toTuple(x: ConfigSample, label: Boolean) = {
          val xArray = renders.render(x)
          assert(fvvLength == xArray.length)
          Tuple.of(
            (xArray :+ (if (label) 1 else 0))
              .map(_.asInstanceOf[java.lang.Object]),
            schema
          )
        }

        val n: Int = (history.length * config.takeTopPercent).toInt
        val dataWinners = history
          .sortBy(_.cost.getOrElse(Double.MaxValue))
          .take(n)
          .map(_ -> true)
        val dataLosers = history
          .sortBy(_.cost.getOrElse(Double.MaxValue))
          .drop(n)
          .map(_ -> false)
        val vectors: java.util.List[Tuple] = (dataLosers ++ dataWinners).map {
          case (run, label) => toTuple(run.config, label)
        }.asJava

        val df = DataFrame.of(vectors)
        val form: Formula = "target".~
        val trained = smile.classification.gbm(formula = form, data = df, subsample = 0.9)

        x: ConfigSample =>
          trained.predict(toTuple(x, label = true)) == 1
      } else {
        //not enough training data.
        x: ConfigSample =>
          true
      }
    }
}
