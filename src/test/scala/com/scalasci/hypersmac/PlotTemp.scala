package com.scalasci.hypersmac

import scala.util.Random
import smile.data
import smile.data.`type`.{StructField, StructType}
import smile.read
import smile.data.`type`.DataTypes._
import smile.plot
import smile.plot.Render._
object PlotTemp extends App {

  val schema = new StructType(
    new StructField("run_id", StringType),
    new StructField("loss", DoubleType),
    new StructField("budget", DoubleType),
    new StructField("method", StringType),
  )

  val df = read.csv("assets/bench.csv", header = false, schema = schema)

  println(df.summary())

  val canvas = plot.swing.plot(df, "budget", "loss", "method", '*')
  canvas.setAxisLabels("budget", "loss")

  val image = canvas.toBufferedImage(400, 400)
  javax.imageio.ImageIO.write(image, "png", new java.io.File("bench.png"))
}
