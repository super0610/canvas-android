/*
 * Copyright (C) 2017 - present Instructure, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package instructure.androidblueprint

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.loader.app.LoaderManager
import androidx.loader.content.Loader
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.instructure.canvasapi2.models.CanvasComparable
import com.instructure.pandarecycler.PaginatedScrollListener
import com.instructure.pandarecycler.PaginatedScrollListener.PaginatedScrollCallback
import com.instructure.pandarecycler.util.GroupSortedList

abstract class SyncExpandableFragment<
        GROUP,
        MODEL : CanvasComparable<*>,
        VIEW : SyncExpandableManager<GROUP, MODEL>,
        PRESENTER : SyncExpandablePresenter<GROUP, MODEL, VIEW>,
        HOLDER : RecyclerView.ViewHolder,
        ADAPTER : SyncExpandableRecyclerAdapter<GROUP, MODEL, HOLDER, VIEW>> : Fragment() {

    protected abstract fun onReadySetGo(presenter: PRESENTER)

    protected abstract fun getPresenterFactory(): PresenterFactory<VIEW, PRESENTER>

    protected abstract fun onPresenterPrepared(presenter: PRESENTER)

    protected abstract fun createAdapter(): ADAPTER

    protected open val adapter: ADAPTER by lazy { createAdapter() }

    protected abstract val recyclerView: RecyclerView

    private fun hitRockBottom() {}

    protected abstract fun perPageCount(): Int

    // boolean flag to avoid delivering the result twice. Calling initLoader in onActivityCreated makes
    // onLoadFinished will be called twice during configuration change.
    private var delivered = false

    lateinit var presenter: PRESENTER
        private set

    @Suppress("DEPRECATION")
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        // LoaderCallbacks as an object, so no hint regarding loader will be leak to the subclasses.
        loaderManager.initLoader(
            LOADER_ID,
            null,
            object : LoaderManager.LoaderCallbacks<PRESENTER> {
                override fun onCreateLoader(
                    id: Int,
                    args: Bundle?
                ): Loader<PRESENTER> {
                    return PresenterLoader(context!!, getPresenterFactory())
                }

                override fun onLoadFinished(
                    loader: Loader<PRESENTER>,
                    presenter: PRESENTER
                ) {
                    if (!delivered) {
                        this@SyncExpandableFragment.presenter = presenter
                        delivered = true
                        onPresenterPrepared(presenter)
                    }
                }

                override fun onLoaderReset(loader: Loader<PRESENTER>) {
                    //this@SyncExpandableFragment.presenter = null
                    onPresenterDestroyed()
                }
            })
    }

    @Suppress("UNCHECKED_CAST")
    override fun onResume() {
        super.onResume()
        onReadySetGo(presenter.onViewAttached(presenterView) as PRESENTER)
    }

    override fun onPause() {
        presenter.onViewDetached()
        super.onPause()
    }

    protected fun onPresenterDestroyed() {
        // hook for subclasses
    }

    open fun withPagination(): Boolean {
        return true
    }

    fun clearAdapter() {
        adapter.clear()
    }

    // Override in case of fragment not implementing Presenter<View> interface
    @Suppress("UNCHECKED_CAST")
    private val presenterView: VIEW
        get() = this as VIEW

    private fun addPagination() {
        if (withPagination()) {
            recyclerView.clearOnScrollListeners()
            recyclerView.addOnScrollListener(
                PaginatedScrollListener(
                    PaginatedScrollCallback { hitRockBottom() },
                    perPageCount()
                )
            )
        }
    }

    protected fun addSwipeToRefresh(swipeRefreshLayout: SwipeRefreshLayout) {
        swipeRefreshLayout.setOnRefreshListener {
            addPagination()
            presenter.refresh(true)
        }
    }

    open val list: GroupSortedList<GROUP, MODEL>
        get() = presenter.data

    companion object {
        private const val LOADER_ID = 1002
    }
}
