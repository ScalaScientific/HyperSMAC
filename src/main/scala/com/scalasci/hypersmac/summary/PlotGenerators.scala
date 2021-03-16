package com.scalasci.hypersmac.summary

import com.scalasci.hypersmac.model.TrialWithResult
import com.scalasci.hypersmac.summary.TrialSchema.trialsToSmileDF
import smile.plot

import java.awt.image.{BufferedImage, RenderedImage}
import smile.data._
import smile.plot.swing.{PlotGrid, ScatterPlot}

import java.io.File

object PlotGenerators {

  def plotBestVsBudg(trials:Seq[TrialWithResult[_]], chopTopPercent:Double = 0.1): ScatterPlot = {
    val maxCost = trials.map(_.cost).max
    val minCost = trials.map(_.cost).min
    val upperBound = trials.sortBy(_.cost).dropRight((trials.length * chopTopPercent).toInt).last.cost
    println(upperBound)
    val (labels, xy) = trials.filter(_.cost < upperBound).groupBy(t=>t.setup.note.getOrElse("")).map{case(k, vs)=>
      vs.map(_.setup.budget).scan(0.0)((l,r) => l+r).zip(
        vs.map(_.cost).scan(maxCost)(_ min _)).map(t=>k->Array(t._1, t._2)).filter(_._2(1) < upperBound).toArray}.flatten.toArray.unzip
        ScatterPlot.of(xy, labels, '.')
  }

  def savePlot(image:RenderedImage, file:File):Unit = {
    javax.imageio.ImageIO.write(image, "png", file)
  }

}
