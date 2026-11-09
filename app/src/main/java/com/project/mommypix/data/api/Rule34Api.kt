package com.project.mommypix.data.api

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Url

interface Rule34Api {

    @GET("index.php")
    suspend fun getPosts(
        @Query("page") page: String = "dapi",
        @Query("s") s: String = "post",
        @Query("q") q: String = "index",
        @Query("json") json: Int = 1,
        @Query("limit") limit: Int = 1,
        @Query("tags") tags: String = "sort:random",
        @Query("api_key") apiKey: String?,
        @Query("user_id") userId: String?
    ): Response<List<PostDto>>

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
    ): Response<List<PostDto>>

    @GET
    suspend fun getMoebooruPosts(
        @Url url: String,
        @Query("limit") limit: Int = 10,
        @Query("tags") tags: String
    ): Response<List<PostDto>>
}
