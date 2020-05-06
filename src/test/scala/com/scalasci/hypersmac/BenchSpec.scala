package com.scalasci.hypersmac

import java.util.logging.Logger

import org.scalatest.flatspec.AnyFlatSpec
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.eval.RegressionEvaluation
import java.io.File
import java.net.URL
import java.io.BufferedInputStream
import java.io.FileInputStream
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.util.concurrent.Executor

import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.FileUtils
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import com.scalasci.hypersmac.api.RendersConfig
import com.scalasci.hypersmac.implemented.{
  BudgetedSampleFunction,
  GaussianPrior,
  HyperSMAC
}
import com.scalasci.hypersmac.model.Trial
import org.nd4j.linalg.learning.config.AdaGrad

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}
class BenchSpec extends AnyFlatSpec {
  val log = Logger.getLogger("Benchmarks")
  val DATA_URL =
    "https://dl4jdata.blob.core.windows.net/training/seatemp/sea_temp.tar.gz"
  val DATA_PATH =
    FilenameUtils.concat(System.getProperty("java.io.tmpdir"), "dl4j_seas/")

  val directory = new File(DATA_PATH)
  directory.mkdir()

  val archizePath = DATA_PATH + "sea_temp.tar.gz"
  val archiveFile = new File(archizePath)
  val extractedPath = DATA_PATH + "sea_temp"
  val extractedFile = new File(extractedPath)

  new File(new URL(DATA_URL).getPath)

  FileUtils.copyURLToFile(new URL(DATA_URL), archiveFile)
  println("copied")
  var fileCount = 0
  var dirCount = 0
  val BUFFER_SIZE = 4096
  val tais = new TarArchiveInputStream(
    new GzipCompressorInputStream(
      new BufferedInputStream(new FileInputStream(archizePath))
    )
  )

  var entry: TarArchiveEntry = tais.getNextEntry.asInstanceOf[TarArchiveEntry]

  while (entry != null) {
    if (entry.isDirectory) {
      new File(DATA_PATH + entry.getName).mkdirs()
      dirCount = dirCount + 1
      fileCount = 0
    } else {
      val data = new Array[scala.Byte](4 * BUFFER_SIZE)

      val fos = new FileOutputStream(DATA_PATH + entry.getName);
      val dest = new BufferedOutputStream(fos, BUFFER_SIZE);
      var count = tais.read(data, 0, BUFFER_SIZE)

      while (count != -1) {
        dest.write(data, 0, count)
        count = tais.read(data, 0, BUFFER_SIZE)
      }

      dest.close()
      fileCount = fileCount + 1
    }
    if (fileCount % 1000 == 0) {
      print(".")
    }

    entry = tais.getNextEntry().asInstanceOf[TarArchiveEntry]
  }

  val path = FilenameUtils.concat(DATA_PATH, "sea_temp/") // set parent directory
  println(path)
  val featureBaseDir = FilenameUtils.concat(path, "features") // set feature directory
  val targetsBaseDir = FilenameUtils.concat(path, "targets") // set label directory

  import org.datavec.api.records.reader.impl.csv.CSVSequenceRecordReader
  import org.datavec.api.split.NumberedFileInputSplit
  import org.deeplearning4j.datasets.datavec.SequenceRecordReaderDataSetIterator

  val numSkipLines = 1
  val regression = true
  val batchSize = 32

  val trainFeatures = new CSVSequenceRecordReader(numSkipLines, ",")
  trainFeatures.initialize(
    new NumberedFileInputSplit(featureBaseDir + "/%d.csv", 1, 1536)
  )
  val trainTargets = new CSVSequenceRecordReader(numSkipLines, ",")
  trainTargets.initialize(
    new NumberedFileInputSplit(targetsBaseDir + "/%d.csv", 1, 1536)
  )

  val train = new SequenceRecordReaderDataSetIterator(
    trainFeatures,
    trainTargets,
    batchSize,
    10,
    regression,
    SequenceRecordReaderDataSetIterator.AlignmentMode.EQUAL_LENGTH
  )

  val testFeatures = new CSVSequenceRecordReader(numSkipLines, ",")
  testFeatures.initialize(
    new NumberedFileInputSplit(featureBaseDir + "/%d.csv", 1537, 1736)
  )
  val testTargets = new CSVSequenceRecordReader(numSkipLines, ",")
  testTargets.initialize(
    new NumberedFileInputSplit(targetsBaseDir + "/%d.csv", 1537, 1736)
  )

  val test = new SequenceRecordReaderDataSetIterator(
    testFeatures,
    testTargets,
    batchSize,
    10,
    regression,
    SequenceRecordReaderDataSetIterator.AlignmentMode.EQUAL_LENGTH
  )

  val V_HEIGHT = 13
  val V_WIDTH = 4
  val kernelSize = 2
  val numChannels = 1

  import org.deeplearning4j.nn.api.OptimizationAlgorithm
  import org.deeplearning4j.nn.conf.GradientNormalization
  import org.deeplearning4j.nn.conf.NeuralNetConfiguration
  import org.deeplearning4j.nn.conf.layers.ConvolutionLayer
  import org.deeplearning4j.nn.conf.layers.LSTM
  import org.deeplearning4j.nn.conf.layers.RnnOutputLayer
  import org.deeplearning4j.nn.conf.preprocessor.CnnToRnnPreProcessor
  import org.deeplearning4j.nn.conf.preprocessor.RnnToCnnPreProcessor
  import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
  import org.nd4j.linalg.activations.Activation
  import org.nd4j.linalg.lossfunctions.LossFunctions.LossFunction
  case class NauticalConfigurationSample(adaGradLR: Double, hiddenSize: Int) {
    def render() = Array(adaGradLR, hiddenSize)
    def toConf() = {
      new NeuralNetConfiguration.Builder()
        .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
        .seed(12345)
        .weightInit(WeightInit.XAVIER)
        .updater(new AdaGrad(adaGradLR))
        .list
        .layer(
          0,
          new ConvolutionLayer.Builder(kernelSize, kernelSize)
            .nIn(1)
            .nOut //1 channel
            (7)
            .stride(2, 2)
            .activation(Activation.RELU)
            .build
        )
        .layer(
          1,
          new LSTM.Builder()
            .activation(Activation.SOFTSIGN)
            .nIn(84)
            .nOut(hiddenSize)
            .gradientNormalization(
              GradientNormalization.ClipElementWiseAbsoluteValue
            )
            .gradientNormalizationThreshold(10)
            .build
        )
        .layer(
          2,
          new RnnOutputLayer.Builder(LossFunction.MSE)
            .activation(Activation.IDENTITY)
            .nIn(hiddenSize)
            .nOut(52)
            .gradientNormalization(
              GradientNormalization.ClipElementWiseAbsoluteValue
            )
            .gradientNormalizationThreshold(10)
            .build
        )
        .inputPreProcessor(
          0,
          new RnnToCnnPreProcessor(V_HEIGHT, V_WIDTH, numChannels)
        )
        .inputPreProcessor(1, new CnnToRnnPreProcessor(6, 2, 7))
        .build

    }
  }
  case class NauticalConfigurationSpace(
    adaGradLR: GaussianPrior = GaussianPrior(0.005, 0.002),
    hiddenSize: GaussianPrior = GaussianPrior(100, 100)
  ) {
    def sample() = {

      NauticalConfigurationSample(
        adaGradLR.sample(),
        hiddenSize.sample().toInt max 20
      )
    }
  }
  implicit val synchronousExecutionContext =
    ExecutionContext.fromExecutor(new Executor {
      def execute(task: Runnable) = task.run()
    })

  val hs =
    new HyperSMAC[NauticalConfigurationSpace, NauticalConfigurationSample] {
      override def trainSurrogateModel(
        history: Seq[Trial[NauticalConfigurationSample]]
      ): NauticalConfigurationSample => Boolean = _ => true
    }

  implicit val rc =
    new RendersConfig[NauticalConfigurationSpace, NauticalConfigurationSample] {

      /**
        * sample
        *
        * @param configSpace the space which can generate samples which can be rendered to vectors for smac
        * @param xElliptic   elliptic curve parameter to determine random sample
        * @return a sample from the config space
        */
      override def sample(configSpace: NauticalConfigurationSpace,
                          xElliptic: Int): NauticalConfigurationSample =
        configSpace.sample()

      /**
        * render a config sample into a smacable vector.
        *
        * @param config the config to render
        * @return the rendered vector
        */
      override def render(config: NauticalConfigurationSample): Array[Double] =
        config.render()
    }

  def f: BudgetedSampleFunction[NauticalConfigurationSample] =
    (confSample: NauticalConfigurationSample, budget: Double) =>
      Future {
        val conf = confSample.toConf()
        val net = new MultiLayerNetwork(conf)

        println("training") // Train model on training set
        net.fit(train, budget.toInt max 1)

        val eval = net.evaluateRegression[RegressionEvaluation](test)

        train.reset()
        test.reset()
        println()

        println(eval.stats())

        //this is strictly loss/cost, so we negate the r^2 value
        -eval.rSquared(0)
    }

  val result = hs.apply(NauticalConfigurationSpace(), f, R = 10)

  val resultSyn = Await.result(result, Duration.Inf)

  val of = new java.io.FileWriter("bench.csv")
  resultSyn
    .filter(_.cost.isDefined)
    .sortBy(a => -a.cost.getOrElse(Double.MaxValue))
    .foreach { result =>
      println(result)
      of.write(
        result.configID + "," + result.cost.get + "," + result.budget + "\n"
      )
      of.flush()
    }

}
