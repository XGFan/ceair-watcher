package com.test4x.app.ceair

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.test4x.app.ceair.bean.Task
import org.asynchttpclient.*

class CeairSearch {

    val jackson = ObjectMapper().findAndRegisterModules().configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true)
            .configure(JsonParser.Feature.IGNORE_UNDEFINED, true)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val asyncHttpClient: AsyncHttpClient

    init {
        val asyncHttpClientConfig = DefaultAsyncHttpClientConfig.Builder()
                .setConnectTimeout(60 * 1000)
                .setMaxRedirects(5)
                .build()
        asyncHttpClient = DefaultAsyncHttpClient(asyncHttpClientConfig)
    }

    fun getSearchList(page: Int = 1, preTask: List<Task> = emptyList()): List<Task> {
        val execute = asyncHttpClient.preparePost("https://vacations.ceair.com/api/product/GetSearchProductList")
                .addFormParam("DepartCityID", "3")
                .addFormParam("OrderBy", "RecommendLevel")
                .addFormParam("OrderFlag", "desc")
                .addFormParam("PageIndex", page.toString())
                .addFormParam("PageSize", "20")
                .execute(object : AsyncCompletionHandler<Pair<List<Task>, Boolean>>() {
                    override fun onCompleted(response: Response): Pair<List<Task>, Boolean> {
                        val json = jackson.readTree(response.responseBody.reader())
                        val data = json.get("Data")
                        val taskList = data.get("ProductList")
                                .map { it.get("ID").asText() }
                                .map { Task(it) }
                        return Pair(
                                taskList,
                                data.get("PageInfo").get("IsNextPage").asBoolean()
                        )
                    }
                })
        val (task, flag) = execute.get()
        return if (flag) {
            getSearchList(page + 1, preTask + task)
        } else {
            asyncHttpClient.close()
            preTask + task
        }
    }
}