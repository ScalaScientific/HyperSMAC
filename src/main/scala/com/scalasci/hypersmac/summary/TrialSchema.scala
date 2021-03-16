package com.scalasci.hypersmac.summary

import com.scalasci.hypersmac.model.TrialWithResult
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
  def trialsToSmileDF(resultSyn: Seq[TrialWithResult[_]]): DataFrame = {
    DataFrame.of(
      resultSyn
        .map { result =>
          Tuple.of(
            Array(result.setup.configID, result.cost, result.setup.budget, result.setup.note.getOrElse(""))
              .map(_.asInstanceOf[AnyRef]),
            schema
          )
        }
        .asJava
    )
  }
}
