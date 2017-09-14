package com.test4x.app.ceair

import com.test4x.app.ceair.bean.Task
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query


@SpringBootApplication
open class Ceair {

    companion object {

        @JvmStatic
        fun main(args: Array<String>) {
            val run = SpringApplication.run(Ceair::class.java, *args)
            val mongoTemplate = run.getBean(MongoTemplate::class.java)
            val searchList = CeairSearch().getSearchList()
            searchList.forEach {
                mongoTemplate.save(it, Constants.TASK)
            }
            println("Get ${searchList.size} items")
            while (mongoTemplate.count(Query.query(Criteria.where("done").`is`(0)), Constants.TASK) > 0) {
                val task = mongoTemplate.findOne(Query.query(Criteria.where("done").`is`(0)), Task::class.java, Constants.TASK)
                try {
                    VacationCrawler(task.id, mongoTemplate).run()
                    task.done = 1
                    println("${task.id} Done")
                    mongoTemplate.save(task)
                } catch (e: Exception) {
                    task.done = -1
                    println("${task.id} Fail ${e.message}")
                    mongoTemplate.save(task)
                    //rollback
                }

            }
        }
    }


}

