package com.koala.ch07

import org.apache.log4j.{Level, Logger}
import org.apache.spark.ml.recommendation.ALSModel
import org.apache.spark.mllib.recommendation.EnhancedMatrixFactorizationModel
import org.apache.spark.sql.SparkSession

/**
  * Created by seawalker on 2017/1/7.
  */
object ExploreModel {
  def main(args: Array[String]): Unit = {
    Logger.getLogger("org.apache.spark").setLevel(Level.ERROR)
    /*
D:\Projects\GitHub\book2\rocket\data\ch07\wh
local[4]
D:\Projects\GitHub\book2\rocket\data\ch07\model
D:\Projects\GitHub\book2\rocket\data\ch07\ratings.dat
D:\Projects\GitHub\book2\rocket\data\ch07\movies.dat
     */
    val Array(wh, mode, modelPath, ratingsPath, moviesPath) = args
    val spark = SparkSession.builder
      .config("spark.sql.warehouse.dir", wh)
      .master(mode)
      .appName(this.getClass.getName)
      .getOrCreate()

    import spark.implicits._

    val grayGuy = spark.read.textFile(ratingsPath).map(parseRating).toDF()
    val model = ALSModel.load(modelPath)
    model.transform(grayGuy).show()

    //在模型的metadata/part-0000
    val rank = 16
    val userFactors = model.userFactors.map {
      case row => (row.getInt(0),  row.getSeq[Float](1).map(_.toDouble).toArray)
    }.rdd.filter(_._1 == 44670)


    val itemFactors = model.itemFactors.map {
      case row => (row.getInt(0),  row.getSeq[Float](1).map(_.toDouble).toArray)
    }.rdd


    val recommend = EnhancedMatrixFactorizationModel.recommendTForS(rank, userFactors, itemFactors, 100)
      .flatMap(_._2.toSeq)

    val movies = spark.sparkContext.textFile(moviesPath).map(_.split("~")).map(x => (x(0).toInt, x(1)))

    val haveSeen = grayGuy.map(_.getInt(1)).collect().toSeq

    recommend.join(movies)
      .map {
        case (docId, (prediction, title))  => (docId, haveSeen.contains(docId), prediction, title)
      }.foreach(println)

    spark.stop()
  }

  def parseRating(str: String): Rating = {
    val fields = str.split(",")
    assert(fields.size == 3)
    Rating(fields(0).toInt, fields(1).toInt, fields(2).toFloat)
  }
}
