package cz.uhk.macroflow.data

import androidx.room.*
import cz.uhk.macroflow.data.UserProfileEntity

@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun getProfileSync(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveProfile(profile: UserProfileEntity)

}