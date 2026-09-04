package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.core.screen.ScreenKey
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.LoadFailureKind
import com.breakyuna.esjzone.network.loadFailureKind
import com.breakyuna.esjzone.network.features.getCategories
import com.breakyuna.esjzone.novellibrary.novel.Category as NovelCategory
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.navigation.pushIfNotCurrent
import com.breakyuna.esjzone.ui.component.QuietBackHeader
import com.breakyuna.esjzone.ui.component.QuietErrorState
import com.breakyuna.esjzone.ui.component.QuietEmptyState
import com.breakyuna.esjzone.ui.component.QuietLoadingState
import com.breakyuna.esjzone.ui.component.QuietSectionHeader
import com.breakyuna.esjzone.ui.page.CategoryPage

class CategoryBrowserPage : Screen {

    override val key: ScreenKey = "CategoryBrowserPage"

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val categoryModel = rememberScreenModel { CategoryModel(authorization) }
        Column(modifier = Modifier.fillMaxSize()) {
            QuietBackHeader(
                title = stringResource(id = R.string.categories),
                onBack = { navigator?.pop() }
            )
            CategoryBrowserContent(
                categoryModel = categoryModel,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }
    }
}

@Composable
private fun CategoryIcon(index: Int, modifier: Modifier = Modifier, tint: Color) {
    when (index % 5) {
        0 -> Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )

        1 -> Icon(
            painter = painterResource(id = R.drawable.outline_swords_24),
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )

        2 -> Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )

        3 -> Icon(
            imageVector = Icons.Filled.Edit,
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )

        else -> Icon(
            imageVector = Icons.Filled.LocalFireDepartment,
            contentDescription = null,
            modifier = modifier,
            tint = tint
        )
    }
}

@Composable
private fun categoryAccent(index: Int): Pair<Color, Color> {
    val scheme = MaterialTheme.colorScheme
    return when (index % 5) {
        0 -> scheme.primary to scheme.primaryContainer
        1 -> scheme.secondary to scheme.secondaryContainer
        2 -> scheme.tertiary to scheme.tertiaryContainer
        3 -> scheme.primary to scheme.primaryContainer
        else -> scheme.error to scheme.errorContainer
    }
}

object CategoryTab : Tab {

    private fun readResolve(): Any = CategoryTab

    override val options: TabOptions
        @Composable
        get() = TabOptions(
            index = 1u,
            title = stringResource(id = R.string.screen_main_tab_category),
            icon = rememberVectorPainter(image = Icons.Filled.Category)
        )

    @Composable
    override fun Content() {
        val authorization = LocalAuthorization.current
        val categoryModel = rememberScreenModel { CategoryModel(authorization) }
        CategoryBrowserContent(
            categoryModel = categoryModel,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun CategoryBrowserContent(
    categoryModel: CategoryModel,
    modifier: Modifier
) {
    val navigator = LocalBaseNavigator.current
    val state by categoryModel.state.collectAsState()
    val adult by remember { GlobalSettings.adult }

    when (state) {
        is CategoryModel.State.Loading -> QuietLoadingState(modifier = modifier)

        is CategoryModel.State.Error -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center
        ) {
            QuietErrorState(
                failure = (state as CategoryModel.State.Error).failure,
                onRetry = categoryModel::retry
            )
        }

        is CategoryModel.State.Result -> {
            val categories = (state as CategoryModel.State.Result).categories
                .filterNot { it.isAdult && !adult }
            val gridState = rememberLazyGridState()

            if (categories.isEmpty()) {
                QuietEmptyState(
                    title = stringResource(R.string.categories_empty),
                    message = stringResource(R.string.home_adult_hidden),
                    icon = Icons.Filled.Category,
                    modifier = modifier
                )
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(minSize = 156.dp),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = modifier
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            QuietSectionHeader(title = stringResource(R.string.categories))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.categories_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    itemsIndexed(
                        items = categories,
                        key = { _, item -> "category-${item.url.trim().ifBlank { item.name.trim() }}" }
                    ) { index, category ->
                        CategoryCard(
                            category = category,
                            index = index,
                            onClick = { navigator?.pushIfNotCurrent(CategoryPage(category)) }
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        categoryModel.getCategories()
    }
}

@Composable
private fun CategoryCard(
    category: NovelCategory,
    index: Int,
    onClick: () -> Unit
) {
    val (accent, accentContainer) = if (category.isAdult) {
        MaterialTheme.colorScheme.error to MaterialTheme.colorScheme.errorContainer
    } else {
        categoryAccent(index)
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(154.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(accent)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(accentContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        CategoryIcon(
                            index = index,
                            modifier = Modifier.size(23.dp),
                            tint = accent
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = category.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

class CategoryModel(
    private val authorization: Authorization
) : StateScreenModel<CategoryModel.State>(State.Loading) {

    private var loadStarted = false

    sealed class State {
        data object Loading : State()
        data class Error(val failure: LoadFailureKind) : State()
        data class Result(val categories: List<NovelCategory>) : State()
    }

    fun getCategories() {
        if (loadStarted) return
        loadStarted = true
        screenModelScope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val categories = EsjzoneClient.getCategories(authorization)
                ensureActive()
                mutableState.value = State.Result(categories)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                loadStarted = false
                mutableState.value = State.Error(e.loadFailureKind())
                com.breakyuna.esjzone.util.AppLogger.e(
                    "CategoryModel",
                    "Failed to load categories",
                    e
                )
            }
        }
    }

    fun retry() {
        loadStarted = false
        getCategories()
    }
}
