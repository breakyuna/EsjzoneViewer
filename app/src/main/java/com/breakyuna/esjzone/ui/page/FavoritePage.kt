package com.breakyuna.esjzone.ui.page

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.screen.Screen
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
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
import com.breakyuna.esjzone.network.PageableRequester
import com.breakyuna.esjzone.network.features.getFavorites
import com.breakyuna.esjzone.network.features.getNovelDetail
import com.breakyuna.esjzone.novellibrary.novel.DetailedNovel
import com.breakyuna.esjzone.novellibrary.novel.FavoriteNovel
import com.breakyuna.esjzone.ui.component.AppBar
import com.breakyuna.esjzone.ui.component.DropdownSelection
import com.breakyuna.esjzone.ui.component.Loading
import com.breakyuna.esjzone.ui.component.Novel
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.BooleanStateHolder
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent

private fun favoriteSortResource(name: String): Int {
    return when (name) {
        "new" -> R.string.favorites_recently_added
        "udate" -> R.string.favorites_recently_update
        else -> R.string.favorites_recently_added
    }
}

object FavoritePage : Screen {

    private fun readResolve(): Any = FavoritePage

    @Composable
    override fun Content() {
        Content(showBack = true)
    }

    @Composable
    fun Content(showBack: Boolean) {
        val navigator = LocalBaseNavigator.current

        val authorization = LocalAuthorization.current

        val scope = rememberCoroutineScope()

        val sort = remember {
            mutableStateOf("new")
        }

        val favoritePageModel =
            rememberScreenModel { FavoritePageModel(authorization, scope, sort) }
        val state by favoritePageModel.state.collectAsState()

        val adult by remember {
            GlobalSettings.adult
        }

        fun onSortChanged(value: String) {
            sort.value = value
            favoritePageModel.getRequester()
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (showBack) {
                AppBar(
                    title = stringResource(id = R.string.favorites),
                    onBack = {
                        navigator?.pop()
                    }
                ) {
                    Row {
                        FavoriteSortSelector(
                            sort = sort,
                            onChange = ::onSortChanged
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.favorites),
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = stringResource(id = R.string.favorites_description),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    FavoriteSortSelector(
                        sort = sort,
                        onChange = ::onSortChanged
                    )
                }
            }

            when (state) {
                is FavoritePageModel.State.Loading -> Loading()

                is FavoritePageModel.State.Result -> {
                    val result = (state as FavoritePageModel.State.Result)
                    val requester = result.requester

                    var current by remember(result) {
                        mutableIntStateOf(2)
                    }

                    val max = requester.pages()

                    val items = remember(result) {
                        mutableStateListOf<FavoriteNovel>().apply {
                            addAll(result.firstPage)
                        }
                    }

                    val cache = remember {
                        mutableStateMapOf<String, DetailedNovel>()
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        items(
                            items = items.distinctBy { it.url.ifBlank { it.name } },
                            key = { item -> item.url.ifBlank { item.name } }
                        ) { favoriteNovel ->
                            var detailedNovel: DetailedNovel? by remember {
                                mutableStateOf(cache[favoriteNovel.url])
                            }

                            val novel = detailedNovel
                            if (novel == null) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                                }

                                LaunchedEffect(favoriteNovel.url) {
                                    try {
                                        val fetched = withContext(Dispatchers.IO) {
                                            EsjzoneClient.getNovelDetail(
                                                authorization,
                                                favoriteNovel
                                            )
                                        }
                                        detailedNovel = fetched
                                        cache[favoriteNovel.url] = fetched
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        com.breakyuna.esjzone.util.AppLogger.e("FavoritePage", "Failed to load novel detail for ${favoriteNovel.name}", e)
                                    }
                                }
                            } else {
                                if (adult || !novel.isAdult) {
                                    val favorite = rememberSaveable {
                                        mutableStateOf(true)
                                    }

                                    val rememberedFavorite by rememberSaveable {
                                        favorite
                                    }

                                    if (rememberedFavorite && novel.isFavorite) {
                                        Novel(covered = novel) {
                                            navigator?.pushIfNotCurrent(
                                                NovelPage(
                                                    favoriteNovel,
                                                    favorite = BooleanStateHolder(favorite)
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            if (current <= max && max > 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator()
                                }
                                LaunchedEffect(current) {
                                    try {
                                        val newlyLoaded = withContext(Dispatchers.IO) {
                                            requester.more(current)
                                        }
                                        for (item in newlyLoaded) {
                                            if (items.contains(item))
                                                continue
                                            items.add(item)
                                        }
                                        current += 1
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        com.breakyuna.esjzone.util.AppLogger.e(
                                            "FavoritePage",
                                            "Failed to load favorite page $current",
                                            e
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
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
            favoritePageModel.getRequester()
        }
    }

}

@Composable
private fun FavoriteSortSelector(
    sort: MutableState<String>,
    onChange: (String) -> Unit
) {
    var exposed by remember { mutableStateOf(false) }
    DropdownSelection(
        label = stringResource(id = R.string.novel_list_sort),
        items = listOf("new", "udate"),
        current = sort.value,
        onChange = onChange,
        exposed = exposed,
        onExposeChanged = { exposed = it },
        modifier = Modifier.width(140.dp),
        nameProvider = {
            stringResource(id = favoriteSortResource(this))
        }
    )
}

class FavoritePageModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope,
    private val sort: MutableState<String>
) : StateScreenModel<FavoritePageModel.State>(State.Loading) {

    private var requestJob: Job? = null

    sealed class State {
        data object Loading : State()
        data class Result(
            val requester: PageableRequester<FavoriteNovel>,
            val firstPage: List<FavoriteNovel>
        ) : State()
    }

    fun getRequester() {
        requestJob?.cancel()
        requestJob = scope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val (requester, novels) = EsjzoneClient.getFavorites(
                    authorization,
                    sort.value
                )
                ensureActive()
                mutableState.value = State.Result(requester, novels)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e("FavoritePageModel", "Failed to load favorites", e)
            }
        }
    }

}
