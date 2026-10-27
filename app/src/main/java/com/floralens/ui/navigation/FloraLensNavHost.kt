package com.floralens.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.unit.dp
import com.floralens.R
import com.floralens.ui.components.KitIcon
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import com.floralens.ui.components.WildflowerTopBar
import com.floralens.ui.screens.explore.ExploreScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Yard
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Yard
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.HorizontalDivider
import com.floralens.ui.theme.Editorial
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.graphics.drawable.toDrawable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.floralens.data.settings.SettingsRepository
import com.floralens.domain.model.IdCandidate
import com.floralens.reminders.NotificationHelper
import com.floralens.ui.screens.about.AboutScreen
import com.floralens.ui.screens.care.CareScreen
import com.floralens.ui.screens.details.PlantDetailsScreen
import com.floralens.ui.screens.identify.IdentifyScreen
import com.floralens.ui.screens.leafhealth.LeafHealthScreen
import com.floralens.ui.screens.map.MapScreen
import com.floralens.ui.screens.onboarding.OnboardingScreen
import com.floralens.ui.screens.plantdetail.PlantDetailScreen
import com.floralens.ui.screens.plants.MyPlantsScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.net.URLEncoder
import javax.inject.Inject
import com.floralens.ui.screens.observation.ObservationEditorScreen
import com.floralens.ui.screens.observation.ObservationGalleryScreen
import com.floralens.ui.screens.culture.CultureScreen
import com.floralens.ui.screens.culture.CultureReviewScreen
import com.floralens.ui.screens.chat.PlantChatScreen
import com.floralens.ui.screens.seasons.SeasonsScreen
import com.floralens.BuildConfig

/** Route constants. */
object Routes {
    const val EXPLORE = "explore"
    const val ONBOARDING = "onboarding"
    const val IDENTIFY = "identify"
    const val PLANTS = "plants"
    const val CARE = "care"
    const val MAP = "map"
    const val ABOUT = "about"
    const val PLANT_DETAIL = "plant/{plantId}?observationId={observationId}"
    const val CULTURE = "culture/{speciesKey}?review={review}&plantId={plantId}&captureId={captureId}"
    const val CULTURE_REVIEW = "culture-review"
    const val SEASONS = "seasons"
    const val CARD = "plant/{plantId}/card"
    const val CHAT = "plant-chat?source={source}&speciesKey={speciesKey}&plantId={plantId}&captureId={captureId}"
    fun card(id: Long) = "plant/$id/card"
    fun culture(key: String, review: Boolean = false, plantId: Long = 0, captureId: String? = null) =
        "culture/${android.net.Uri.encode(key)}?review=$review&plantId=$plantId&captureId=${android.net.Uri.encode(captureId.orEmpty())}"
    fun seasonalObservation(plantId: Long, observationId: Long) = "plant/$plantId?observationId=$observationId"
    const val LEAF_HEALTH = "plant/{plantId}/leaf-health"
    const val SPECIES_DETAILS = "species?species={species}&common={common}&images={images}"
    const val CAPTURE_DETAILS = "capture/{captureId}"
    const val OBSERVATION_EDITOR = "plant/{plantId}/observation?observationId={observationId}"
    const val OBSERVATION_GALLERY = "plant/{plantId}/gallery"
    fun captureDetails(id: String) = "capture/$id"
    fun editor(id: Long, observationId: Long = 0) = "plant/$id/observation?observationId=$observationId"
    fun gallery(id: Long) = "plant/$id/gallery"
    fun chatCapture(id: String) = "plant-chat?source=capture&speciesKey=&plantId=0&captureId=${android.net.Uri.encode(id)}"
    fun chatSpecies(key: String) = "plant-chat?source=species&speciesKey=${android.net.Uri.encode(key)}&plantId=0&captureId="
    fun chatPlant(id: Long) = "plant-chat?source=plant&speciesKey=&plantId=$id&captureId="

    fun plantDetail(plantId: Long) = "plant/$plantId"
    fun leafHealth(plantId: Long) = "plant/$plantId/leaf-health"

    fun speciesDetails(candidate: IdCandidate): String {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        val species = enc(candidate.scientificName)
        val common = enc(candidate.commonName)
        val images = enc(candidate.imageUrls.joinToString("\n"))
        return "species?species=$species&common=$common&images=$images"
    }

    /** Species details by name — used from the plant screen; no image URLs. */
    fun speciesDetailsByName(scientificName: String, commonName: String): String {
        val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
        return "species?species=${enc(scientificName)}&common=${enc(commonName)}&images="
    }
}

private data class TopLevelDestination(val route: String, val icon: ImageVector, val label: String)

private val topLevelDestinations = listOf(
    TopLevelDestination(Routes.EXPLORE, Icons.Outlined.Eco, "发现"),
    TopLevelDestination(Routes.IDENTIFY, Icons.Outlined.PhotoCamera, "识别"),
    TopLevelDestination(Routes.PLANTS, Icons.AutoMirrored.Outlined.MenuBook, "花园"),
)

@HiltViewModel
class AppStartViewModel @Inject constructor(
    settings: SettingsRepository,
) : ViewModel() {
    val startDestination: StateFlow<String?> = settings.onboarded
        .map { onboarded -> if (onboarded) Routes.EXPLORE else Routes.ONBOARDING }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}

@Composable
fun FloraLensNavHost(
    pendingDestination: String? = null,
    onDestinationConsumed: () -> Unit = {},
) {
    val navController = rememberNavController()

    val appStartViewModel: AppStartViewModel = hiltViewModel()
    val resolvedStart = appStartViewModel.startDestination.collectAsStateWithLifecycle().value
        ?: return
    val startDestination = remember { resolvedStart }

    LaunchedEffect(pendingDestination) {
        if (pendingDestination == NotificationHelper.DESTINATION_CARE) {
            // A cold notification can arrive before NavHost attaches its graph.
            // Wait for an actual entry instead of racing the graph getter.
            navController.currentBackStackEntryFlow.first()
            // A notification tap while Care is already visible must not save
            // that same secondary page into Garden's restored tab stack.
            if (navController.currentDestination?.route != Routes.CARE) {
                navController.navigate(Routes.CARE) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
            onDestinationConsumed()
        }
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBottomBar = topLevelDestinations.any { it.route == currentRoute }
    val isCamera = currentRoute == Routes.IDENTIFY
    val window = LocalActivity.current?.window
    val view = LocalView.current
    // Some host windows already inset the Compose view below the cutout. Do not inset Scan twice.
    // Edge-to-edge hosts (origin == 0) keep Scaffold's safe insets instead.
    val nativeInsets = IntArray(2).also { view.getLocationInWindow(it) }[1] > 0
    val shellColor = if (isCamera) Color(0xFF0A1A12) else MaterialTheme.colorScheme.background
    SideEffect {
        window?.let {
            it.setBackgroundDrawable(shellColor.toArgb().toDrawable())
            WindowCompat.getInsetsController(it, view).apply {
                isAppearanceLightStatusBars = !isCamera
                isAppearanceLightNavigationBars = !isCamera
            }
        }
    }

    Scaffold(
        containerColor = shellColor,
        contentWindowInsets = if (isCamera && nativeInsets) WindowInsets(0, 0, 0, 0) else ScaffoldDefaults.contentWindowInsets,
        bottomBar = {
            if (showBottomBar) {
                Column(Modifier.windowInsetsPadding(if (isCamera && nativeInsets) WindowInsets(0, 0, 0, 0) else NavigationBarDefaults.windowInsets)) {
                HorizontalDivider(color = if (isCamera) Color(0xFF24452E) else Editorial.hairline)
                NavigationBar(modifier = Modifier.height(74.dp), containerColor = if (isCamera) Color(0xFF193B27) else Editorial.paper, tonalElevation = androidx.compose.ui.unit.Dp(0f),
                    windowInsets = WindowInsets(0, 0, 0, 0)) {
                    topLevelDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = currentRoute == dest.route,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { KitIcon(when(dest.route) { Routes.EXPLORE -> R.drawable.wf_icon_leaf; Routes.IDENTIFY -> R.drawable.wf_icon_camera; else -> R.drawable.wf_icon_book }, description = dest.label, tint = if (isCamera) Editorial.paper else Editorial.forest) },
                            label = { Text(dest.label, style = MaterialTheme.typography.labelSmall) },
                            colors = if (isCamera) NavigationBarItemDefaults.colors(
                                selectedIconColor = Editorial.paper, selectedTextColor = Editorial.paper,
                                unselectedIconColor = Editorial.sage, unselectedTextColor = Editorial.sage,
                                indicatorColor = Color(0xFF24452E))
                            else NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding).consumeWindowInsets(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onFinished = {
                    navController.navigate(Routes.EXPLORE) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.EXPLORE) {
                ExploreScreen(
                    onSpecies = { navController.navigate(Routes.speciesDetailsByName(it.scientific, it.name)) },
                    onScan = { navController.navigate(Routes.IDENTIFY) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    } },
                    onSettings = { navController.navigate(Routes.ABOUT) },
                    onCulture = { navController.navigate(Routes.culture(it)) },
                    onReview = { if (BuildConfig.DEBUG && BuildConfig.INTERNAL_TOOLS) navController.navigate(Routes.CULTURE_REVIEW) },
                )
            }
            composable(Routes.IDENTIFY) {
                IdentifyScreen(
                    onDetails = { navController.navigate(Routes.captureDetails(it)) },
                    onChat = { navController.navigate(Routes.chatCapture(it)) },
                    onSettings = { navController.navigate(Routes.ABOUT) },
                )
            }
            composable(Routes.PLANTS) {
                MyPlantsScreen(
                    onPlantClick = { id -> navController.navigate(Routes.plantDetail(id)) },
                    onIdentifyClick = {
                        navController.navigate(Routes.IDENTIFY) {
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAboutClick = { navController.navigate(Routes.ABOUT) },
                    onMapClick = { navController.navigate(Routes.MAP) },
                    onCareClick = { navController.navigate(Routes.CARE) },
                    onSeasons = { navController.navigate(Routes.SEASONS) },
                )
            }
            composable(Routes.CARE) { SecondaryPage("养护提醒", { navController.popBackStack() }) { CareScreen() } }
            composable(Routes.MAP) { SecondaryPage("观察地图", { navController.popBackStack() }) {
                MapScreen(onPlant = { navController.navigate(Routes.plantDetail(it)) })
            } }
            composable(Routes.ABOUT) {
                AboutScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.PLANT_DETAIL,
                arguments = listOf(navArgument("plantId") { type = NavType.LongType }, navArgument("observationId") { type = NavType.LongType; defaultValue = 0L }),
            ) { entry ->
                val plantId = entry.arguments?.getLong("plantId") ?: return@composable
                PlantDetailScreen(
                    onBack = { navController.popBackStack() },
                    onLeafCheck = { navController.navigate(Routes.leafHealth(plantId)) },
                    onAddObservation = { navController.navigate(Routes.editor(plantId)) },
                    onGallery = { navController.navigate(Routes.gallery(plantId)) },
                    onCard = { navController.navigate(Routes.card(plantId)) },
                    onChat = { navController.navigate(Routes.chatPlant(plantId)) },
                    onLearnMore = { species, common ->
                        navController.navigate(Routes.speciesDetailsByName(species, common))
                    },
                    onCulture = { key, id -> navController.navigate(Routes.culture(key, plantId = id)) },
                )
            }
            composable(
                Routes.LEAF_HEALTH,
                arguments = listOf(navArgument("plantId") { type = NavType.LongType }),
            ) {
                LeafHealthScreen(onBack = { navController.popBackStack() })
            }
            composable(
                Routes.SPECIES_DETAILS,
                arguments = listOf(
                    navArgument("species") { type = NavType.StringType },
                    navArgument("common") { type = NavType.StringType; defaultValue = "" },
                    navArgument("images") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                PlantDetailsScreen(onBack = { navController.popBackStack() }, onCulture = { key, capture -> navController.navigate(Routes.culture(key, captureId = capture)) },
                    onChat = { key -> navController.navigate(Routes.chatSpecies(key)) }, onCollected = { id ->
                    navController.navigate(Routes.plantDetail(id)) { popUpTo(entry.destination.id) { inclusive = true }; launchSingleTop = true }
                })
            }
            composable(Routes.CAPTURE_DETAILS, arguments = listOf(navArgument("captureId") { type = NavType.StringType })) { entry ->
                PlantDetailsScreen(onBack = { navController.popBackStack() }, onCulture = { key, capture -> navController.navigate(Routes.culture(key, captureId = capture)) },
                    onChat = { key -> navController.navigate(Routes.chatSpecies(key)) }, onCollected = { id ->
                    navController.navigate(Routes.plantDetail(id)) { popUpTo(entry.destination.id) { inclusive = true }; launchSingleTop = true }
                })
            }
            composable(Routes.OBSERVATION_EDITOR, arguments = listOf(navArgument("plantId") { type = NavType.LongType }, navArgument("observationId") { type = NavType.LongType; defaultValue = 0L })) {
                ObservationEditorScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.OBSERVATION_GALLERY, arguments = listOf(navArgument("plantId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("plantId") ?: return@composable
                ObservationGalleryScreen(onBack = { navController.popBackStack() },
                    onEdit = { navController.navigate(Routes.editor(id, it)) },
                    onAddObservation = { navController.navigate(Routes.editor(id)) })
            }
            composable(Routes.CULTURE, arguments = listOf(
                navArgument("speciesKey") { type = NavType.StringType }, navArgument("review") { type = NavType.BoolType; defaultValue = false },
                navArgument("plantId") { type = NavType.LongType; defaultValue = 0L }, navArgument("captureId") { type = NavType.StringType; defaultValue = "" })) {
                CultureScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.CHAT, arguments = listOf(
                navArgument("source") { type = NavType.StringType; defaultValue = "" },
                navArgument("speciesKey") { type = NavType.StringType; defaultValue = "" },
                navArgument("plantId") { type = NavType.LongType; defaultValue = 0L },
                navArgument("captureId") { type = NavType.StringType; defaultValue = "" },
            )) {
                PlantChatScreen(onBack = { navController.popBackStack() })
            }
            if (BuildConfig.DEBUG && BuildConfig.INTERNAL_TOOLS) composable(Routes.CULTURE_REVIEW) {
                CultureReviewScreen(onBack = { navController.popBackStack() }, onEntry = { navController.navigate(Routes.culture(it, review = true)) })
            }
            composable(Routes.SEASONS) {
                SeasonsScreen(onBack = { navController.popBackStack() }, onScan = { navController.navigate(Routes.IDENTIFY) },
                    onObservation = { plant, observation -> navController.navigate(Routes.seasonalObservation(plant, observation)) })
            }
            composable(Routes.CARD, arguments = listOf(navArgument("plantId") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("plantId") ?: return@composable
                com.floralens.ui.screens.card.BotanicalCardScreen(onBack = { navController.popBackStack() },
                    onAddObservation = { navController.navigate(Routes.editor(id)) })
            }
        }
    }
}

@Composable
private fun SecondaryPage(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        WildflowerTopBar(title, onBack)
        Box(Modifier.weight(1f)) { content() }
    }
}
