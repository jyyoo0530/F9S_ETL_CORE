package f9s.core.query._garbage

import com.mongodb.spark.MongoSpark
import f9s.{appConf, hadoopConf, mongoConf}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{col, collect_list, struct}

case class F9S_DSBD_RTELIST(var spark: SparkSession) {

  val filePath = appConf().dataLake match {
    case "file" => appConf().folderOrigin
    case "hadoop" => hadoopConf.hadoopPath
  }

  def dsbd_rtelist(): Unit = {
    println("////////////////////////////////DSBD RTELIST: JOB STARTED////////////////////////////////////////")
    lazy val FTR_OFER = spark.read.parquet(filePath + "/FTR_OFER")
    lazy val FTR_OFER_RTE = spark.read.parquet(filePath + "/FTR_OFER_RTE")
    lazy val MDM_PORT = spark.read.parquet(filePath + "/MDM_PORT")

    lazy val srcMdmPort = MDM_PORT.select(col("locCd"), col("locNm")).distinct
    lazy val srcOfer = FTR_OFER.select(
      col("OFER_NR").as("offerNumber"),
      col("EMP_NR").as("userId"),
      col("OFER_TP_CD").as("offerTypeCode")
    )
    lazy val srcPol = FTR_OFER_RTE.select(
      col("OFER_NR").as("offerNumber"),
      col("TRDE_LOC_CD").as("polCode"),
      col("TRDE_LOC_TP_CD"),
      col("OFER_REG_SEQ")
    )
      .filter(col("TRDE_LOC_TP_CD") === "02")
      .drop("TRDE_LOC_TP_CD")
      .join(
        srcMdmPort.withColumn("polCode", col("locCd"))
          .withColumn("polName", col("locNm"))
          .drop("locCd", "locNm"), Seq("polCode"), "left"
      )
    lazy val srcPod = FTR_OFER_RTE.select(
      col("OFER_NR").as("offerNumber"),
      col("TRDE_LOC_CD").as("podCode"),
      col("TRDE_LOC_TP_CD"),
      col("OFER_REG_SEQ")
    )
      .filter(col("TRDE_LOC_TP_CD") === "03")
      .drop("TRDE_LOC_TP_CD")
      .join(
        srcMdmPort.withColumn("podCode", col("locCd"))
          .withColumn("podName", col("locNm"))
          .drop("locCd", "locNm"), Seq("podCode"), "left"
      )
    lazy val srcRte = srcPol.join(srcPod, Seq("offerNumber", "OFER_REG_SEQ"), "left").drop("OFER_REG_SEQ")

    lazy val agged1 = srcOfer.join(srcRte, Seq("offerNumber"), "left").drop("offerNumber").distinct
    lazy val F9S_DSBD_RTELIST = agged1.groupBy("userId", "offerTypeCode").agg(collect_list(struct("polCode", "podCode", "polName", "podName")).as("rteList"))

    MongoSpark.save(F9S_DSBD_RTELIST.write
      .option("uri", mongoConf.sparkMongoUri)
      .option("database", "f9s")
      .option("collection", "F9S_DSBD_RTELIST").mode("overwrite"))
    F9S_DSBD_RTELIST.printSchema
    println("/////////////////////////////JOB FINISHED//////////////////////////////")
  }

  def append_dsbd_rtelist(userId: Seq[String]): Unit = {
    println("////////////////////////////////DSBD RTELIST: JOB STARTED////////////////////////////////////////")
    lazy val FTR_OFER = spark.read.parquet(filePath + "/FTR_OFER")
      .filter(col("EMP_NR") isin (userId: _*))
    lazy val offerList = FTR_OFER.select("OFER_NR").rdd.map(r => r(0).asInstanceOf[String].split("\\|").map(_.toString).distinct).collect().flatten.toSeq
    lazy val FTR_OFER_RTE = spark.read.parquet(filePath + "/FTR_OFER_RTE")
      .filter(col("OFER_NR") isin (offerList: _*))
    lazy val MDM_PORT = spark.read.parquet(filePath + "/MDM_PORT")

    lazy val srcMdmPort = MDM_PORT.select(col("locCd"), col("locNm")).distinct
    lazy val srcOfer = FTR_OFER.select(
      col("OFER_NR").as("offerNumber"),
      col("EMP_NR").as("userId"),
      col("OFER_TP_CD").as("offerTypeCode")
    )
    lazy val srcPol = FTR_OFER_RTE.select(
      col("OFER_NR").as("offerNumber"),
      col("TRDE_LOC_CD").as("polCode"),
      col("TRDE_LOC_TP_CD"),
      col("OFER_REG_SEQ")
    )
      .filter(col("TRDE_LOC_TP_CD") === "02")
      .drop("TRDE_LOC_TP_CD")
      .join(
        srcMdmPort.withColumn("polCode", col("locCd"))
          .withColumn("polName", col("locNm"))
          .drop("locCd", "locNm"), Seq("polCode"), "left"
      )
    lazy val srcPod = FTR_OFER_RTE.select(
      col("OFER_NR").as("offerNumber"),
      col("TRDE_LOC_CD").as("podCode"),
      col("TRDE_LOC_TP_CD"),
      col("OFER_REG_SEQ")
    )
      .filter(col("TRDE_LOC_TP_CD") === "03")
      .drop("TRDE_LOC_TP_CD")
      .join(
        srcMdmPort.withColumn("podCode", col("locCd"))
          .withColumn("podName", col("locNm"))
          .drop("locCd", "locNm"), Seq("podCode"), "left"
      )
    lazy val srcRte = srcPol.join(srcPod, Seq("offerNumber", "OFER_REG_SEQ"), "left").drop("OFER_REG_SEQ")

    lazy val agged1 = srcOfer.join(srcRte, Seq("offerNumber"), "left").drop("offerNumber").distinct
    lazy val F9S_DSBD_RTELIST = agged1.groupBy("userId", "offerTypeCode").agg(collect_list(struct("polCode", "podCode", "polName", "podName")).as("rteList"))


    MongoSpark.save(F9S_DSBD_RTELIST.write
      .option("uri", mongoConf.sparkMongoUri)
      .option("database", "f9s")
      .option("collection", "F9S_DSBD_RTELIST").mode("append"))
    F9S_DSBD_RTELIST.printSchema
    println("/////////////////////////////JOB FINISHED//////////////////////////////")
  }
}
