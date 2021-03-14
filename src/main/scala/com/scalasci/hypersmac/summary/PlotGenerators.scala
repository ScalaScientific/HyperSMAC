package com.scalasci.hypersmac.summary

import com.scalasci.hypersmac.model.Trial
import com.scalasci.hypersmac.summary.TrialSchema.trialsToSmileDF
import smile.plot

import java.awt.image.{BufferedImage, RenderedImage}
import smile.data._
import smile.plot.swing.{PlotGrid, ScatterPlot}

import java.io.File

object PlotGenerators {
  def plotMethodBudgetLossBox(trials:Seq[Trial[_]]) = {
    val grid = new PlotGrid(1,3)

    val plots = for(budget <- trials.map(_.budget.toString.take(4)).distinct)yield{
      val df = trialsToSmileDF(trials)
      val dfFilt  = df.filter(_.getDouble("budget").toString.take(4) == budget)
      if(dfFilt.size() > 2){
        val (labels, grouped) = dfFilt.groupBy(row=>row.get("method")).mapValues(df=>df("loss").toDoubleArray).toArray.unzip
        val canvas = plot.swing.boxplot(grouped,labels.map(_.toString)).setTitle(s"budget: ${budget.toString.take(4)}")
        grid.add(canvas.panel())
      }

    }
    grid.window()
  }
  def plotBestVsBudg(trials:Seq[Trial[_]]) = {
    val (labels, xy) = trials.groupBy(t=>t.note.getOrElse("")).map{case(k, vs)=>
      val sorted = vs.filter(_.cost.isDefined)//.sortBy(a=> -a.cost.get)
      sorted.map(_.budget).scan(0.0)((l,r) => l+r).zip(
        sorted.map(_.cost.get).scan(sorted.map(_.cost.get).max)(_ min _)).map(t=>k->Array(t._1, t._2)).toArray}.flatten.toArray.unzip
        ScatterPlot.of(xy,labels, '.')
  }

  def savePlot(image:RenderedImage, file:File) = {
    javax.imageio.ImageIO.write(image, "png", file)
  }

}
