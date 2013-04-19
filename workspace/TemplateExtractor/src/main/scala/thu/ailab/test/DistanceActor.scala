package thu.ailab.test

import akka.actor._
import akka.routing.RoundRobinRouter
import akka.util.Duration
import akka.util.duration._
import akka.event.Logging
import thu.ailab.tree.TagSeqFactory
import thu.ailab.config._
import thu.ailab.utils.Point
import thu.ailab.distance.LCSArray
import thu.ailab.tree.TreeNode
import thu.ailab.global.AppEntry
import thu.ailab.utils.Tools.timeIt

object TextSimilarities extends AppEntry {
  object TriangleType extends Enumeration {
    type TriangleType = Value
    val DOWN, UP = Value
  }
  import TriangleType._
  sealed trait Message
  case object StartCalculation extends Message
  case class AreaSplit(p1: Point, p2: Point) extends Message
  case class AreaFinished() extends Message
  case class AllFinished(duration: Duration) extends Message
  
  val factory = new TagSeqFactory(MyConfigFactory.getConfString("document.blogdir"))

  val algoRunner = new LCSArray[TreeNode]
      
  val distArray = new Array[Double](factory.size * (factory.size - 1) / 2)
  
  class Worker extends Actor {
    def receive = {
      case AreaSplit(p1, p2) =>
        val (count, duration) = timeIt(calculateArea(p1, p2))
        logger.info("Time spent: %f at %d".format(duration, count))
        sender ! AreaFinished
    }
    def calculateArea(p1: Point, p2: Point) = {
      var acc = 0 
      for (i <- p1.x + 1 to p2.x; j <- p1.y until math.min(p2.y, i)) {
        distArray((i - 1) * i / 2 + j) = algoRunner.run(factory.getInstance(i), factory.getInstance(j))
        acc += 1
      }
      acc
    }
  }
  class Master(nrOfWorkers: Int,
      pieceLength: Int,
      listener: ActorRef) extends Actor {
    val start = System.currentTimeMillis()
    val workerRouter = context.actorOf(
      Props[Worker].withRouter(RoundRobinRouter(nrOfWorkers)), name = "workRouter")
    val nrOfHSplit = (factory.size - 2) / pieceLength + 1
    val nrOfMessages = (nrOfHSplit + 1) * nrOfHSplit / 2
    logger.info("Total area count: %d".format(nrOfMessages))
    var finishedCount = 0
    def receive = {
      case StartCalculation =>
        var acc = 1
        for (i <- 0 until nrOfHSplit; j <- 0 to i) {
          logger.info("Start %d worker".format(acc))
          acc += 1
          workerRouter ! AreaSplit(Point(i * pieceLength, j * pieceLength), 
              Point(math.min((i + 1) * pieceLength, factory.size - 1),
                  math.min((j + 1) * pieceLength, factory.size - 1)))
        }
      case AreaFinished => 
        finishedCount += 1
        logger.info("FinishedCount: %d".format(finishedCount))
        if (finishedCount == nrOfMessages) {
          listener ! AllFinished((System.currentTimeMillis() - start).millis)
          context.stop(self)
        }
    }
  }
  
  class Listener extends Actor {
    def receive = {
      case AllFinished(duration) =>
        writeFile(MyConfigFactory.getConfString("output.distFile"))
        println("Calculation time: %s".format(duration))
        context.system.shutdown
    }
    import thu.ailab.utils.Tools.withPrintWriter
    def writeFile(filename: String) = {
      withPrintWriter(filename) {pw =>
        var idx = 0
        for (i <- 0 until factory.size; j <- 0 until i) {
          pw.println("%f".format(distArray(idx)))
          idx += 1
        }
      }
    }
  }
  
  def calculate(nrOfWorkers: Int, pieceLength: Int) = {
    val system = ActorSystem("CalcSimilarities")
    val listener = system.actorOf(Props[Listener], name = "listener")
    val master = system.actorOf(Props(new Master(
        nrOfWorkers, pieceLength, listener)),
        name = "master")
     master ! StartCalculation 
  }
  
  calculate(nrOfWorkers = MyConfigFactory.getConfInt("actor.nrOfWorkers"), 
      pieceLength = MyConfigFactory.getConfInt("actor.pieceLength"))
}