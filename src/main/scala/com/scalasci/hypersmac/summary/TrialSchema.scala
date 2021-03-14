package com.scalasci.hypersmac.summary

import com.scalasci.hypersmac.model.Trial
import smile.data.{DataFrame, Tuple}
import smile.data.`type`.{DataTypes, DoubleType, StringType, StructField, StructType}

import scala.collection.JavaConverters.seqAsJavaListConverter

object TrialSchema {

  /**
    * This schema defines a df format for summarizing trials
    */
  val schema = new StructType(
    new StructField("run_id", DataTypes.StringType),
    new StructField("loss", DataTypes.DoubleType),
    new StructField("budget", DataTypes.DoubleType),
    new StructField("method", DataTypes.StringType),
  )

  /**
    * Convert some trial results into a summary data frame
    * @param resultSyn
    * @return
    */
  def trialsToSmileDF(resultSyn: Seq[Trial[_]]): DataFrame = {
    DataFrame.of(
      resultSyn
        .filter(_.cost.isDefined)
        .map { result =>
          Tuple.of(
            Array(result.configID, result.cost.get, result.budget, result.note.getOrElse(""))
              .map(_.asInstanceOf[AnyRef]),
            schema
          )
        }
        .asJava
    )
  }
}
