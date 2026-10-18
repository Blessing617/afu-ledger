/*
 * Designed and developed by 2024 skydoves (Jaewoong Eum)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.skydoves.pokedex.compose.feature.home

import android.content.res.Configuration
import androidx.activity.compose.ReportDrawnWhen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.nonInteractiveScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import com.bumptech.glide.integration.compose.placeholder
import com.skydoves.pokedex.compose.R
import com.skydoves.pokedex.compose.core.PokedexFeatureFlags
import com.skydoves.pokedex.compose.core.data.repository.home.FakeHomeRepository
import com.skydoves.pokedex.compose.core.database.entitiy.mapper.getPokemonImageUrlByName
import com.skydoves.pokedex.compose.core.designsystem.component.PokedexAppBar
import com.skydoves.pokedex.compose.core.designsystem.component.PokedexCircularProgress
import com.skydoves.pokedex.compose.core.designsystem.component.pokedexSharedElement
import com.skydoves.pokedex.compose.core.designsystem.theme.PokedexTheme
import com.skydoves.pokedex.compose.core.model.Pokemon
import com.skydoves.pokedex.compose.core.navigation.PokedexScreen
import com.skydoves.pokedex.compose.core.navigation.boundsTransform
import com.skydoves.pokedex.compose.core.navigation.currentComposeNavigator
import com.skydoves.pokedex.compose.core.preview.PokedexPreviewTheme
import com.skydoves.pokedex.compose.core.preview.PreviewUtils
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@Composable
fun PokedexHome(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    homeViewModel: HomeViewModel,
) {
    val uiState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val pokemonList by
        if (PokedexFeatureFlags.UseLifecycleEffectForDataLoadingOnStartup) {
            val scope = rememberCoroutineScope()
            val pokemonList = remember { mutableStateOf(emptyList<Pokemon>()) }
            LifecycleStartEffect(scope) {
                scope.launch { homeViewModel.pokemonList.collect { pokemonList.value = it } }
                onStopOrDispose { scope.cancel() }
            }
            pokemonList
        } else {
            homeViewModel.pokemonList.collectAsStateWithLifecycle()
        }

    Column(modifier = Modifier.fillMaxSize()) {
        PokedexAppBar()

        HomeContent(
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope,
            uiState = uiState,
            pokemonList = pokemonList.toImmutableList(),
            fetchNextPokemonList = homeViewModel::fetchNextPokemonList,
        )
    }
}

@Composable
private fun HomeContent(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    uiState: HomeUiState,
    pokemonList: ImmutableList<Pokemon>,
    fetchNextPokemonList: () -> Unit,
) {
    if (sharedTransitionScope != null) {
        val statusText =
            "pokedex-home-transition-active-${sharedTransitionScope.isTransitionActive}"
        Text(statusText, Modifier.semantics { testTag = statusText })
    }
    Box(modifier = Modifier.fillMaxSize()) {
        val gridState = rememberLazyGridState()
        LaunchedEffect(gridState, pokemonList) {
            val paginationThreshold = pokemonList.size - PaginationBufferSize
            snapshotFlow { gridState.firstVisibleItemIndex >= paginationThreshold }
                .collect { shouldFetchNewItems ->
                    if (shouldFetchNewItems) {
                        fetchNextPokemonList()
                    }
                }
        }

        val scrollIndicatorState = gridState.scrollIndicatorState
        val scrollbarModifier =
            if (PokedexFeatureFlags.EnableScrollbar && scrollIndicatorState != null) {
                Modifier.nonInteractiveScrollbar(
                    state = scrollIndicatorState,
                    orientation = Orientation.Vertical,
                )
            } else {
                Modifier
            }

        ReportDrawnWhen { pokemonList.isNotEmpty() }

        LazyVerticalGrid(
            state = gridState,
            modifier = Modifier.testTag("PokedexList").then(scrollbarModifier),
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(6.dp),
        ) {
            items(items = pokemonList, key = { pokemon -> pokemon.name }) { pokemon ->
                PokemonCard(
                    animatedVisibilityScope = animatedVisibilityScope,
                    sharedTransitionScope = sharedTransitionScope,
                    pokemon = pokemon,
                )
            }
        }

        if (uiState == HomeUiState.Loading) {
            PokedexCircularProgress()
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun PokemonCard(
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope,
    pokemon: Pokemon,
) {
    val composeNavigator = currentComposeNavigator
    val palette by remember { mutableStateOf<Palette?>(null) }
    val backgroundColor by palette.paletteBackgroundColor()

    Card(
        modifier =
            Modifier.padding(6.dp).fillMaxWidth().testTag("${pokemon.name}_card").clickable {
                composeNavigator.navigate(PokedexScreen.Details(pokemon = pokemon))
            },
        shape = RoundedCornerShape(14.dp),
        colors =
            CardDefaults.cardColors(
                containerColor = backgroundColor,
                contentColor = backgroundColor,
                disabledContainerColor = backgroundColor,
                disabledContentColor = backgroundColor,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        PokemonCardImage(
            modifier =
                Modifier.align(Alignment.CenterHorizontally)
                    .padding(top = 20.dp)
                    .size(120.dp)
                    .then(
                        if (
                            sharedTransitionScope != null &&
                                PokedexFeatureFlags.EnableSharedElementTransitions
                        ) {
                            Modifier.pokedexSharedElement(
                                sharedTransitionScope = sharedTransitionScope,
                                isLocalInspectionMode = LocalInspectionMode.current,
                                state =
                                    sharedTransitionScope.rememberSharedContentState(
                                        key = "image-${pokemon.name}"
                                    ),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = boundsTransform,
                            )
                        } else Modifier
                    ),
            pokemon = pokemon,
        )

        Text(
            modifier =
                Modifier.align(Alignment.CenterHorizontally)
                    .fillMaxWidth()
                    .then(
                        if (
                            sharedTransitionScope != null &&
                                PokedexFeatureFlags.EnableSharedElementTransitions
                        ) {
                            Modifier.pokedexSharedElement(
                                sharedTransitionScope = sharedTransitionScope,
                                isLocalInspectionMode = LocalInspectionMode.current,
                                state =
                                    sharedTransitionScope.rememberSharedContentState(
                                        key = "name-${pokemon.name}"
                                    ),
                                animatedVisibilityScope = animatedVisibilityScope,
                                boundsTransform = boundsTransform,
                            )
                        } else Modifier
                    )
                    .padding(12.dp),
            text = pokemon.name,
            color = PokedexTheme.colors.black,
            textAlign = TextAlign.Center,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
@OptIn(ExperimentalGlideComposeApi::class)
private fun PokemonCardImage(pokemon: Pokemon, modifier: Modifier = Modifier) {
    val imageModel = getPokemonImageUrlByName(pokemon.name).toString()
    if (PokedexFeatureFlags.UseCoil) {
        AsyncImage(
            modifier = modifier,
            contentDescription = pokemon.name,
            model =
                ImageRequest.Builder(LocalContext.current)
                    .data(imageModel)
                    .crossfade(PokemonCardImageCrossfadeDurationMillis)
                    .build(),
            contentScale = ContentScale.Inside,
            placeholder = painterResource(id = R.drawable.pokemon_preview),
        )
    } else {
        GlideImage(
            modifier = modifier,
            contentDescription = pokemon.name,
            model = imageModel,
            contentScale = ContentScale.Inside,
            loading = placeholder(R.drawable.pokemon_preview),
            failure = placeholder(R.drawable.pokemon_preview),
        )
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PokedexHomePreview() {
    PokedexTheme {
        SharedTransitionScope { modifier ->
            AnimatedVisibility(modifier = modifier, visible = true, label = "") {
                PokedexHome(
                    animatedVisibilityScope = this,
                    sharedTransitionScope = this@SharedTransitionScope,
                    homeViewModel =
                        viewModel { HomeViewModel(homeRepository = FakeHomeRepository()) },
                )
            }
        }
    }
}

@Preview
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HomeContentPreview() {
    PokedexPreviewTheme { scope ->
        val homeViewModel = viewModel { HomeViewModel(homeRepository = FakeHomeRepository()) }
        HomeContent(
            animatedVisibilityScope = scope,
            sharedTransitionScope = this@PokedexPreviewTheme,
            uiState = HomeUiState.Idle,
            pokemonList = PreviewUtils.mockPokemonList().toImmutableList(),
            fetchNextPokemonList = { homeViewModel.fetchNextPokemonList() },
        )
    }
}

private const val PaginationBufferSize = 8
private const val PokemonCardImageCrossfadeDurationMillis = 250
