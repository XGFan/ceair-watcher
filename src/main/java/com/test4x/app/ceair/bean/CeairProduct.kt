package com.test4x.app.ceair.bean

import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document

@Document
class CeairProduct(@Id val ID: Int,
                   val ProductCode: String,
                   val ProductName: String,
                   val ProductSubName: String,
                   val DepartureTravelCityID: String,
                   val DepartureCityName: String,
                   val DestinationTravelCityID: String,
                   val DestinationCityName: String
) {
    var Days = 0
}