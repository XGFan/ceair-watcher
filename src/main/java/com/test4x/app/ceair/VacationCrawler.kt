package com.test4x.app.ceair

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.test4x.app.ceair.bean.CeairProduct
import com.test4x.app.ceair.bean.DatePrice
import org.asynchttpclient.*
import org.springframework.data.mongodb.core.MongoTemplate
import java.time.LocalDateTime

class VacationCrawler(val productId: String, val mongo: MongoTemplate) {

    val jackson = ObjectMapper().findAndRegisterModules().configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
            .configure(JsonParser.Feature.IGNORE_UNDEFINED, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val asyncHttpClient: AsyncHttpClient

    init {
        val asyncHttpClientConfig = DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(180 * 1000)
                .setReadTimeout(180 * 1000)
                .setRequestTimeout(180 * 1000)
                .setMaxRedirects(5)
                .build()
        asyncHttpClient = DefaultAsyncHttpClient(asyncHttpClientConfig)
    }


    fun run() {
        //获取日程表
        val productSchedule = getProductSchedule()

        val datePriceList = productSchedule.get()

        var ceairProduct: CeairProduct? = null
        //获取详情
        var counter = 0
        val iter = datePriceList.iterator()
        while (ceairProduct == null) {
            try {
                ceairProduct = getProductDetail(iter.next().dateId).get()
            } catch (e: Exception) {
                counter++
                if (counter >= 5) {
                    throw RuntimeException("$productId 这个产品有毒")
                }
            }
        }

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
            mongo.save(it, Constants.DATE_PRICE)
        }
        mongo.save(ceairProduct, Constants.PRODUCT)
        asyncHttpClient.close()
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
                        try {
                            return json.get("Data")
                                    .get("ProductDefultPriceList")
                                    .first()
                                    .get("TotalAmount")
                                    .asInt()
                        } catch (e: Exception) {
                            println(productId + " - " + scheduleId)
                            return -2
                        }

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

    public fun <T> Sequence<T>.batch(n: Int): Sequence<List<T>> {
        return BatchingSequence(this, n)
    }

    private class BatchingSequence<T>(val source: Sequence<T>, val batchSize: Int) : Sequence<List<T>> {
        override fun iterator(): Iterator<List<T>> = object : AbstractIterator<List<T>>() {
            val iterate = if (batchSize > 0) source.iterator() else emptyList<T>().iterator()
            override fun computeNext() {
                if (iterate.hasNext()) setNext(iterate.asSequence().take(batchSize).toList())
                else done()
            }
        }
    }


}