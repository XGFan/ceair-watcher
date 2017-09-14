package com.test4x.app.ceair.bean

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
class Task(@Id val id: String) {
    var done = false
}