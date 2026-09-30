@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.breakyuna.esjzone.ui.page

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.breakyuna.esjzone.R
import com.breakyuna.esjzone.ui.designsystem.GlobalText as Text
import com.breakyuna.esjzone.ui.designsystem.globalStringResource as stringResource
import com.breakyuna.esjzone.ui.navigation.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal data class OpenSourceLicense(val path: String, val title: String, val text: String)

internal class OpenSourceLicensesModel(context: Context) :
    AppStateViewModel<List<OpenSourceLicense>?>(null) {
    init {
        val assets = context.applicationContext.assets
        viewModelScope.launch(Dispatchers.IO) {
            mutableState.value = listOf("open_source_licenses", "font_licenses").flatMap { directory ->
                assets.list(directory).orEmpty().sorted().map { name ->
                    val path = "$directory/$name"
                    val text = assets.open(path).bufferedReader().use { it.readText() }
                    OpenSourceLicense(path, when (name) {
                        "source_han_serif.txt" -> "Source Han Serif"
                        "source_han_sans.txt" -> "Source Han Sans"
                        "lxgw_wenkai.txt" -> "LXGW WenKai"
                        else -> text.lineSequence().first().removePrefix("# ")
                    }, text)
                }
            }
        }
    }
}

object OpenSourceLicensesPage : AppDestination {
    override val key = "OpenSourceLicensesPage"
    private fun readResolve(): Any = OpenSourceLicensesPage

    @Composable
    override fun Content() {
        val navigator = LocalBaseNavigator.current
        val context = LocalContext.current
        val model = rememberAppViewModel { OpenSourceLicensesModel(context) }
        val licenses by model.state.collectAsStateWithLifecycle()
        var expandedPath by rememberSaveable { mutableStateOf<String?>(null) }
        Scaffold(topBar = {
            TopAppBar(title = { Text(stringResource(R.string.open_source_licenses)) }, navigationIcon = {
                IconButton(onClick = { navigator?.pop() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.reading_stats_back))
                }
            })
        }) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp)) {
                if (licenses == null) item { CircularProgressIndicator() }
                items(licenses.orEmpty(), key = OpenSourceLicense::path) { license ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            license.title, style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth().clickable {
                                expandedPath = if (expandedPath == license.path) null else license.path
                            }.padding(vertical = 16.dp)
                        )
                        if (expandedPath == license.path) SelectionContainer {
                            Text(license.text, style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(bottom = 16.dp))
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
