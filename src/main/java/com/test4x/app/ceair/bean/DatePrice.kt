package com.test4x.app.ceair.bean

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.time.LocalDate

@Document
data class DatePrice(val productId: String, val dateId: String, val date: LocalDate, val amount: Int) {
    @Id
    var id = productId + ":" + dateId
    var price: Int = -1
}