package com.example.appsearchlibrarysample

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appsearch.app.*
import androidx.appsearch.exceptions.AppSearchException
import androidx.appsearch.localstorage.LocalStorage
import androidx.core.content.ContextCompat
import com.example.appsearchlibrarysample.document.Pet
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

@Suppress("UnstableApiUsage")
class MainActivity : AppCompatActivity() {
    lateinit var mExecutor: Executor
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mExecutor = ContextCompat.getMainExecutor(this@MainActivity)
        setContentView(R.layout.activity_main)

        val context: Context = applicationContext
        val sessionFuture = LocalStorage.createSearchSession(
            LocalStorage.SearchContext.Builder(context, "pets_app")
                .build()
        )

        val setSchemaRequest =
            SetSchemaRequest.Builder().addDocumentClasses(Pet::class.java)
                .build()
        val setSchemaFuture = Futures.transformAsync(
            sessionFuture,
            { session ->
                Log.e(TAG, "onCrete: SESSION CREATED")
                Log.e(TAG, "onCreate: SET SCHEMA")
                session?.setSchema(setSchemaRequest)
            },
            mExecutor
        )
        val pet = Pet(
            namespace = "user1",
            id = "petID",
            score = 10,
            name = "Aishley"
        )

        setSchemaFuture.addListener(
            {
                Log.e(TAG, "onCreate: SCHEMA SET_UP DONE, Insert Pet Document")
                putPetDocument(sessionFuture, pet)
            },
            mExecutor
        )

        // TODO remove
        /*  val removeRequest = RemoveByDocumentIdRequest.Builder("user1")
              .addIds("petID")
              .build()
  
          val removeFuture = Futures.transformAsync(
              sessionFuture,
              { session ->
                  session?.remove(removeRequest)
              },
              mExecutor
          )
  
          removeFuture.addListener(
              {
                  Log.e(TAG, "onCreate: Removal done")
              },
              mExecutor
          )*/
    }

    private fun putPetDocument(sessionFuture: ListenableFuture<AppSearchSession>, pet: Pet) {
        Log.e(TAG, "putPet: ${sessionFuture.isDone}")

        val putRequest = PutDocumentsRequest.Builder().addDocuments(pet).build()
        val putFuture = Futures.transformAsync(
            sessionFuture,
            { session ->
                session?.put(putRequest)
            },
            mExecutor
        )

        Futures.addCallback(
            putFuture,
            object : FutureCallback<AppSearchBatchResult<String, Void>?> {
                override fun onSuccess(result: AppSearchBatchResult<String, Void>?) {
                    // Gets map of successful results from Id to Void
                    val successfulResults = result?.successes
                    Log.e(TAG, "onSuccess: INSERT ${successfulResults?.size}")

                    successfulResults?.forEach {
                        Log.e(TAG, "onSuccess: INSERT ${it.key} - ${it.value}")
                    }
                    // Gets map of failed results from Id to AppSearchResult
                    val failedResults = result?.failures
                    Log.e(TAG, "onSuccessFailures: INSERT $failedResults")
                    searchPet(sessionFuture)
                }

                override fun onFailure(t: Throwable) {
                    Log.e(TAG, "Failed to put documents.", t)
                }
            },
            mExecutor
        )
    }

    private fun searchPet(sessionFuture: ListenableFuture<AppSearchSession>) {
        Log.e(TAG, "searchPet: ${sessionFuture.isDone}")
        // search
        val searchSpec = SearchSpec.Builder()
            .addFilterNamespaces("user1")
            .build()

        val searchFuture = Futures.transform(
            sessionFuture,
            { session ->
                session?.search("Aishley", searchSpec)
            },
            mExecutor
        )
        Futures.addCallback(
            searchFuture,
            object : FutureCallback<SearchResults> {
                override fun onSuccess(searchResults: SearchResults?) {
                    Futures.addCallback(
                        iterateSearchResults(searchResults),
                        object : FutureCallback<Pet> {
                            override fun onSuccess(result: Pet?) {
                                Log.e(TAG, "onSuccess $result")
                            }

                            override fun onFailure(t: Throwable) {
                                Log.e(TAG, "onFailure")
                            }
                        },
                        mExecutor
                    )
                }

                override fun onFailure(t: Throwable?) {
                    Log.e(TAG, "Failed to search pets in AppSearch.", t)
                }
            },
            mExecutor
        )
    }

    fun iterateSearchResults(searchResults: SearchResults?): ListenableFuture<Pet> =
        Futures.transform(
            searchResults?.nextPage!!,
            { page: List<SearchResult>? ->
                // Gets GenericDocument from SearchResult.
                val genericDocument: GenericDocument = page!![0].genericDocument
                println(genericDocument)
                Log.e(TAG, "iterateSearchResults: \n $genericDocument")
                val schemaType = genericDocument.schemaType
                val pet: Pet? = try {
                    if (schemaType == "Pet") {
                        Log.e(TAG, "iterateSearchResults: Schema Pet exist")
                        Log.e(TAG, "iterateSearchResults: ${page.size}")
                        // Converts GenericDocument object to Pet object.
                        genericDocument.toDocumentClass(Pet::class.java)
                    } else null
                } catch (e: AppSearchException) {
                    Log.e(
                        TAG,
                        "Failed to convert GenericDocument to Pet",
                        e
                    )
                    null
                }
                Log.e(TAG, "iterateSearchResults: $pet")
                pet
            },
            mExecutor
        )

    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }
}
