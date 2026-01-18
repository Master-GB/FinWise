package com.example.finwise_lab.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.finwise_lab.CurrencyDetails

@Dao
interface CurrencyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCurrency(currency: CurrencyDetails)

    @Query("SELECT * FROM CurrencyDetails LIMIT 1")
    suspend fun getCurrency(): CurrencyDetails?

    @Query("DELETE FROM CurrencyDetails")
    suspend fun clearCurrency()
}
