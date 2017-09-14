package com.test4x.app.ceair

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.test4x.app.ceair.bean.CeairProduct
import com.test4x.app.ceair.bean.DatePrice
import org.asynchttpclient.*
import org.springframework.data.mongodb.core.BulkOperations
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDateTime

class VacationCrawler(val productId: String, val mongo: MongoTemplate) {

    val jackson = ObjectMapper().findAndRegisterModules().configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
            .configure(JsonParser.Feature.IGNORE_UNDEFINED, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val asyncHttpClient: AsyncHttpClient

    init {
        val builder = DefaultAsyncHttpClientConfig.Builder()
        val asyncHttpClientConfig = builder.build()
        asyncHttpClient = DefaultAsyncHttpClient(asyncHttpClientConfig)
    }


    fun run(): List<String> {
        //获取日程表
        val productSchedule = getProductSchedule()

        val datePriceList = productSchedule.get()

        //获取详情
        val detailFuture = getProductDetail(datePriceList.first().dateId).toCompletableFuture().thenAccept {
            mongo.save(it, "product")
        }

        val bulkOps = mongo.bulkOps(BulkOperations.BulkMode.UNORDERED, "datePrice")
        val getProductRelation = GetProductRelation()
        datePriceList.map {
            getProductPrice(it.dateId)
                    .toCompletableFuture().thenAccept { p ->
                it.price = p
            }
        }.forEach {
            it.get()
        }

        datePriceList.forEach {
            bulkOps.insert(it)
        }
        bulkOps.execute()
        detailFuture.get()
        val productRelation = getProductRelation.get()
        asyncHttpClient.close()
        return productRelation;

    }

    fun getProductSchedule(): ListenableFuture<List<DatePrice>> {
        return asyncHttpClient.preparePost("https://vacations.ceair.com/api/Product/GetProductDatePrice")
                .addFormParam("ProductID", productId)
                .addFormParam("StartDepScheduleDate", "2017-09")
                .addFormParam("EndDepScheduleDate", "2018-12-31")
                .execute(object : AsyncCompletionHandler<List<DatePrice>>() {
                    override fun onCompleted(response: Response): List<DatePrice> {
                        val json = jackson.readTree(response.responseBody.reader())
                        return json.get("Data")
                                .get("PkgProductScheduleList")
                                .map {
                                    val dateId = it.get("ID").asText()
                                    val dateString = it.get("DepartureDate").asText()
                                    val amount = it.get("Quantity").asInt()
                                    val ld = LocalDateTime.parse(dateString).toLocalDate()
                                    DatePrice(productId, dateId, ld, amount)
                                }
                    }

                    override fun onThrowable(t: Throwable?) {
                        //todo 应该抛出重试
                        super.onThrowable(t)
                    }
                })
    }

    fun getProductDetail(scheduleId: String): ListenableFuture<CeairProduct> {
        return asyncHttpClient.prepareGet("https://vacations.ceair.com/api/Product/GetProductResourceItems")
                .addQueryParam("productID", productId)
                .addQueryParam("scheduleID", scheduleId)
                .addQueryParam("adultQuantity", "2")
                .addQueryParam("childrenQuantity", "0")
                .execute(object : AsyncCompletionHandler<CeairProduct>() {
                    override fun onCompleted(response: Response): CeairProduct {
                        val json = jackson.readTree(response.responseBody.reader())
                        val pkgProductInfo = json.get("Data").get("PkgProductInfo")
                        val ceairProduct = jackson.convertValue<CeairProduct>(pkgProductInfo, CeairProduct::class.java)
                        val days = pkgProductInfo.get("PkgProduct_Segment").map {
                            it.get("StayDays").asInt()
                        }.sum()
                        ceairProduct.Days = days
                        return ceairProduct
                    }

                    override fun onThrowable(t: Throwable?) {
                        //todo 应该抛出重试
                        super.onThrowable(t)
                    }
                })
    }

    fun getProductPrice(scheduleId: String, adult: Int = 2, child: Int = 0): ListenableFuture<Int> {
        return asyncHttpClient.prepareGet("https://vacations.ceair.com/api/Product/GetProductResourceItems")
                .addQueryParam("productID", productId)
                .addQueryParam("scheduleID", scheduleId)
                .addQueryParam("adultQuantity", adult.toString())
                .addQueryParam("childrenQuantity", child.toString())
                .execute(object : AsyncCompletionHandler<Int>() {
                    override fun onCompleted(response: Response): Int {
                        val json = jackson.readTree(response.responseBody.reader())
                        return json.get("Data")
                                .get("ProductDefultPriceList")
                                .first()
                                .get("TotalAmount")
                                .asInt()
                    }

                    override fun onThrowable(t: Throwable?) {
                        //todo 应该抛出重试
                        super.onThrowable(t)
                    }
                })


    }

    fun GetProductRelation(): ListenableFuture<List<String>> {
        return asyncHttpClient.preparePost("https://vacations.ceair.com/api/Product/GetProductRelation")
                .addFormParam("ProductID", productId)
                .execute(object : AsyncCompletionHandler<List<String>>() {
                    override fun onCompleted(response: Response): List<String> {
                        val json = jackson.readTree(response.responseBody.reader())
                        val data = json.get("Data")
                        val list1 = data
                                .get("RouteRelationList")
                                .map {
                                    it.get("RelationProductID").asText()
                                }
                        val list2 = data.get("DepRelationList")
                                .map {
                                    it.get("RelationProductID").asText()
                                }
                        return list1 + list2
                    }
                })
    }


}