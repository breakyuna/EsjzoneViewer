package com.breakyuna.esjzone.ui.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.currentCompositeKeyHash
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.tab.Tab
import cafe.adriel.voyager.navigator.tab.TabOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.breakyuna.esjzone.GlobalSettings
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.network.Authorization
import com.breakyuna.esjzone.network.EsjzoneClient
import com.breakyuna.esjzone.network.LocalAuthorization
import com.breakyuna.esjzone.network.features.getCategories
import com.breakyuna.esjzone.novellibrary.novel.Category
import com.breakyuna.esjzone.ui.navigation.LocalBaseNavigator
import com.breakyuna.esjzone.ui.page.CategoryPage

@Composable
private fun MirroredIcon(index: Int, modifier: Modifier = Modifier) {
    when (index % 5) {
        0 -> Icon(imageVector = Icons.Filled.Refresh, contentDescription = "", modifier = modifier, tint = MaterialTheme.colorScheme.primary)
        1 -> Icon(painter = painterResource(id = R.drawable.outline_swords_24), contentDescription = "", modifier = modifier, tint = MaterialTheme.colorScheme.secondary)
        2 -> Icon(imageVector = Icons.Filled.Favorite, contentDescription = "", modifier = modifier, tint = MaterialTheme.colorScheme.tertiary)
        3 -> Icon(imageVector = Icons.Filled.Edit, contentDescription = "", modifier = modifier, tint = MaterialTheme.colorScheme.primary)
        4 -> Icon(imageVector = Icons.Filled.LocalFireDepartment, contentDescription = "", modifier = modifier, tint = MaterialTheme.colorScheme.error)
        else -> Icon(imageVector = Icons.Filled.Category, contentDescription = "", modifier = modifier, tint = MaterialTheme.colorScheme.primary)
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
        val navigator = LocalBaseNavigator.current
        val authorization = LocalAuthorization.current
        val scope = rememberCoroutineScope()

        val categoryModel = rememberScreenModel {
            CategoryModel(
                authorization,
                scope
            )
        }
        val state by categoryModel.state.collectAsState()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Text(
                    text = stringResource(id = R.string.categories),
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Text(
                    text = "Browse stories by genres and themes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            when (state) {
                is CategoryModel.State.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 2.5.dp)
                }

                is CategoryModel.State.Result -> {
                    val result = state as CategoryModel.State.Result

                    val adult by remember {
                        GlobalSettings.adult
                    }

                    val filteredList =
                        result.categories.filter { !(it.isAdult && !adult) }

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredList) { index, category ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1.15f)
                                    .clickable {
                                        navigator?.push(CategoryPage(category))
                                    },
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                elevation = CardDefaults.cardElevation(
                                    defaultElevation = 1.dp,
                                    pressedElevation = 3.dp
                                )
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(54.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surface),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        MirroredIcon(
                                            index = index,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    Text(
                                        text = category.name,
                                        style = MaterialTheme.typography.titleMedium.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        LaunchedEffect(currentCompositeKeyHash) {
            categoryModel.getCategories()
        }
    }

}

class CategoryModel(
    private val authorization: Authorization,
    private val scope: CoroutineScope
) : StateScreenModel<CategoryModel.State>(State.Loading) {

    sealed class State {
        data object Loading : State()
        data class Result(
            val categories: List<Category>
        ) : State()
    }

    fun getCategories() {
        scope.launch(Dispatchers.IO) {
            mutableState.value = State.Loading
            try {
                val categories = EsjzoneClient.getCategories(authorization)
                mutableState.value = State.Result(categories)
            } catch (e: Exception) {
                com.breakyuna.esjzone.util.AppLogger.e("CategoryModel", "Failed to load categories", e)
            }
        }
    }

}
