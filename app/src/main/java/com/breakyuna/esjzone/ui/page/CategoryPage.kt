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
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.Novel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator

class CategoryPage(private val category: Category) : Screen {

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current

        val authorization = LocalAuthorization.current

        val scope = rememberCoroutineScope()

        val categoryPageModel =
            rememberScreenModel { CategoryPageModel(authorization, scope, category) }
        val state by categoryPageModel.state.collectAsState()

        Column {
            AppBar(
                title = category.name,
                onBack = {
                    navigator.pop()
                }
            )

            when (state) {
                is CategoryPageModel.State.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is CategoryPageModel.State.Result -> {
                    val result = state as CategoryPageModel.State.Result

                    val novels = result.categoryNovels

                    val cache = remember {
                        mutableStateMapOf<String, DetailedNovel>()
                    }

                    val adult by remember {
                        GlobalSettings.adult
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(novels) { categoryNovel ->
                            var detailedNovel: DetailedNovel? by remember {
                                mutableStateOf(cache[categoryNovel.url])
                            }

                            val novel = detailedNovel
                            if (novel == null) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                }

                                LaunchedEffect(currentCompositeKeyHash) {
                                    scope.launch(Dispatchers.IO) {
                                        try {
                                            val fetched = EsjzoneClient.getNovelDetail(
                                                authorization,
                                                categoryNovel
                                            )
                                            detailedNovel = fetched
                                            cache[categoryNovel.url] = fetched
                                        } catch (e: Exception) {
                                            com.breakyuna.esjzone.util.AppLogger.e("CategoryPage", "Failed to load novel detail for ${categoryNovel.name}", e)
                                        }
                                    }
                                }
                            } else {
                                if (adult || !novel.isAdult) {
                                    Novel(covered = novel)
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

        LaunchedEffect(currentCompositeKeyHash) {
            categoryPageModel.getNovels()
        }
    }

}

class CategoryPageModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope,
    private val category: Category
) : StateScreenModel<CategoryPageModel.State>(State.Loading) {

    sealed class State {
        data object Loading : State()
        data class Result(val categoryNovels: List<CategoryNovel>) : State()
    }

    fun getNovels() {
        scope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val novels = EsjzoneClient.listNovels(authorization, category)
                mutableState.value = State.Result(novels)
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e("CategoryPageModel", "Failed to list novels for category: ${category.name}", e)
            }
        }
    }

}