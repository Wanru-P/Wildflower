package com.floralens.ui.screens.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.baidu.mapapi.map.*
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.model.LatLngBounds
import com.floralens.data.network.SystemNetworkStatus
import kotlinx.coroutines.delay

/** SDK ownership is tied to the composition/lifecycle, while camera values survive navigation/recreation. */
@Composable
fun BaiduObservationMap(pins: List<MapPin>, onSelect: (Long) -> Unit, modifier: Modifier = Modifier,
    agreed: Boolean, focusedObservationId: Long? = null, focusRequest: Int = 0, fitRequest: Int = 0, onFitAll: () -> Unit = {}, hasLocationPermission: Boolean = false,
    onProjected: (Map<Long, com.floralens.domain.model.DisplayCoordinate>) -> Unit = {}) =
    BaiduMapSurface(pins.map { BaiduMapPoint(it.observationId, it.latitude, it.longitude, it.coordinateSystem) },
        onSelect, modifier, agreed, focusedObservationId, focusRequest, fitRequest, onFitAll, hasLocationPermission, onProjected)

data class BaiduMapPoint(val id: Long, val latitude: Double, val longitude: Double, val source: com.floralens.domain.model.CoordinateSystem)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BaiduMapSurface(pins: List<BaiduMapPoint>, onSelect: (Long) -> Unit, modifier: Modifier = Modifier,
    agreed: Boolean, focusedObservationId: Long? = null, focusRequest: Int = 0, fitRequest: Int = 0, onFitAll: () -> Unit = {}, hasLocationPermission: Boolean = false,
    onProjected: (Map<Long, com.floralens.domain.model.DisplayCoordinate>) -> Unit = {}) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val network = remember(context) { SystemNetworkStatus(context) }
    var connected by remember { mutableStateOf(network.isConnected()) }
    var generation by remember { mutableIntStateOf(0) }
    var lastRetry by remember { mutableLongStateOf(0) }
    var initError by remember { mutableStateOf(false) }
    var requestDone by remember(generation) { mutableStateOf(false) }
    var preparationTimedOut by remember(generation) { mutableStateOf(false) }
    var locationNotice by remember { mutableStateOf<String?>(null) }
    val ready by BaiduMapRuntime.ready.collectAsState()
    val auth by BaiduMapRuntime.authCode.collectAsState()
    var lat by rememberSaveable { mutableDoubleStateOf(0.0) }
    var lon by rememberSaveable { mutableDoubleStateOf(0.0) }
    var zoom by rememberSaveable { mutableFloatStateOf(3f) }
    var bearing by rememberSaveable { mutableFloatStateOf(0f) }
    var fitted by rememberSaveable { mutableStateOf(false) }
    var appliedFit by rememberSaveable { mutableIntStateOf(0) }
    var appliedFocus by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(agreed, generation) {
        initError = runCatching { BaiduMapRuntime.initialize(context, agreed) }.isFailure
        requestDone = !initError
    }
    LaunchedEffect(ready, requestDone, generation) {
        preparationTimedOut = false
        if (!ready || !requestDone) { delay(25_000); preparationTimedOut = true }
    }
    LaunchedEffect(network) {
        while (true) {
            val next = network.isConnected()
            if (next && !connected) generation++
            connected = next
            delay(1_500)
        }
    }
    fun retry() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastRetry < 1_000) return
        lastRetry = now; connected = network.isConnected()
        if (connected) generation++
    }
    if (!agreed) return
    Box(modifier.testTag(if (ready && requestDone && !initError) "baidu-sdk-ready" else "baidu-sdk-starting")) {
        if (ready && requestDone && !initError) key(generation) {
            var loaded by remember { mutableStateOf(false) }
            var tile by remember { mutableStateOf(false) }
            var timedOut by remember { mutableStateOf(false) }
            val mask = remember(context) { MapRegionMask(context) }
            val points = remember(pins) { pins.mapNotNull { pin -> baiduCoordinate(pin.latitude, pin.longitude, pin.source, mask)?.let { pin.id to it } }.toMap() }
            val projectCallback by rememberUpdatedState(onProjected)
            LaunchedEffect(points) { projectCallback(points.mapValues { com.floralens.domain.model.DisplayCoordinate(it.value.latitude, it.value.longitude) }) }
            val select by rememberUpdatedState(onSelect)
            val holder = remember(context) { runCatching {
                BaiduMapHolder(context, MapStatus.Builder().target(LatLng(lat, lon)).zoom(zoom).rotate(bearing).build(),
                    { loaded = true }, { tile = true }, { select(it) }, { position ->
                        lat = position.target.latitude; lon = position.target.longitude; zoom = position.zoom; bearing = position.rotate
                    })
            }.getOrNull() }
            LaunchedEffect(loaded, tile, connected) { timedOut = false; if ((!loaded || !tile) && connected) { delay(25_000); timedOut = true } }
            if (holder != null) {
                DisposableEffect(holder, lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) { Lifecycle.Event.ON_RESUME -> holder.resume(); Lifecycle.Event.ON_PAUSE -> holder.pause(); else -> Unit }
                    }
                    lifecycle.addObserver(observer)
                    if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.resume()
                    onDispose { lifecycle.removeObserver(observer); holder.pause() }
                }
                AndroidView(factory = { holder.view }, modifier = Modifier.fillMaxSize().pointerInteropFilter { event ->
                    holder.view.parent?.requestDisallowInterceptTouchEvent(event.actionMasked != android.view.MotionEvent.ACTION_UP && event.actionMasked != android.view.MotionEvent.ACTION_CANCEL)
                    holder.touch(event)
                    true
                }.testTag(
                    if (loaded && tile && auth == 0) "baidu-map-loaded" else "baidu-map-loading"),
                    onReset = null, onRelease = { holder.destroy() }, update = { holder.update(points) })
                LaunchedEffect(holder, loaded, points, fitRequest) {
                    if (loaded && points.isNotEmpty() && (!fitted || appliedFit != fitRequest)) {
                        holder.fit(points.values.toList()); fitted = true; appliedFit = fitRequest
                    }
                }
                LaunchedEffect(holder, loaded, focusRequest) {
                    if (loaded && appliedFocus != focusRequest) points[focusedObservationId]?.let {
                        holder.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(it, 16f)); appliedFocus = focusRequest
                    }
                }
                if (loaded && connected && points.isNotEmpty()) FilledTonalIconButton(onFitAll,
                    Modifier.align(Alignment.TopEnd).padding(8.dp).testTag("map-fit-all")) { Icon(Icons.Default.Map, "查看全部地点") }
                if (loaded && hasLocationPermission) FilledTonalIconButton({
                    val fix = com.floralens.data.location.SystemLocationProvider(context).lastKnownLocation()
                    val point = fix?.let { baiduCoordinate(it.latitude, it.longitude, com.floralens.domain.model.CoordinateSystem.WGS84, mask) }
                    if (point != null) {
                        holder.map.setMyLocationEnabled(true)
                        holder.map.setMyLocationData(MyLocationData.Builder().latitude(point.latitude).longitude(point.longitude).build())
                        holder.map.animateMapStatus(MapStatusUpdateFactory.newLatLngZoom(point, 16f)); locationNotice = null
                    } else locationNotice = "系统暂无可用位置，已有观察记录不受影响。"
                }, Modifier.align(Alignment.TopEnd).padding(top = 60.dp, end = 8.dp)) { Text("定位", style = MaterialTheme.typography.labelSmall) }
                locationNotice?.let { Text(it, Modifier.align(Alignment.TopStart).padding(top = 50.dp, start = 12.dp), style = MaterialTheme.typography.bodySmall) }
                if (points.size < pins.size) Surface(Modifier.align(Alignment.TopStart).padding(8.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .94f), shape = MaterialTheme.shapes.small) {
                    Text("${pins.size - points.size} 条位置来源待确认 · 可查看本地记录", Modifier.padding(8.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            when {
                !connected -> MapLoadNotice("当前网络不可用", "恢复网络后会重新加载，已有观察仍可从本地记录查看。", ::retry)
                holder == null -> MapLoadNotice("地图服务暂不可用", "地图未能启动，已有观察记录已保留。", ::retry)
                auth != null && auth != 0 -> MapLoadNotice("在线地图暂不可用", "暂时无法取得地图服务授权，请稍后重试。已有记录仍可浏览。", ::retry)
                timedOut -> MapLoadNotice("在线地图暂时无法加载", "网络或地图服务可能受限，请重试或查看本地记录。", ::retry)
                !loaded || !tile || auth != 0 -> Surface(Modifier.align(Alignment.TopCenter).padding(top = 54.dp, start = 12.dp, end = 12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = .94f), shape = MaterialTheme.shapes.medium) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("正在加载在线底图…", Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        } else if (!connected) MapLoadNotice("当前网络不可用", "恢复网络后可重试，已有观察仍可从本地记录查看。", ::retry)
        else if (initError || preparationTimedOut) MapLoadNotice("地图服务暂不可用", "暂时无法准备地图，请稍后重试。", ::retry)
        else Text("正在准备地图服务…", Modifier.align(Alignment.Center))
    }
}

@Composable
private fun BoxScope.MapLoadNotice(title: String, body: String, retry: () -> Unit) {
    Surface(Modifier.align(Alignment.Center).padding(20.dp).testTag("baidu-map-failed"), shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium); Text(body, style = MaterialTheme.typography.bodyMedium)
            TextButton(retry) { Text("重新加载地图") }
        }
    }
}

private class BaiduMapHolder(context: Context, status: MapStatus, loaded: () -> Unit, tile: () -> Unit,
    selected: (Long) -> Unit, private val cameraChanged: (MapStatus) -> Unit) {
    val view = MapView(context, BaiduMapOptions().mapStatus(status).zoomControlsEnabled(false)
        .zoomGesturesEnabled(true).scrollGesturesEnabled(true)
        .logoPosition(LogoPosition.logoPostionleftBottom))
    val map: BaiduMap = view.map
    private val markers = mutableMapOf<Long, Marker>()
    private var resumed = false
    private var resumeWanted = false
    private var destroyed = false
    private var scaling = false
    private var previousSpan = 0f
    private val icon: BitmapDescriptor
    private val attachment = object : android.view.View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: android.view.View) {
            v.post { if (resumeWanted && !destroyed && v.isAttachedToWindow) resumeNative() }
        }
        override fun onViewDetachedFromWindow(v: android.view.View) { pauseNative() }
    }
    init {
        android.util.Log.i("WildflowerMap", "Map container created")
        map.uiSettings.setAllGesturesEnabled(true)
        map.uiSettings.setOverlookingGesturesEnabled(false)
        // AndroidView/Baidu's two-pointer recognizer does not consistently change zoom on API 35.
        // Route real two-pointer distance changes through the SDK camera; native single-pointer pan remains intact.
        map.uiSettings.setZoomGesturesEnabled(false)
        val scale = context.resources.displayMetrics.density
        val bitmap = Bitmap.createBitmap((32 * scale).toInt(), (44 * scale).toInt(), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(36, 69, 46) }
        canvas.drawPath(Path().apply { moveTo(4f, 20f); lineTo(16f, 42f); lineTo(28f, 20f); close() }, paint)
        canvas.drawCircle(16f, 16f, 14f, paint); paint.color = android.graphics.Color.rgb(248, 248, 241)
        canvas.drawCircle(16f, 16f, 6f, paint)
        icon = BitmapDescriptorFactory.fromBitmap(bitmap)
        map.setOnMapLoadedCallback { android.util.Log.i("WildflowerMap", "Map loaded"); loaded() }
        map.setOnMapTileLoadedCallback { android.util.Log.i("WildflowerMap", "First online tile loaded"); tile() }
        map.setOnMarkerClickListener { marker -> marker.extraInfo?.let { selected(it.getLong("observationId")) }; true }
        map.setOnMapStatusChangeListener(object : BaiduMap.OnMapStatusChangeListener {
            override fun onMapStatusChangeStart(status: MapStatus) = Unit
            override fun onMapStatusChangeStart(status: MapStatus, reason: Int) = Unit
            override fun onMapStatusChange(status: MapStatus) = Unit
            override fun onMapStatusChangeFinish(status: MapStatus) = cameraChanged(status)
        })
        view.addOnAttachStateChangeListener(attachment)
    }
    fun update(points: Map<Long, LatLng>) {
        if (destroyed) return
        markers.keys.filterNot(points::containsKey).forEach { markers.remove(it)?.remove() }
        points.forEach { (id, point) ->
            val marker = markers[id]
            if (marker != null) { if (marker.position.latitude != point.latitude || marker.position.longitude != point.longitude) marker.position = point }
            else markers[id] = map.addOverlay(MarkerOptions().position(point).icon(icon).anchor(.5f, 1f)
                .extraInfo(Bundle().apply { putLong("observationId", id) })) as Marker
        }
    }
    fun fit(points: List<LatLng>) {
        if (destroyed || points.isEmpty()) return
        val update = if (points.distinctBy { it.latitude to it.longitude }.size == 1) MapStatusUpdateFactory.newLatLngZoom(points.first(), 15f)
        else MapStatusUpdateFactory.newLatLngBounds(LatLngBounds.Builder().apply { points.forEach(::include) }.build())
        view.post { if (!destroyed) map.setMapStatus(update) }
    }
    fun touch(event: android.view.MotionEvent) {
        if (destroyed) return
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) { scaling = false; previousSpan = 0f }
        if (event.pointerCount >= 2) {
            val span = kotlin.math.hypot(event.getX(0) - event.getX(1), event.getY(0) - event.getY(1))
            if (!scaling) {
                val cancel = android.view.MotionEvent.obtain(event).apply { action = android.view.MotionEvent.ACTION_CANCEL }
                view.dispatchTouchEvent(cancel); cancel.recycle(); scaling = true
            }
            if (event.actionMasked == android.view.MotionEvent.ACTION_MOVE && previousSpan > 0 && span > 0) {
                val delta = kotlin.math.log2(span / previousSpan)
                map.setMapStatus(MapStatusUpdateFactory.zoomBy(delta, android.graphics.Point(
                    ((event.getX(0) + event.getX(1)) / 2).toInt(), ((event.getY(0) + event.getY(1)) / 2).toInt())))
            }
            previousSpan = span
        } else if (scaling) {
            if (event.actionMasked == android.view.MotionEvent.ACTION_UP || event.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
                scaling = false; previousSpan = 0f
            }
        } else view.dispatchTouchEvent(event)
    }
    private fun resumeNative() { if (!destroyed && !resumed) { view.onResume(); resumed = true } }
    private fun pauseNative() { if (!destroyed && resumed) { cameraChanged(map.mapStatus); view.onPause(); resumed = false } }
    fun resume() { resumeWanted = true; if (view.isAttachedToWindow) resumeNative() }
    fun pause() { resumeWanted = false; pauseNative() }
    fun destroy() {
        if (destroyed) return
        pause(); destroyed = true
        view.removeOnAttachStateChangeListener(attachment)
        map.setOnMapLoadedCallback(null); map.setOnMapTileLoadedCallback(null); map.setOnMapStatusChangeListener(null)
        map.setOnMarkerClickListener(null); markers.clear(); view.onDestroy()
        // The SDK may still drain queued native bitmap work while its view is being released.
        // Keep the descriptor owned by this holder until GC instead of recycling it underneath that work.
        android.util.Log.i("WildflowerMap", "Map container destroyed")
    }
}
