package io.github.mangi.eta.ui.screens.gallery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.itos.coui.layout.CouiPageDefaults
import com.itos.coui.layout.Footer
import io.github.mangi.eta.R
import io.github.mangi.eta.data.model.AppearanceUiStyle
import io.github.mangi.eta.ui.app.LocalUiStyle
import io.github.mangi.eta.ui.components.savePreviewImage
import io.github.mangi.eta.ui.components.sharePreviewImage
import io.github.mangi.eta.ui.components.toastBriefly
import io.github.mangi.eta.ui.components.MiuixScaffoldPage
import io.github.mangi.eta.ui.components.coui.CouiPageBottomSpacer
import io.github.mangi.eta.ui.components.coui.CouiScaffoldPage
import io.github.mangi.eta.ui.components.rememberDataUrlBitmap
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 只把真正的图片文件列进画廊，sidecar 的 json 要排除。 */
private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

/** 一次最多铺这么多张，避免目录堆积几百张时整页组合卡住。 */
private const val MAX_GALLERY_ITEMS = 600

/**
 * 图片画廊：把本机已生成的所有图片按时间倒序铺成网格。
 *
 * 数据源就是生图落盘目录 `files/generated_images`，与聊天里看到的产物是同一份文件，
 * 所以历史会话删掉也不影响这里，点开仍是全屏预览，能单独保存或分享。
 */
@Composable
internal fun ImageGalleryScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var uris by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        uris = withContext(Dispatchers.IO) {
            val dir = File(context.filesDir, "generated_images")
            dir.listFiles()
                ?.asSequence()
                ?.filter { it.isFile && it.extension.lowercase() in IMAGE_EXTENSIONS }
                ?.sortedByDescending(File::lastModified)
                ?.take(MAX_GALLERY_ITEMS)
                ?.map { "file://${it.absolutePath}" }
                ?.toList()
                .orEmpty()
        }
        loading = false
    }

    val title = stringResource(R.string.image_gallery_title)
    val emptyText = stringResource(R.string.image_gallery_empty)
    val loadingText = stringResource(R.string.image_gallery_loading)
    val noteText = stringResource(R.string.image_gallery_note)
    // 画廊是独立路由，聊天页的图片预览宿主不在这个作用域里，
    // 所以自己开一个全屏预览，不依赖 compositionLocal。
    var previewIndex by remember { mutableStateOf<Int?>(null) }
    val openAt: (Int) -> Unit = { index -> previewIndex = index }

    previewIndex?.let { index ->
        if (uris.isNotEmpty()) {
            GalleryPreviewDialog(
                uris = uris,
                initialIndex = index.coerceIn(0, uris.lastIndex),
                onDismiss = { previewIndex = null },
            )
        }
    }

    when (LocalUiStyle.current) {
        AppearanceUiStyle.COUI -> {
            CouiScaffoldPage(title = title, onBack = onBack) {
                when {
                    loading -> GalleryHint(loadingText)
                    uris.isEmpty() -> GalleryHint(emptyText)
                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = CouiPageDefaults.horizontalMargin),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            // 三列网格：以行为单位铺，行内不足三张用空位补齐对齐。
                            uris.withIndex().chunked(3).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    row.forEach { entry ->
                                        GalleryTile(
                                            path = entry.value,
                                            modifier = Modifier.weight(1f),
                                            onClick = { openAt(entry.index) },
                                        )
                                    }
                                    repeat(3 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
                Footer(text = noteText)
                CouiPageBottomSpacer()
            }
        }

        AppearanceUiStyle.MIUIX -> {
            MiuixScaffoldPage(title = title, onBack = onBack) {
                when {
                    loading -> item(key = "gallery_loading") { GalleryHint(loadingText) }
                    uris.isEmpty() -> item(key = "gallery_empty") { GalleryHint(emptyText) }
                    else -> {
                        val rows = uris.withIndex().chunked(3)
                        items(rows) { row ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                row.forEach { entry ->
                                    GalleryTile(
                                        path = entry.value,
                                        modifier = Modifier.weight(1f),
                                        onClick = { openAt(entry.index) },
                                    )
                                }
                                repeat(3 - row.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                        item(key = "gallery_note") {
                            Text(
                                text = noteText,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier
                                    .padding(horizontal = 28.dp)
                                    .padding(bottom = 12.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryHint(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

@Composable
private fun GalleryTile(
    path: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val bitmap = rememberDataUrlBitmap(path)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

}

/** 画廊的全屏预览：黑底、点击退出、底部单张保存与分享。 */
@Composable
private fun GalleryPreviewDialog(
    uris: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(
        initialPage = initialIndex,
        pageCount = { uris.size },
    )
    val currentPath = uris.getOrNull(pagerState.currentPage)
    val saveLabel = stringResource(R.string.image_action_save)
    val shareLabel = stringResource(R.string.image_action_share)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // 左右滑动切图；点画面不再退出，退出只走右上角关闭。
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val bitmap = rememberDataUrlBitmap(uris[page])
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        // 点画面关闭；左右滑动仍然切图，两者不冲突。
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onDismiss),
                    )
                }
            }
            // 与聊天里的图片查看一致：页码居中、没有关闭按钮，点画面退出。
            if (uris.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.16f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1} / ${uris.size}",
                        style = MiuixTheme.textStyles.footnote1,
                        color = Color.White.copy(alpha = 0.94f),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (currentPath != null) {
                    GalleryActionChip(
                        text = saveLabel,
                        onClick = {
                            scope.launch {
                                toastBriefly(context, savePreviewImage(context, currentPath))
                            }
                        },
                    )
                    GalleryActionChip(
                        text = shareLabel,
                        onClick = {
                            scope.launch {
                                sharePreviewImage(context, currentPath)?.let { message ->
                                    toastBriefly(context, message)
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GalleryActionChip(
    text: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.16f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote1,
            color = Color.White.copy(alpha = 0.94f),
        )
    }
}
