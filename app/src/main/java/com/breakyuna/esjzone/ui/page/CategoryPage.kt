package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category as CategoryIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.listNovels
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.novellibrary.novel.preview
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.LoadError
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.QuietNovelListItem
import com.breakyuna.esjzone.ui.component.QuietSectionHeader
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator

class CategoryPage(private val category: Category) : Screen {

    override val key: ScreenKey =
        "CategoryPage:" + category.url.trim().ifBlank { category.name.trim() }

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current

        val authorization = LocalAuthorization.current

        val categoryPageModel =
            rememberScreenModel { CategoryPageModel(authorization, category) }
        val state by categoryPageModel.state.collectAsState()

        Column(modifier = Modifier.fillMaxSize()) {
            AppBar(
                title = category.name,
                onBack = {
                    navigator?.pop()
                }
            )

            when (state) {
                is CategoryPageModel.State.Loading -> QuietLoadingState(modifier = Modifier.fillMaxSize())

                is CategoryPageModel.State.Error -> QuietErrorState(
                    onRetry = categoryPageModel::retry,
                    failure = (state as CategoryPageModel.State.Error).failure
                )

                is CategoryPageModel.State.Result -> {
                    val result = state as CategoryPageModel.State.Result

                    val novels = result.categoryNovels.distinctBy { it.url.ifBlank { it.name } }

                    val detailLoader = rememberScreenModel { NovelDetailLoader(authorization) }

                    val adult by remember {
                        GlobalSettings.adult
                    }

                    val unresolvedDetails = novels.count { novel ->
                        val key = detailLoader.key(novel)
                        key !in detailLoader.details && key !in detailLoader.failures
                    }
                    val visibleNovels = novels.mapNotNull { novel ->
                        detailLoader.details[detailLoader.key(novel)]
                    }.filter { adult || !it.isAdult }
                    val renderNovels = novels.filter { novel ->
                        val key = detailLoader.key(novel)
                        val detail = detailLoader.details[key]
                        detail == null || detailLoader.failures.containsKey(key) || adult || !detail.isAdult
                    }
                    val hasDetailFailures = novels.any { novel ->
                        detailLoader.failures.containsKey(detailLoader.key(novel))
                    }
                    val detailsResolved = unresolvedDetails == 0

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        item {
                            QuietSectionHeader(
                                title = category.name,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )
                        }
                        if (novels.isEmpty()) {
                            item {
                                QuietEmptyState(
                                    title = stringResource(R.string.search_no_results),
                                    message = stringResource(R.string.home_collection_empty_message),
                                    icon = Icons.Filled.CategoryIcon
                                )
                            }
                        }
                        if (novels.isNotEmpty() && detailsResolved && visibleNovels.isEmpty() && !hasDetailFailures) {
                            item(key = "category-filtered-empty") {
                                QuietEmptyState(
                                    title = stringResource(R.string.search_no_results),
                                    message = stringResource(R.string.home_adult_hidden),
                                    icon = Icons.Filled.CategoryIcon
                                )
                            }
                        }
                        items(
                            renderNovels,
                            key = { item -> "category-novel-${detailLoader.key(item)}" }
                        ) { categoryNovel ->
                            val key = detailLoader.key(categoryNovel)
                            val novel = detailLoader.details[key]
                            if (novel == null) {
                                if (detailLoader.failures[key] != null) {
                                    LoadError(
                                        onRetry = {
                                            detailLoader.retry(categoryNovel)
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        failure = detailLoader.failures[key]
                                            ?: LoadFailureKind.CLIENT
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                    }
                                }

                                LaunchedEffect(key) { detailLoader.load(categoryNovel) }
                            } else {
                                if (adult || !novel.isAdult) {
                                    QuietNovelListItem(
                                        novel = novel,
                                        summary = novel.description.preview()
                                    )
                                }
                            }
                        }

                        if (visibleNovels.isNotEmpty() && detailsResolved) {
                            item(key = "category-novels-end") {
                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Spacer(modifier = Modifier.weight(1f))
                                    Text(text = stringResource(id = R.string.the_end), modifier = Modifier.padding(16.dp))
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(Unit) {
            categoryPageModel.getNovels()
        }
    }

}

class CategoryPageModel(
    private val authorization: Authorization,
    private val category: Category
) : StateScreenModel<CategoryPageModel.State>(State.Loading) {

    private var loadJob: Job? = null
    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val categoryNovels: List<CategoryNovel>) : State()
    }

    fun getNovels() {
        if (loadStarted) return
        loadStarted = true
        loadJob?.cancel()
        loadJob = screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val novels = EsjzoneClient.listNovels(authorization, category)
                ensureActive()
                mutableState.value = State.Result(novels)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = State.Error(e.loadFailureKind())
                loadStarted = false
                com.breakyuna.esjzone.util.AppLogger.e("CategoryPageModel", "Failed to list novels for category: ${category.name}", e)
            }
        }
    }

    fun retry() {
        loadStarted = false
        getNovels()
    }

}
