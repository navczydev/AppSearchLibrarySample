package com.example.appsearchlibrarysample

import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appsearch.app.*
import androidx.appsearch.exceptions.AppSearchException
import androidx.appsearch.localstorage.LocalStorage
import androidx.core.content.ContextCompat
import com.example.appsearchlibrarysample.document.Note
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor

@Suppress("UnstableApiUsage")
class MainActivity : AppCompatActivity() {
    lateinit var mExecutor: Executor
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context: Context = applicationContext
        mExecutor = ContextCompat.getMainExecutor(this@MainActivity)
        setContentView(R.layout.activity_main)

        val sessionFuture = LocalStorage.createSearchSession(
            LocalStorage.SearchContext.Builder(context, "notes_app")
                .build()
        )

        val setSchemaRequest =
            SetSchemaRequest.Builder().addDocumentClasses(Note::class.java)
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
        val note = Note(
            namespace = "user1",
            id = "noteID",
            score = 10,
            text = "Buy fresh fruit"
        )

        setSchemaFuture.addListener(
            {
                Log.e(TAG, "onCreate: SCHEMA SET_UP DONE, Insert NOTE")
                // putNotes(sessionFuture, note)
                searchNotes(sessionFuture)
            },
            mExecutor
        )

        // putNotes(sessionFuture, note)

        // TODO remove
        /*  val removeRequest = RemoveByDocumentIdRequest.Builder("user1")
              .addIds("noteID")
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

        // searchNotes(sessionFuture)
    }

    private fun putNotes(sessionFuture: ListenableFuture<AppSearchSession>, note: Note) {
        Log.e(TAG, "putNotes: ${sessionFuture.isDone}")

        val putRequest = PutDocumentsRequest.Builder().addDocuments(note).build()
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
                    searchNotes(sessionFuture)
                }

                override fun onFailure(t: Throwable) {
                    Log.e(TAG, "Failed to put documents.", t)
                }
            },
            mExecutor
        )
    }

    private fun searchNotes(sessionFuture: ListenableFuture<AppSearchSession>) {
        Log.e(TAG, "searchNotes: ${sessionFuture.isDone}")
        // search
        val searchSpec = SearchSpec.Builder()
            .addFilterNamespaces("user1")
            .build()

        val searchFuture = Futures.transform(
            sessionFuture,
            { session ->
                session?.search("fruit", searchSpec)
            },
            mExecutor
        )
        Futures.addCallback(
            searchFuture,
            object : FutureCallback<SearchResults> {
                override fun onSuccess(searchResults: SearchResults?) {
                    Futures.addCallback(
                        iterateSearchResults(searchResults),
                        object : FutureCallback<Note> {
                            override fun onSuccess(result: Note?) {
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
                    Log.e(TAG, "Failed to search notes in AppSearch.", t)
                }
            },
            mExecutor
        )
    }

    fun iterateSearchResults(searchResults: SearchResults?): ListenableFuture<Note> =
        Futures.transform(
            searchResults?.nextPage!!,
            { page: List<SearchResult>? ->
                // Gets GenericDocument from SearchResult.
                val genericDocument: GenericDocument = page!![0].genericDocument
                val schemaType = genericDocument.schemaType
                val note: Note? = try {
                    if (schemaType == "Note") {
                        Log.e(TAG, "iterateSearchResults: Schema NOTE exist")
                        Log.e(TAG, "iterateSearchResults: ${page.size}")
                        // Converts GenericDocument object to Note object.
                        genericDocument.toDocumentClass(Note::class.java)
                    } else null
                } catch (e: AppSearchException) {
                    Log.e(
                        TAG,
                        "Failed to convert GenericDocument to Note",
                        e
                    )
                    null
                }
                note
            },
            mExecutor
        )

    companion object {
        val TAG: String = MainActivity::class.java.simpleName
    }
}
