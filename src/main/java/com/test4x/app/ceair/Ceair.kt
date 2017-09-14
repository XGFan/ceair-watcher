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
            val collection = mongoTemplate.getCollection(Constants.TASK)
            while (collection.count() > 0) {
                val task = mongoTemplate.findOne(Query.query(Criteria.where("done").`is`(false)), Task::class.java, Constants.TASK)
                try {
                    VacationCrawler(task.id, mongoTemplate).run().forEach {
                        mongoTemplate.save(Task(it), "task")
                    }
                    task.done = true
                    mongoTemplate.save(task)
                } catch (e: Exception) {
                    e.printStackTrace()
                    //rollback
                }

            }
        }
    }


}

