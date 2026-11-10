package com.brian.dialbuddy.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.brian.dialbuddy.R
import com.brian.dialbuddy.data.AppUpdate
import com.brian.dialbuddy.domain.DeletionCounts
import com.brian.dialbuddy.domain.ReleaseOffer
import com.brian.dialbuddy.domain.callPageAfterReorder
import com.brian.dialbuddy.domain.orderedCallCards
import com.brian.dialbuddy.domain.presentVoicemails
import com.brian.dialbuddy.telecom.Calls
import com.brian.dialbuddy.ui.components.AreaCodesPage
import com.brian.dialbuddy.ui.components.CallsMenu
import com.brian.dialbuddy.ui.components.CardPager
import com.brian.dialbuddy.ui.components.HistoryPage
import com.brian.dialbuddy.ui.components.KeypadPane
import com.brian.dialbuddy.ui.components.MailMenu
import com.brian.dialbuddy.ui.components.MailboxPane
import com.brian.dialbuddy.ui.components.NoticesMenu
import com.brian.dialbuddy.ui.components.NoticesPage
import com.brian.dialbuddy.ui.components.PhoneMenu
import com.brian.dialbuddy.ui.components.appVersionName
import com.brian.dialbuddy.ui.components.RaggedCard
import com.brian.dialbuddy.ui.components.SpeakerButton
import com.brian.dialbuddy.ui.components.StatTable
import com.brian.dialbuddy.ui.components.StatsActions
import com.brian.dialbuddy.ui.theme.Accent
import com.brian.dialbuddy.ui.theme.DigitStroke
import com.brian.dialbuddy.ui.theme.Forest
import com.brian.dialbuddy.ui.theme.HistoryBand
import com.brian.dialbuddy.ui.theme.HistoryBody
import com.brian.dialbuddy.ui.theme.HistoryInk
import com.brian.dialbuddy.ui.theme.HistoryStroke
import com.brian.dialbuddy.ui.theme.NoticeBand
import com.brian.dialbuddy.ui.theme.NoticeBody
import com.brian.dialbuddy.ui.theme.NoticeInk
import com.brian.dialbuddy.ui.theme.NoticeStroke
import com.brian.dialbuddy.ui.theme.PhoneBand
import com.brian.dialbuddy.ui.theme.PhoneInk
import com.brian.dialbuddy.ui.theme.PhoneStroke
import com.brian.dialbuddy.ui.theme.TitleBand
import com.brian.dialbuddy.ui.theme.TitleInk
import com.brian.dialbuddy.ui.theme.StatCoral
import com.brian.dialbuddy.ui.theme.StatHoney
import com.brian.dialbuddy.ui.theme.StatLilac
import com.brian.dialbuddy.viewmodel.DialUiState
import com.brian.dialbuddy.viewmodel.DialViewModel
import com.brian.dialbuddy.viewmodel.MailboxTab
import com.brian.dialbuddy.viewmodel.PendingConfirm
import com.brian.dialbuddy.viewmodel.visibleMailboxTabs

private const val PHONE = 0
private const val HISTORY = 1
private const val NOTICES = 2
private const val VOICEMAIL = 3

@Composable
fun DialScreen(
    viewModel: DialViewModel,
    state: DialUiState,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val inCall by Calls.state.collectAsState()
    val calls = orderedCallCards(inCall.calls) { it.isIncoming() }
    var section by remember { mutableIntStateOf(PHONE) }
    var seenCallKeys by remember { mutableStateOf(emptySet<String>()) }
    var settledCallKey by remember { mutableStateOf<String?>(null) }
    val phonePager = rememberPagerState(initialPage = 0) { 1 + calls.size }
    val mailCards = visibleMailboxTabs(viewModel.knownCount(state), viewModel.unknownCount(state))
    val mailPager = rememberPagerState(initialPage = 0) { mailCards.size }
    val historyPager = rememberPagerState(initialPage = 0) { 2 }
    val noticeApps = viewModel.noticeApps(state)
    val noticePager = rememberPagerState(initialPage = 0) { noticeApps.size.coerceAtLeast(1) }
    var shownMailCard by remember { mutableStateOf<MailboxTab?>(null) }
    var shownNoticeApp by remember { mutableStateOf<String?>(null) }
    var areaCodesOpen by remember { mutableStateOf(false) }
    var updateNotice by remember { mutableStateOf<UpdateNotice?>(null) }
    var updateCheck by remember { mutableIntStateOf(0) }
    var readyOffer by remember { mutableStateOf<ReleaseOffer?>(null) }
    val version = remember(context) { appVersionName(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_START) return@LifecycleEventObserver
            val activity = context as? Activity ?: return@LifecycleEventObserver
            AppUpdate.refreshAsync(activity) { offer ->
                if (offer != null && AppUpdate.claimPrompt(activity, offer)) readyOffer = offer
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(state.toast) {
        val message = state.toast ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        viewModel.consumeToast()
    }
    LaunchedEffect(state.focusKeypad) {
        if (state.focusKeypad > 0) {
            section = PHONE
            phonePager.scrollToPage(0)
        }
    }
    LaunchedEffect(phonePager.settledPage) {
        settledCallKey = calls.getOrNull(phonePager.settledPage - 1)?.key
    }
    LaunchedEffect(calls) {
        val keys = calls.map { it.key }
        val added = keys.filter { it !in seenCallKeys }
        seenCallKeys = keys.toSet()
        if (added.isNotEmpty()) {
            section = PHONE
            areaCodesOpen = false
            val target = keys.indexOf(added.last()) + 1
            if (phonePager.currentPage != target) phonePager.animateScrollToPage(target)
        } else {
            val target = callPageAfterReorder(keys, settledCallKey, phonePager.currentPage)
            if (phonePager.currentPage != target) phonePager.scrollToPage(target)
        }
    }
    WatchIncomingSilence(phonePager.settledPage, calls) { page -> page - 1 }
    LaunchedEffect(mailPager.settledPage, mailCards) {
        mailCards.getOrNull(mailPager.settledPage)?.let { shownMailCard = it }
    }
    LaunchedEffect(mailCards) {
        if (mailPager.currentPage >= mailCards.size) {
            mailPager.scrollToPage(mailCards.lastIndex)
        }
        val current = shownMailCard ?: return@LaunchedEffect
        val at = mailCards.getOrNull(mailPager.currentPage)
        if (current in mailCards && at == current) return@LaunchedEffect
        val preferred = when {
            current in mailCards -> current
            current == MailboxTab.Known && MailboxTab.Unknown in mailCards -> MailboxTab.Unknown
            else -> mailCards.last()
        }
        val target = mailCards.indexOf(preferred)
        if (target >= 0 && mailPager.currentPage != target) mailPager.scrollToPage(target)
    }
    LaunchedEffect(noticePager.settledPage, noticeApps) {
        noticeApps.getOrNull(noticePager.settledPage)?.packageName?.let { shownNoticeApp = it }
    }
    LaunchedEffect(noticeApps) {
        if (noticeApps.isEmpty()) {
            if (noticePager.currentPage != 0) noticePager.scrollToPage(0)
            return@LaunchedEffect
        }
        if (noticePager.currentPage >= noticeApps.size) {
            noticePager.scrollToPage(noticeApps.lastIndex)
        }
        val current = shownNoticeApp ?: return@LaunchedEffect
        val at = noticeApps.getOrNull(noticePager.currentPage)?.packageName
        if (noticeApps.any { it.packageName == current } && at == current) return@LaunchedEffect
        val found = noticeApps.indexOfFirst { it.packageName == current }
        val target = if (found >= 0) found else noticePager.currentPage.coerceAtMost(noticeApps.lastIndex)
        if (noticePager.currentPage != target) noticePager.scrollToPage(target)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Forest)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (section == PHONE) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhoneMenu(
                    version = version,
                    onAreaCodes = {
                        areaCodesOpen = true
                        scope.launch { phonePager.animateScrollToPage(0) }
                    },
                    onCheckUpdates = {
                        val generation = updateCheck + 1
                        updateCheck = generation
                        updateNotice = UpdateNotice.Checking
                        val activity = context as? Activity
                        if (activity == null) {
                            updateNotice = UpdateNotice.Failed
                        } else {
                            AppUpdate.checkNow(activity) { result ->
                                if (updateCheck != generation) return@checkNow
                                updateNotice = when (result) {
                                    is AppUpdate.Check.Update -> UpdateNotice.Available(result.offer)
                                    AppUpdate.Check.Current -> UpdateNotice.Current
                                    AppUpdate.Check.Failed -> UpdateNotice.Failed
                                }
                            }
                        }
                    },
                )
                PageDots(
                    page = phonePager.currentPage,
                    count = 1 + calls.size,
                    color = PhoneBand,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(36.dp))
            }
            CardPager(
                state = phonePager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val line = calls.getOrNull(page - 1)
                PageCard(
                    seed = page,
                    title = when {
                        page == 0 && areaCodesOpen -> stringResource(R.string.area_codes)
                        line == null -> null
                        line.isIncoming() -> stringResource(R.string.incoming_call)
                        else -> stringResource(R.string.active_call)
                    },
                    band = PhoneBand,
                    ink = PhoneInk,
                    stroke = PhoneStroke,
                ) {
                    if (page == 0 && areaCodesOpen) {
                        AreaCodesPage(
                            codes = state.areaCodes,
                            onAdd = viewModel::addAreaCode,
                            onRemove = viewModel::removeAreaCode,
                            onClose = { areaCodesOpen = false },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (page == 0 || line == null) {
                        KeypadPane(
                            digits = state.digits,
                            contactName = state.dialContactName,
                            contactPhoto = state.dialContactPhoto,
                            hint = stringResource(R.string.keypad_hint),
                            backspaceLabel = stringResource(R.string.backspace),
                            callLabel = stringResource(R.string.call),
                            browseLabel = stringResource(R.string.browse),
                            onDigit = viewModel::appendDigit,
                            onPlus = viewModel::holdZeroForPlus,
                            onBackspace = viewModel::backspace,
                            onCall = { viewModel.requestCall() },
                            onBrowse = viewModel::browseContacts,
                            stroke = PhoneStroke,
                            accent = PhoneBand,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        CallCard(line, inCall, Modifier.fillMaxSize())
                    }
                }
            }
        } else if (section == HISTORY) {
            val recognizedRows = viewModel.historyRows(true, state)
            val unrecognizedRows = viewModel.historyRows(false, state)
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (recognizedRows.isNotEmpty() || unrecognizedRows.isNotEmpty()) {
                    CallsMenu(
                        showRecognized = recognizedRows.isNotEmpty(),
                        showUnrecognized = unrecognizedRows.isNotEmpty(),
                        onRecognized = { scope.launch { historyPager.animateScrollToPage(0) } },
                        onUnrecognized = { scope.launch { historyPager.animateScrollToPage(1) } },
                    )
                } else {
                    Box(Modifier.size(36.dp))
                }
                PageDots(
                    page = historyPager.currentPage,
                    count = 2,
                    color = HistoryBand,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(36.dp))
            }
            CardPager(
                state = historyPager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val recognized = page == 0
                PageCard(
                    seed = page + 20,
                    title = stringResource(
                        if (recognized) R.string.history_recognized else R.string.history_unrecognized,
                    ),
                    band = HistoryBand,
                    ink = HistoryInk,
                    stroke = HistoryStroke,
                    body = HistoryBody,
                    bleed = true,
                ) {
                    HistoryPage(
                        rows = if (recognized) recognizedRows else unrecognizedRows,
                        recognized = recognized,
                        access = state.historyAccess,
                        onCall = viewModel::requestCall,
                        onViewContact = viewModel::openContact,
                        onAddContact = viewModel::addContact,
                        onBecomeDialer = viewModel::requestDialerRole,
                        onAllow = viewModel::requestCallLogPermission,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else if (section == NOTICES) {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NoticesMenu(
                    apps = noticeApps,
                    onSystemHistory = viewModel::openNotificationHistory,
                    onDeleteAll = viewModel::dismissAllNotices,
                    ignored = state.ignoredSenders,
                    onRestore = viewModel::restoreSender,
                    onPick = { packageName ->
                        val index = noticeApps.indexOfFirst { it.packageName == packageName }
                        if (index >= 0) {
                            shownNoticeApp = packageName
                            scope.launch { noticePager.animateScrollToPage(index) }
                        }
                    },
                )
                PageDots(
                    page = noticePager.currentPage,
                    count = noticeApps.size.coerceAtLeast(1),
                    color = NoticeBand,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(36.dp))
            }
            CardPager(
                state = noticePager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val app = noticeApps.getOrNull(page)
                PageCard(
                    seed = app?.packageName?.hashCode() ?: 40,
                    title = app?.appName ?: stringResource(R.string.notices_title),
                    band = NoticeBand,
                    ink = NoticeInk,
                    stroke = NoticeStroke,
                    body = NoticeBody,
                    bleed = true,
                ) {
                    NoticesPage(
                        rows = if (app == null) emptyList() else viewModel.noticeRows(app.packageName, state),
                        access = state.noticeAccess,
                        onOpen = viewModel::openNotice,
                        onDismiss = viewModel::dismissNotice,
                        onDismissAll = { app?.let { viewModel.dismissApp(it.packageName) } },
                        onAllow = viewModel::requestNotificationAccess,
                        appName = app?.appName,
                        onIgnore = { app?.let { viewModel.ignoreSender(it.packageName) } },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            Row(
                Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MailMenu(
                    cards = mailCards,
                    onPick = { card ->
                        val index = mailCards.indexOf(card)
                        if (index >= 0) {
                            shownMailCard = card
                            scope.launch { mailPager.animateScrollToPage(index) }
                        }
                    },
                )
                PageDots(
                    page = mailPager.currentPage,
                    count = mailCards.size,
                    color = TitleBand,
                    modifier = Modifier.weight(1f),
                )
                Box(Modifier.size(36.dp))
            }
            CardPager(
                state = mailPager,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) { page ->
                val card = mailCards[page]
                PageCard(
                    seed = card.ordinal + 2,
                    title = mailTitle(card),
                    band = TitleBand,
                    ink = TitleInk,
                    stroke = DigitStroke,
                    bleed = card != MailboxTab.Stats,
                    overlay = {
                        if (card != MailboxTab.Stats) {
                            SpeakerButton(
                                on = state.speakerOn,
                                onClick = viewModel::toggleSpeaker,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 48.dp, end = 16.dp),
                            )
                        }
                    },
                ) {
                    if (card == MailboxTab.Stats) {
                        StatsPage(viewModel, state)
                    } else {
                        MailboxPage(viewModel, state, card)
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BottomTab(
                stringResource(R.string.tab_phone),
                fill = if (section == PHONE) PhoneBand else Forest,
                ink = if (section == PHONE) PhoneInk else PhoneBand,
                edge = PhoneStroke,
                modifier = Modifier.weight(1f),
                icon = { color -> PhoneTabIcon(color) },
            ) { section = PHONE }
            BottomTab(
                stringResource(R.string.tab_history),
                fill = if (section == HISTORY) HistoryBand else HistoryBody,
                ink = if (section == HISTORY) HistoryInk else HistoryBand,
                edge = HistoryStroke,
                modifier = Modifier.weight(1f),
                icon = { color -> HistoryTabIcon(color) },
            ) { section = HISTORY }
            BottomTab(
                stringResource(R.string.tab_notices),
                fill = if (section == NOTICES) NoticeBand else NoticeBody,
                ink = if (section == NOTICES) NoticeInk else NoticeBand,
                edge = NoticeStroke,
                modifier = Modifier.weight(1f),
                icon = { color -> NoticesTabIcon(color) },
            ) { section = NOTICES }
            BottomTab(
                stringResource(
                    R.string.tab_voicemail_count,
                    viewModel.knownCount(state) + viewModel.unknownCount(state),
                ),
                fill = if (section == VOICEMAIL) TitleBand else Forest,
                ink = if (section == VOICEMAIL) TitleInk else TitleBand,
                edge = DigitStroke,
                modifier = Modifier.weight(1f),
                icon = { color -> VoicemailTabIcon(color) },
            ) { section = VOICEMAIL }
        }
    }
    ConfirmDialogs(viewModel, state.pendingConfirm)
    UpdateDialog(
        notice = updateNotice,
        onDismiss = {
            updateCheck += 1
            updateNotice = null
        },
        onInstall = { offer ->
            updateCheck += 1
            updateNotice = null
            (context as? Activity)?.let { AppUpdate.startInstall(it, offer) }
        },
    )
    readyOffer?.let { offer ->
        ReadyUpdateDialog(
            versionName = offer.versionName,
            onLater = {
                readyOffer = null
                AppUpdate.dismiss(context, offer.versionName)
            },
            onInstall = {
                readyOffer = null
                (context as? Activity)?.let { AppUpdate.startInstall(it, offer) }
            },
            onDismiss = { readyOffer = null },
        )
    }
}

@Composable
private fun PageDots(page: Int, count: Int, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = sectionDots(page, count),
        color = color,
        fontSize = 14.sp,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun PageCard(
    seed: Int,
    title: String?,
    band: Color,
    ink: Color,
    stroke: Color,
    body: Color = Color.Transparent,
    bleed: Boolean = false,
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    RaggedCard(
        seed = seed,
        title = title,
        band = band,
        ink = ink,
        stroke = stroke,
        body = body,
        bleed = bleed,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        overlay = overlay,
    ) {
        content()
    }
}

private val tabShape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)

@Composable
private fun BottomTab(
    label: String,
    fill: Color,
    ink: Color,
    edge: Color,
    modifier: Modifier,
    icon: @Composable (Color) -> Unit,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .clip(tabShape)
            .background(fill)
            .border(1.dp, edge, tabShape)
            .clickable(onClick = onClick)
            .padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon(ink)
        Text(
            label,
            color = ink,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
private fun PhoneTabIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        drawRoundRect(
            color = color,
            topLeft = Offset(size.width * 0.28f, size.height * 0.06f),
            size = Size(size.width * 0.44f, size.height * 0.88f),
            cornerRadius = CornerRadius(size.width * 0.1f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.4f, size.height * 0.22f),
            end = Offset(size.width * 0.6f, size.height * 0.22f),
            strokeWidth = 1.6.dp.toPx(),
        )
    }
}

@Composable
private fun HistoryTabIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        drawCircle(
            color = color,
            radius = size.minDimension * 0.42f,
            center = center,
            style = stroke,
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(center.x, size.height * 0.22f),
            strokeWidth = 1.6.dp.toPx(),
        )
        drawLine(
            color = color,
            start = center,
            end = Offset(size.width * 0.72f, center.y),
            strokeWidth = 1.6.dp.toPx(),
        )
    }
}

@Composable
private fun NoticesTabIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        drawLine(
            color = color,
            start = Offset(size.width * 0.5f, size.height * 0.06f),
            end = Offset(size.width * 0.5f, size.height * 0.16f),
            strokeWidth = 1.6.dp.toPx(),
        )
        drawArc(
            color = color,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(size.width * 0.16f, size.height * 0.14f),
            size = Size(size.width * 0.68f, size.height * 0.62f),
            style = stroke,
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.62f),
            end = Offset(size.width * 0.82f, size.height * 0.62f),
            strokeWidth = 1.6.dp.toPx(),
        )
        drawCircle(
            color = color,
            radius = size.minDimension * 0.08f,
            center = Offset(size.width * 0.5f, size.height * 0.78f),
            style = stroke,
        )
    }
}

@Composable
private fun VoicemailTabIcon(color: Color) {
    Canvas(Modifier.size(22.dp)) {
        val stroke = Stroke(width = 1.6.dp.toPx())
        val radius = size.minDimension * 0.2f
        val cy = size.height * 0.58f
        drawCircle(color, radius, Offset(size.width * 0.28f, cy), style = stroke)
        drawCircle(color, radius, Offset(size.width * 0.72f, cy), style = stroke)
        drawLine(
            color = color,
            start = Offset(size.width * 0.28f, cy),
            end = Offset(size.width * 0.72f, cy),
            strokeWidth = 1.6.dp.toPx(),
        )
    }
}

@Composable
private fun mailTitle(card: MailboxTab): String {
    return when (card) {
        MailboxTab.Known -> stringResource(R.string.tab_known)
        MailboxTab.Unknown -> stringResource(R.string.tab_unknown)
        MailboxTab.Stats -> stringResource(R.string.tab_settings)
    }
}

private fun sectionDots(page: Int, count: Int): String {
    return (0 until count).joinToString(" ") { if (it == page) "●" else "○" }
}

@Composable
private fun DeletionCard(counts: DeletionCounts, title: String, color: Color) {
    StatTable(
        title = title,
        recognized = counts.known.toLong(),
        unrecognized = counts.unknown.toLong(),
        bulk = counts.bulk.toLong(),
        color = color,
    )
}

@Composable
private fun StatsPage(
    viewModel: DialViewModel,
    state: DialUiState,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            DeletionCard(viewModel.deletedIn(DialViewModel.weekWindow), stringResource(R.string.deleted_week_label), Accent)
            DeletionCard(viewModel.deletedIn(DialViewModel.monthWindow), stringResource(R.string.deleted_month_label), StatHoney)
            DeletionCard(viewModel.deletedIn(DialViewModel.yearWindow), stringResource(R.string.deleted_year_label), StatLilac)
            StatTable(
                title = stringResource(R.string.deleted_life_label),
                recognized = state.deletedKnown,
                unrecognized = state.deletedUnknown,
                bulk = state.deletedBulk,
                color = StatCoral,
            )
            StatsActions(
                isDialer = state.isDialer,
                showVoicemail = state.isDialer && !state.hasVoicemailPermission,
                showContacts = state.isDialer && !state.hasContactsPermission,
                becomeLabel = stringResource(R.string.become_dialer),
                voicemailLabel = stringResource(R.string.allow_voicemail),
                contactsLabel = stringResource(R.string.allow_contacts),
                onBecome = viewModel::requestDialerRole,
                onVoicemail = viewModel::requestVoicemailPermission,
                onContacts = viewModel::requestContactsPermission,
            )
        }
    }
}

private sealed interface UpdateNotice {
    data object Checking : UpdateNotice
    data object Current : UpdateNotice
    data class Available(val offer: ReleaseOffer) : UpdateNotice
    data object Failed : UpdateNotice
}

@Composable
private fun UpdateDialog(
    notice: UpdateNotice?,
    onDismiss: () -> Unit,
    onInstall: (ReleaseOffer) -> Unit,
) {
    if (notice == null) return
    val message = when (notice) {
        UpdateNotice.Checking -> stringResource(R.string.update_checking)
        UpdateNotice.Current -> stringResource(R.string.update_current)
        is UpdateNotice.Available -> stringResource(R.string.update_available, notice.offer.versionName)
        UpdateNotice.Failed -> stringResource(R.string.update_failed)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.check_for_updates)) },
        text = { Text(message) },
        confirmButton = {
            if (notice is UpdateNotice.Available) {
                TextButton(onClick = { onInstall(notice.offer) }) {
                    Text(stringResource(R.string.update_install))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

@Composable
private fun ReadyUpdateDialog(
    versionName: String,
    onLater: () -> Unit,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(stringResource(R.string.update_ready, versionName)) },
        confirmButton = {
            TextButton(onClick = onInstall) { Text(stringResource(R.string.update_install)) }
        },
        dismissButton = {
            TextButton(onClick = onLater) { Text(stringResource(R.string.update_later)) }
        },
    )
}

@Composable
private fun ConfirmDialogs(viewModel: DialViewModel, pending: PendingConfirm?) {
    when (pending) {
        is PendingConfirm.Bulk -> AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(R.string.delete_title)) },
            text = { Text(stringResource(R.string.delete_message, pending.count, pending.threshold)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.cancel)) }
            },
        )
        is PendingConfirm.One -> AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            title = { Text(stringResource(R.string.delete_one_title)) },
            text = { Text(stringResource(R.string.delete_one_message, pending.label)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmPending) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirm) { Text(stringResource(R.string.cancel)) }
            },
        )
        null -> Unit
    }
}

@Composable
private fun MailboxPage(viewModel: DialViewModel, state: DialUiState, tab: MailboxTab) {
    val rows = viewModel.visibleRows(state, tab)
    val bulk = viewModel.bulkCount(state, tab)
    MailboxPane(
        tab = tab,
        knownCount = viewModel.knownCount(state),
        unknownCount = viewModel.unknownCount(state),
        rows = rows,
        bulkCount = bulk,
        thresholdSeconds = state.thresholdSeconds,
        statusText = state.statusText,
        showSamsungNote = state.isDialer && state.scanned && !state.busy &&
            presentVoicemails(state.entries).isEmpty(),
        samsungNote = stringResource(R.string.samsung_note),
        thresholdLabel = stringResource(R.string.threshold_label),
        deleteLabel = pluralStringResource(R.plurals.delete_button, bulk, bulk),
        deleteEnabled = state.isDialer && !state.busy && bulk > 0,
        busy = state.busy,
        onTab = viewModel::showMailbox,
        onThreshold = viewModel::setThreshold,
        onDelete = { viewModel.askDeleteVisible(tab) },
        playingId = state.playingVoicemailId,
        playbackFraction = state.playbackFraction,
        onPlay = viewModel::play,
        onDeleteOne = viewModel::askDeleteOne,
        onViewContact = { viewModel.openContact(it.entry.number) },
        stats = {},
        showTabs = false,
        modifier = Modifier.fillMaxSize(),
    )
}
