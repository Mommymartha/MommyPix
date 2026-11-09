package com.project.mommypix.data.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface Rule34Api {

    @GET
    suspend fun getGelbooruPosts(
        @Url url: String,
        @Query("page") page: String = "dapi",
        @Query("s") s: String = "post",
        @Query("q") q: String = "index",
        @Query("json") json: Int = 1,
        @Query("limit") limit: Int = 10,
        @Query("tags") tags: String,
        @Query("api_key") apiKey: String? = null,
        @Query("user_id") userId: String? = null
    ): Response<ResponseBody>

    @GET
    suspend fun getMoebooruPosts(
        @Url url: String,
        @Query("limit") limit: Int = 10,
        @Query("tags") tags: String
    ): Response<ResponseBody>
}
