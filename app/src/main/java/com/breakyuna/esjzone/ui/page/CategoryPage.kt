package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.withContext
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.network.features.listNovels
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.novellibrary.novel.CategoryNovel
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.preview
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.LoadError
import com.breakyuna.esjzone.ui.component.Novel
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

        Column {
            AppBar(
                title = category.name,
                onBack = {
                    navigator?.pop()
                }
            )

            when (state) {
                is CategoryPageModel.State.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is CategoryPageModel.State.Error -> LoadError(
                    onRetry = categoryPageModel::retry
                )

                is CategoryPageModel.State.Result -> {
                    val result = state as CategoryPageModel.State.Result

                    val novels = result.categoryNovels.distinctBy { it.url.ifBlank { it.name } }

                    val cache = remember {
                        mutableStateMapOf<String, DetailedNovel>()
                    }

                    val adult by remember {
                        GlobalSettings.adult
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            novels,
                            key = { item -> item.url.ifBlank { item.name } }
                        ) { categoryNovel ->
                            var detailedNovel: DetailedNovel? by remember {
                                mutableStateOf(cache[categoryNovel.url])
                            }
                            var detailFailed by remember(categoryNovel.url) {
                                mutableStateOf(false)
                            }
                            var detailRetry by remember(categoryNovel.url) {
                                mutableIntStateOf(0)
                            }

                            val novel = detailedNovel
                            if (novel == null) {
                                if (detailFailed) {
                                    LoadError(
                                        onRetry = {
                                            detailFailed = false
                                            detailRetry += 1
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp)
                                    )
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                    }
                                }

                                LaunchedEffect(categoryNovel.url, detailRetry) {
                                    try {
                                        val fetched = withContext(Dispatchers.IO) {
                                            EsjzoneClient.getNovelDetail(
                                                authorization,
                                                categoryNovel
                                            )
                                        }
                                        detailedNovel = fetched
                                        cache[categoryNovel.url] = fetched
                                        detailFailed = false
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        detailFailed = true
                                        com.breakyuna.esjzone.util.AppLogger.e("CategoryPage", "Failed to load novel detail for ${categoryNovel.name}", e)
                                    }
                                }
                            } else {
                                if (adult || !novel.isAdult) {
                                    Novel(
                                        covered = novel,
                                        summary = novel.description.preview()
                                    )
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = stringResource(id = R.string.the_end),
                                    modifier = Modifier.padding(16.dp)
                                )
                                Spacer(modifier = Modifier.weight(1f))
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
        data object Error : State()
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
                mutableState.value = State.Error
                com.breakyuna.esjzone.util.AppLogger.e("CategoryPageModel", "Failed to list novels for category: ${category.name}", e)
            }
        }
    }

    fun retry() {
        loadStarted = false
        getNovels()
    }

}
