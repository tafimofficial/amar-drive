package com.amar.gallery.data.tdlib

import android.content.Context
import android.util.Log
import com.amar.gallery.data.db.AmarDatabase
import com.amar.gallery.data.model.AuthState
import com.amar.gallery.data.model.ChannelItem
import com.amar.gallery.data.model.MediaItem
import com.amar.gallery.data.model.MediaKind
import com.amar.gallery.data.pref.AppPreferences
import com.amar.gallery.util.NativeEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FileProgress(
    val fileId: Int,
    val path: String?,
    val isCompleted: Boolean,
    val downloadOffset: Long,
    val downloadedPrefixSize: Long,
    val downloadedSize: Long,
    val expectedSize: Long
)

class TdClientManager private constructor(private val context: Context) : Client.ResultHandler {

    companion object {
        private const val TAG = "TdClientManager"
        private val API_ID = com.amar.gallery.BuildConfig.TELEGRAM_API_ID
        private val API_HASH = com.amar.gallery.BuildConfig.TELEGRAM_API_HASH

        @Volatile
        private var instance: TdClientManager? = null

        fun getInstance(context: Context): TdClientManager {
            return instance ?: synchronized(this) {
                instance ?: TdClientManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var client: Client? = null
    private val db = AmarDatabase.getInstance(context)
    private val prefs = AppPreferences.getInstance(context)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Initial)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _syncProgress = MutableStateFlow<String>("")
    val syncProgress: StateFlow<String> = _syncProgress.asStateFlow()

    private val localFilePaths = ConcurrentHashMap<Int, String>()
    private val completedFileIds = ConcurrentHashMap.newKeySet<Int>()
    private val fileProgressMap = ConcurrentHashMap<Int, FileProgress>()
    private val fileRangeTrackers = ConcurrentHashMap<Int, FileRangeTracker>()
    private val _fileEvents = MutableSharedFlow<Int>(extraBufferCapacity = 128)
    val fileEvents: SharedFlow<Int> = _fileEvents.asSharedFlow()

    fun getFileLocalPath(fileId: Int): String? = localFilePaths[fileId]
    fun isFileCompleted(fileId: Int): Boolean = completedFileIds.contains(fileId)
    fun getFileProgress(fileId: Int): FileProgress? = fileProgressMap[fileId]

    fun getAvailableEnd(fileId: Int, position: Long): Long {
        if (completedFileIds.contains(fileId)) {
            val path = localFilePaths[fileId]
            if (path != null) {
                val f = File(path)
                if (f.exists() && f.length() > 0) return maxOf(f.length(), position + 10 * 1024 * 1024L)
            }
            return Long.MAX_VALUE
        }
        val tracker = fileRangeTrackers[fileId] ?: return 0L
        return tracker.getAvailableEnd(position)
    }

    fun markFileCompleted(fileId: Int, size: Long = 0L) {
        completedFileIds.add(fileId)
        val tracker = fileRangeTrackers.computeIfAbsent(fileId) { FileRangeTracker() }
        tracker.markCompleted(size)
    }

    private var myUserId: Long = 0L
    private var pendingPhone: String = ""

    fun initialize() {
        if (client != null) return
        scope.launch {
            try {
                Client.execute(TdApi.SetLogVerbosityLevel(3))
                Client.setLogMessageHandler(3) { level, message ->
                    Log.d("TDLibNative", "[$level] $message")
                }
                client = Client.create(
                    this@TdClientManager,
                    { t -> Log.e(TAG, "TDLib update exception", t) },
                    { t -> Log.e(TAG, "TDLib default exception", t) }
                )
                Log.i(TAG, "TDLib client successfully created.")
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to initialize TDLib client", t)
                _authState.value = AuthState.Error("TDLib init error: ${t.message}")
            }
        }
    }

    fun handleFileUpdate(file: TdApi.File) {
        val local = file.local ?: return
        val p = if (!local.path.isNullOrEmpty()) local.path else null
        if (p != null) {
            localFilePaths[file.id] = p
        }
        val tracker = fileRangeTrackers.computeIfAbsent(file.id) { FileRangeTracker() }
        val actualSize = if (file.size > 0) file.size else file.expectedSize
        if (local.isDownloadingCompleted) {
            completedFileIds.add(file.id)
            val size = if (actualSize > 0) actualSize else (local.downloadOffset + local.downloadedPrefixSize)
            tracker.markCompleted(size)
            if (p != null) {
                scope.launch(Dispatchers.IO) {
                    db.updateLocalPath(file.id, p)
                }
            }
        } else if (local.downloadedPrefixSize > 0) {
            tracker.addRange(local.downloadOffset, local.downloadOffset + local.downloadedPrefixSize)
        }
        fileProgressMap[file.id] = FileProgress(
            fileId = file.id,
            path = p,
            isCompleted = local.isDownloadingCompleted,
            downloadOffset = local.downloadOffset,
            downloadedPrefixSize = local.downloadedPrefixSize,
            downloadedSize = local.downloadedSize,
            expectedSize = actualSize
        )
        Log.i("AmarStream", "handleFileUpdate: id=${file.id} path='$p' completed=${local.isDownloadingCompleted} offset=${local.downloadOffset} prefix=${local.downloadedPrefixSize} dlSize=${local.downloadedSize} actualSize=$actualSize")
        _fileEvents.tryEmit(file.id)
    }

    override fun onResult(obj: TdApi.Object?) {
        if (obj == null) return
        when (obj.constructor) {
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateAuthorizationState
                handleAuthState(update.authorizationState)
            }
            TdApi.UpdateConnectionState.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateConnectionState
                Log.i(TAG, "ConnectionState: ${update.state.javaClass.simpleName}")
            }
            TdApi.UpdateFile.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateFile
                handleFileUpdate(update.file)
            }
            TdApi.UpdateUser.CONSTRUCTOR -> {
                val update = obj as TdApi.UpdateUser
                if (update.user.id == myUserId) {
                    val name = update.user.firstName + (if (!update.user.lastName.isNullOrEmpty()) " " + update.user.lastName else "")
                    prefs.userName = name
                    prefs.userPhone = update.user.phoneNumber.orEmpty()
                }
            }
        }
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        Log.i(TAG, "handleAuthState: ${state.javaClass.simpleName}")
        when (state.constructor) {
            TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                val tdDir = File(context.filesDir, "td")
                val dbDir = File(tdDir, "db").apply { mkdirs() }
                val filesDir = File(tdDir, "files").apply { mkdirs() }

                val params = TdApi.SetTdlibParameters().apply {
                    useTestDc = false
                    databaseDirectory = dbDir.absolutePath
                    filesDirectory = filesDir.absolutePath
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = API_ID
                    apiHash = API_HASH
                    systemLanguageCode = "en"
                    deviceModel = android.os.Build.MODEL ?: "Android"
                    systemVersion = android.os.Build.VERSION.RELEASE ?: "13"
                    applicationVersion = "1.0.0"
                }

                send(params) { res ->
                    if (res is TdApi.Error) {
                        Log.e(TAG, "SetTdlibParameters error: ${res.message} [${res.code}]")
                        _authState.value = AuthState.Error(res.message)
                    }
                }
            }
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                _authState.value = AuthState.WaitPhone
            }
            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                _authState.value = AuthState.WaitCode(pendingPhone)
            }
            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                _authState.value = AuthState.WaitPassword(pendingPhone)
            }
            TdApi.AuthorizationStateWaitOtherDeviceConfirmation.CONSTRUCTOR -> {
                val other = state as TdApi.AuthorizationStateWaitOtherDeviceConfirmation
                Log.i(TAG, "AuthorizationStateWaitOtherDeviceConfirmation link: ${other.link}")
                _authState.value = AuthState.WaitQr(other.link)
            }
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                prefs.isLoggedIn = true
                send(TdApi.GetMe()) { res ->
                    if (res is TdApi.User) {
                        myUserId = res.id
                        val name = res.firstName + (if (!res.lastName.isNullOrEmpty()) " " + res.lastName else "")
                        prefs.userName = name
                        prefs.userPhone = res.phoneNumber.orEmpty()
                        _authState.value = AuthState.Ready(name, res.phoneNumber.orEmpty())
                        // Load channels
                        loadChannels()
                    }
                }
            }
            TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR,
            TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                prefs.isLoggedIn = false
                _authState.value = AuthState.Closed
            }
        }
    }

    fun sendPhoneNumber(phone: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPhone = phone.trim()
        pendingPhone = cleanPhone
        Log.i(TAG, "sendPhoneNumber called for: $cleanPhone")
        val settings = TdApi.PhoneNumberAuthenticationSettings().apply {
            allowFlashCall = false
            allowMissedCall = false
            isCurrentPhoneNumber = false
            allowSmsRetrieverApi = false
        }
        val func = TdApi.SetAuthenticationPhoneNumber().apply {
            phoneNumber = cleanPhone
            this.settings = settings
        }
        send(func) { res ->
            scope.launch(Dispatchers.Main) {
                if (res is TdApi.Ok) {
                    _authState.value = AuthState.WaitCode(cleanPhone)
                    onResult(true, null)
                } else if (res is TdApi.Error) {
                    Log.e(TAG, "Send phone error: ${res.message} [code: ${res.code}]")
                    onResult(false, res.message)
                }
            }
        }
    }

    fun sendCode(code: String, onResult: (Boolean, String?) -> Unit) {
        val cleanCode = code.trim()
        val func = TdApi.CheckAuthenticationCode().apply {
            this.code = cleanCode
        }
        send(func) { res ->
            scope.launch(Dispatchers.Main) {
                if (res is TdApi.Ok) {
                    onResult(true, null)
                } else if (res is TdApi.Error) {
                    Log.e(TAG, "Send code error: ${res.message} [code: ${res.code}]")
                    onResult(false, res.message)
                }
            }
        }
    }

    fun sendPassword(pwd: String, onResult: (Boolean, String?) -> Unit) {
        val cleanPwd = pwd.trim()
        val func = TdApi.CheckAuthenticationPassword().apply {
            this.password = cleanPwd
        }
        send(func) { res ->
            scope.launch(Dispatchers.Main) {
                if (res is TdApi.Ok) {
                    onResult(true, null)
                } else if (res is TdApi.Error) {
                    Log.e(TAG, "Send password error: ${res.message} [code: ${res.code}]")
                    onResult(false, res.message)
                }
            }
        }
    }

    fun requestQrCode() {
        val func = TdApi.RequestQrCodeAuthentication().apply {
            otherUserIds = longArrayOf()
        }
        send(func) {}
    }

    fun logOut(onComplete: () -> Unit) {
        send(TdApi.LogOut()) {
            prefs.clear()
            scope.launch(Dispatchers.IO) {
                db.clearAllData()
                withContext(Dispatchers.Main) {
                    _authState.value = AuthState.WaitPhone
                    onComplete()
                }
            }
        }
    }

    fun loadChannels(onDone: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            _syncProgress.value = "Discovering channels & groups..."
            if (myUserId == 0L) {
                val me = sendSuspend<TdApi.User>(TdApi.GetMe())
                if (me != null) myUserId = me.id
            }

            val loadChats = TdApi.LoadChats().apply {
                chatList = TdApi.ChatListMain()
                limit = 200
            }
            sendSuspend<TdApi.Ok>(loadChats)

            val getChats = TdApi.GetChats().apply {
                chatList = TdApi.ChatListMain()
                limit = 200
            }

            val chatsResult = sendSuspend<TdApi.Chats>(getChats)
            if (chatsResult != null) {
                for (chatId in chatsResult.chatIds) {
                    checkAndAddChat(chatId)
                }
            }
            _syncProgress.value = "Synced channels & groups"
            withContext(Dispatchers.Main) {
                onDone?.invoke()
            }
        }
    }

    private suspend fun checkAndAddChat(chatId: Long) {
        val chat = sendSuspend<TdApi.Chat>(TdApi.GetChat().apply { this.chatId = chatId }) ?: return

        // Verify if channel or group
        val (isChannel, isGroup) = when (val t = chat.type) {
            is TdApi.ChatTypeSupergroup -> Pair(t.isChannel, !t.isChannel)
            is TdApi.ChatTypeBasicGroup -> Pair(false, true)
            else -> Pair(false, false)
        }

        if (!isChannel && !isGroup) return

        if (myUserId == 0L) {
            val me = sendSuspend<TdApi.User>(TdApi.GetMe())
            if (me != null) myUserId = me.id
        }

        // Check Admin or Creator status
        var isAdminOrCreator = false
        if (isGroup && chat.type is TdApi.ChatTypeBasicGroup) {
            val bgId = (chat.type as TdApi.ChatTypeBasicGroup).basicGroupId
            val bg = sendSuspend<TdApi.BasicGroup>(TdApi.GetBasicGroup().apply { this.basicGroupId = bgId })
            if (bg?.status is TdApi.ChatMemberStatusAdministrator || bg?.status is TdApi.ChatMemberStatusCreator) {
                isAdminOrCreator = true
            }
        } else {
            val member = sendSuspend<TdApi.ChatMember>(
                TdApi.GetChatMember().apply {
                    this.chatId = chatId
                    this.memberId = TdApi.MessageSenderUser().apply { userId = myUserId }
                }
            )
            if (member?.status is TdApi.ChatMemberStatusAdministrator ||
                member?.status is TdApi.ChatMemberStatusCreator) {
                isAdminOrCreator = true
            }
        }

        val existing = db.getChannel(chatId)

        // For admin chats: default enabled = true
        // For joined member chats/groups: default enabled = false (so gallery is not spammed, but shows in "Joined Channels & Groups"!)
        val defaultEnabled = isAdminOrCreator
        val enabledState = existing?.enabled ?: defaultEnabled

        val channel = ChannelItem(
            chatId = chatId,
            title = chat.title,
            username = null,
            photoPath = chat.photo?.small?.local?.path ?: existing?.photoPath,
            enabled = enabledState,
            isAdmin = isAdminOrCreator,
            isGroup = isGroup,
            totalMedia = existing?.totalMedia ?: 0,
            lastSyncAt = System.currentTimeMillis()
        )
        db.upsertChannel(channel)

        // Only sync media if the channel/group is currently enabled
        if (enabledState) {
            syncChannelMedia(chatId)
        }
    }

    fun addChatByQuery(query: String, onResult: (Boolean, String?) -> Unit) {
        scope.launch(Dispatchers.IO) {
            val clean = query.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("t.me/")
                .removePrefix("@")

            val numericId = clean.toLongOrNull()
            if (numericId != null) {
                val chat = sendSuspend<TdApi.Chat>(TdApi.GetChat().apply { this.chatId = numericId })
                if (chat != null) {
                    val (isChan, isGrp) = when (val t = chat.type) {
                        is TdApi.ChatTypeSupergroup -> Pair(t.isChannel, !t.isChannel)
                        is TdApi.ChatTypeBasicGroup -> Pair(false, true)
                        else -> Pair(false, false)
                    }
                    if (isChan || isGrp) {
                        val item = ChannelItem(
                            chatId = chat.id,
                            title = chat.title,
                            username = null,
                            photoPath = chat.photo?.small?.local?.path,
                            enabled = true,
                            isAdmin = true,
                            isGroup = isGrp,
                            totalMedia = 0,
                            lastSyncAt = System.currentTimeMillis()
                        )
                        db.upsertChannel(item)
                        syncChannelMedia(chat.id)
                        withContext(Dispatchers.Main) { onResult(true, null) }
                        return@launch
                    }
                }
            }

            val publicChat = sendSuspend<TdApi.Chat>(TdApi.SearchPublicChat().apply { this.username = clean })
            if (publicChat != null) {
                val (isChan, isGrp) = when (val t = publicChat.type) {
                    is TdApi.ChatTypeSupergroup -> Pair(t.isChannel, !t.isChannel)
                    is TdApi.ChatTypeBasicGroup -> Pair(false, true)
                    else -> Pair(false, false)
                }
                val item = ChannelItem(
                    chatId = publicChat.id,
                    title = publicChat.title,
                    username = clean,
                    photoPath = publicChat.photo?.small?.local?.path,
                    enabled = true,
                    isAdmin = true,
                    isGroup = isGrp,
                    totalMedia = 0,
                    lastSyncAt = System.currentTimeMillis()
                )
                db.upsertChannel(item)
                syncChannelMedia(publicChat.id)
                withContext(Dispatchers.Main) { onResult(true, null) }
                return@launch
            }

            withContext(Dispatchers.Main) {
                onResult(false, "Could not find channel or group: $query")
            }
        }
    }

    fun syncChannelMedia(chatId: Long) {
        scope.launch(Dispatchers.IO) {
            try {
                _syncProgress.value = "Indexing channel $chatId..."
                val mediaList = mutableListOf<MediaItem>()

                // 1. Photos & Videos (paginated)
                val photosAndVideos = fetchMessagesWithFilter(chatId, TdApi.SearchMessagesFilterPhotoAndVideo())
                mediaList.addAll(photosAndVideos)
                _syncProgress.value = "Fetched ${mediaList.size} photos & videos..."

                // 2. Documents & Files (includes photos & videos sent as files, paginated)
                val docs = fetchMessagesWithFilter(chatId, TdApi.SearchMessagesFilterDocument())
                mediaList.addAll(docs)
                _syncProgress.value = "Fetched ${mediaList.size} items (including files)..."

                // 3. Animations & GIFs
                val animations = fetchMessagesWithFilter(chatId, TdApi.SearchMessagesFilterAnimation())
                mediaList.addAll(animations)

                // 4. Audio
                val audio = fetchMessagesWithFilter(chatId, TdApi.SearchMessagesFilterAudio())
                mediaList.addAll(audio)

                if (mediaList.isNotEmpty()) {
                    db.upsertMedia(mediaList)
                    db.getChannel(chatId)?.let { ch ->
                        db.upsertChannel(ch.copy(totalMedia = db.getMediaCount(chatId, MediaKind.ALL)))
                    }
                }
                _syncProgress.value = "Synced ${mediaList.size} media items"
            } catch (t: Throwable) {
                Log.e(TAG, "Error syncing channel $chatId", t)
            }
        }
    }

    private suspend fun fetchMessagesWithFilter(
        chatId: Long,
        filter: TdApi.SearchMessagesFilter,
        maxCount: Int = 3000
    ): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        var fromMessageId = 0L

        while (result.size < maxCount) {
            val req = TdApi.SearchChatMessages().apply {
                this.chatId = chatId
                this.query = ""
                this.fromMessageId = fromMessageId
                this.offset = 0
                this.limit = 100
                this.filter = filter
            }
            val found = sendSuspend<TdApi.FoundChatMessages>(req) ?: break
            if (found.messages.isEmpty()) break

            for (msg in found.messages) {
                val item = parseMessage(msg)
                if (item != null) result.add(item)
            }

            if (found.messages.size < 100) break
            val lastId = found.messages.last().id
            if (lastId == fromMessageId) break
            fromMessageId = lastId
        }
        return result
    }

    private fun parseMessage(msg: TdApi.Message): MediaItem? {
        val chatId = msg.chatId
        val messageId = msg.id
        val date = msg.date.toLong()

        when (val content = msg.content) {
            is TdApi.MessagePhoto -> {
                val photo = content.photo
                val minithumb = photo.minithumbnail?.data
                val largest = photo.sizes.maxByOrNull { it.width * it.height } ?: photo.sizes.lastOrNull() ?: return null
                // Pick medium thumbnail ('m' ~320px or 'x' ~800px, avoiding the tiny blurry 's' 100px)
                val mediumThumb = photo.sizes.find { it.type == "m" }
                    ?: photo.sizes.find { it.type == "x" }
                    ?: photo.sizes.firstOrNull { it.width in 200..640 }
                    ?: photo.sizes.firstOrNull()
                val thumb = mediumThumb
                val localPath = largest.photo.local?.path
                val thumbPath = thumb?.photo?.local?.path

                if (!localPath.isNullOrEmpty()) {
                    localFilePaths[largest.photo.id] = localPath
                    if (largest.photo.local?.isDownloadingCompleted == true) {
                        completedFileIds.add(largest.photo.id)
                    }
                }
                if (!thumbPath.isNullOrEmpty() && thumb != null) {
                    localFilePaths[thumb.photo.id] = thumbPath
                }
                if (thumb != null && thumb.photo.local?.isDownloadingCompleted == false) {
                    downloadFile(thumb.photo.id, priority = 2)
                }

                return MediaItem(
                    chatId = chatId,
                    messageId = messageId,
                    uniqueId = largest.photo.remote.uniqueId,
                    fileId = largest.photo.id,
                    thumbFileId = thumb?.photo?.id,
                    minithumb = minithumb,
                    kind = MediaKind.PHOTO,
                    mime = "image/jpeg",
                    fileName = null,
                    sizeBytes = largest.photo.size,
                    width = largest.width,
                    height = largest.height,
                    durationSec = 0,
                    date = date,
                    caption = content.caption?.text,
                    localPath = localPath,
                    thumbPath = thumbPath
                )
            }
            is TdApi.MessageVideo -> {
                val vid = content.video
                val minithumb = vid.minithumbnail?.data
                val localPath = vid.video.local?.path
                val vidThumb = vid.thumbnail
                val thumbPath = vidThumb?.file?.local?.path

                if (!localPath.isNullOrEmpty()) {
                    localFilePaths[vid.video.id] = localPath
                    if (vid.video.local?.isDownloadingCompleted == true) {
                        markFileCompleted(vid.video.id, vid.video.size)
                    } else if (vid.video.local != null && vid.video.local!!.downloadedPrefixSize > 0) {
                        val tr = fileRangeTrackers.computeIfAbsent(vid.video.id) { FileRangeTracker() }
                        tr.addRange(vid.video.local!!.downloadOffset, vid.video.local!!.downloadOffset + vid.video.local!!.downloadedPrefixSize)
                    }
                }
                if (!thumbPath.isNullOrEmpty() && vidThumb != null) {
                    localFilePaths[vidThumb.file.id] = thumbPath
                }
                if (vidThumb != null && vidThumb.file.local?.isDownloadingCompleted == false) {
                    downloadFile(vidThumb.file.id, priority = 1)
                }

                return MediaItem(
                    chatId = chatId,
                    messageId = messageId,
                    uniqueId = vid.video.remote.uniqueId,
                    fileId = vid.video.id,
                    thumbFileId = vidThumb?.file?.id,
                    minithumb = minithumb,
                    kind = MediaKind.VIDEO,
                    mime = vid.mimeType,
                    fileName = vid.fileName,
                    sizeBytes = vid.video.size,
                    width = vid.width,
                    height = vid.height,
                    durationSec = vid.duration,
                    date = date,
                    caption = content.caption?.text,
                    localPath = localPath,
                    thumbPath = thumbPath
                )
            }
            is TdApi.MessageAnimation -> {
                val anim = content.animation
                val minithumb = anim.minithumbnail?.data
                val localPath = anim.animation.local?.path
                val animThumb = anim.thumbnail
                val thumbPath = animThumb?.file?.local?.path

                if (!localPath.isNullOrEmpty()) {
                    localFilePaths[anim.animation.id] = localPath
                    if (anim.animation.local?.isDownloadingCompleted == true) {
                        markFileCompleted(anim.animation.id, anim.animation.size)
                    } else if (anim.animation.local != null && anim.animation.local!!.downloadedPrefixSize > 0) {
                        val tr = fileRangeTrackers.computeIfAbsent(anim.animation.id) { FileRangeTracker() }
                        tr.addRange(anim.animation.local!!.downloadOffset, anim.animation.local!!.downloadOffset + anim.animation.local!!.downloadedPrefixSize)
                    }
                }
                if (!thumbPath.isNullOrEmpty() && animThumb != null) {
                    localFilePaths[animThumb.file.id] = thumbPath
                }
                if (animThumb != null && animThumb.file.local?.isDownloadingCompleted == false) {
                    downloadFile(animThumb.file.id, priority = 2)
                }

                return MediaItem(
                    chatId = chatId,
                    messageId = messageId,
                    uniqueId = anim.animation.remote.uniqueId,
                    fileId = anim.animation.id,
                    thumbFileId = animThumb?.file?.id,
                    minithumb = minithumb,
                    kind = MediaKind.ANIMATION,
                    mime = anim.mimeType,
                    fileName = anim.fileName,
                    sizeBytes = anim.animation.size,
                    width = anim.width,
                    height = anim.height,
                    durationSec = anim.duration,
                    date = date,
                    caption = content.caption?.text,
                    localPath = localPath,
                    thumbPath = thumbPath
                )
            }
            is TdApi.MessageDocument -> {
                val doc = content.document
                val minithumb = doc.minithumbnail?.data
                val kindCode = NativeEngine.getMediaKind(doc.mimeType, doc.fileName)
                var kind = MediaKind.fromCode(kindCode)

                val mimeLower = doc.mimeType?.lowercase().orEmpty()
                val nameLower = doc.fileName?.lowercase().orEmpty()
                if (kind == MediaKind.FILE) {
                    if (mimeLower.startsWith("image/") ||
                        nameLower.endsWith(".jpg") || nameLower.endsWith(".jpeg") ||
                        nameLower.endsWith(".png") || nameLower.endsWith(".webp") ||
                        nameLower.endsWith(".heic") || nameLower.endsWith(".heif") ||
                        nameLower.endsWith(".dng") || nameLower.endsWith(".bmp") ||
                        nameLower.endsWith(".svg") || nameLower.endsWith(".ico") ||
                        nameLower.endsWith(".raw") || nameLower.endsWith(".cr2") ||
                        nameLower.endsWith(".nef") || nameLower.endsWith(".arw") ||
                        nameLower.endsWith(".tif") || nameLower.endsWith(".tiff")
                    ) {
                        kind = MediaKind.PHOTO
                    } else if (mimeLower.startsWith("video/") ||
                        nameLower.endsWith(".mp4") || nameLower.endsWith(".mkv") ||
                        nameLower.endsWith(".mov") || nameLower.endsWith(".webm") ||
                        nameLower.endsWith(".avi") || nameLower.endsWith(".3gp") ||
                        nameLower.endsWith(".flv") || nameLower.endsWith(".ts") ||
                        nameLower.endsWith(".wmv") || nameLower.endsWith(".m4v") ||
                        nameLower.endsWith(".mpg") || nameLower.endsWith(".mpeg") ||
                        nameLower.endsWith(".3gpp") || nameLower.endsWith(".m2ts")
                    ) {
                        kind = MediaKind.VIDEO
                    }
                }
                val localPath = doc.document.local?.path
                val docThumb = doc.thumbnail
                val thumbPath = docThumb?.file?.local?.path

                if (!localPath.isNullOrEmpty()) {
                    localFilePaths[doc.document.id] = localPath
                    if (doc.document.local?.isDownloadingCompleted == true) {
                        markFileCompleted(doc.document.id, doc.document.size)
                    } else if (doc.document.local != null && doc.document.local!!.downloadedPrefixSize > 0) {
                        val tr = fileRangeTrackers.computeIfAbsent(doc.document.id) { FileRangeTracker() }
                        tr.addRange(doc.document.local!!.downloadOffset, doc.document.local!!.downloadOffset + doc.document.local!!.downloadedPrefixSize)
                    }
                }
                if (!thumbPath.isNullOrEmpty() && docThumb != null) {
                    localFilePaths[docThumb.file.id] = thumbPath
                }

                // If document is classified as PHOTO or VIDEO, prefetch thumbnail
                if (kind == MediaKind.PHOTO || kind == MediaKind.VIDEO) {
                    if (docThumb != null && docThumb.file.local?.isDownloadingCompleted == false) {
                        downloadFile(docThumb.file.id, priority = 1)
                    }
                }

                return MediaItem(
                    chatId = chatId,
                    messageId = messageId,
                    uniqueId = doc.document.remote.uniqueId,
                    fileId = doc.document.id,
                    thumbFileId = docThumb?.file?.id,
                    minithumb = minithumb,
                    kind = kind,
                    mime = doc.mimeType,
                    fileName = doc.fileName,
                    sizeBytes = doc.document.size,
                    width = 0,
                    height = 0,
                    durationSec = 0,
                    date = date,
                    caption = content.caption?.text,
                    localPath = localPath,
                    thumbPath = thumbPath
                )
            }
            is TdApi.MessageAudio -> {
                val audio = content.audio
                return MediaItem(
                    chatId = chatId,
                    messageId = messageId,
                    uniqueId = audio.audio.remote.uniqueId,
                    fileId = audio.audio.id,
                    thumbFileId = audio.albumCoverThumbnail?.file?.id,
                    minithumb = null,
                    kind = MediaKind.AUDIO,
                    mime = audio.mimeType,
                    fileName = audio.fileName ?: audio.title,
                    sizeBytes = audio.audio.size,
                    width = 0,
                    height = 0,
                    durationSec = audio.duration,
                    date = date,
                    caption = content.caption?.text,
                    localPath = audio.audio.local?.path
                )
            }
            else -> return null
        }
    }

    fun downloadFile(fileId: Int, priority: Int = 1) {
        downloadFileOffset(fileId, offset = 0L, limit = 0L, priority = priority)
    }

    fun downloadFileOffset(fileId: Int, offset: Long = 0L, limit: Long = 0L, priority: Int = 32) {
        val func = TdApi.DownloadFile().apply {
            this.fileId = fileId
            this.priority = priority
            this.offset = offset
            this.limit = limit
            this.synchronous = false
        }
        send(func) { res ->
            if (res is TdApi.File) {
                handleFileUpdate(res)
            }
        }
    }

    fun requestFile(fileId: Int) {
        send(TdApi.GetFile().apply { this.fileId = fileId }) { res ->
            if (res is TdApi.File) {
                handleFileUpdate(res)
            }
        }
    }

    fun readFilePartSync(fileId: Int, offset: Long, count: Long, timeoutMs: Long = 500): ByteArray? {
        val latch = java.util.concurrent.CountDownLatch(1)
        var resultData: ByteArray? = null
        val req = TdApi.ReadFilePart().apply {
            this.fileId = fileId
            this.offset = offset
            this.count = count
        }
        send(req) { res ->
            if (res is TdApi.Data) {
                resultData = res.data
            }
            latch.countDown()
        }
        try {
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {}
        return resultData
    }

    fun getFreshMediaInfoSync(chatId: Long, messageId: Long, timeoutMs: Long = 2000): Pair<Int, String?>? {
        val latch = java.util.concurrent.CountDownLatch(1)
        var resultPair: Pair<Int, String?>? = null
        val req = TdApi.GetMessage().apply {
            this.chatId = chatId
            this.messageId = messageId
        }
        send(req) { res ->
            if (res is TdApi.Message) {
                val parsed = parseMessage(res)
                if (parsed != null) {
                    resultPair = Pair(parsed.fileId, parsed.localPath)
                    scope.launch(Dispatchers.IO) {
                        db.updateMediaFileId(chatId, messageId, parsed.fileId, parsed.thumbFileId)
                        if (!parsed.localPath.isNullOrEmpty()) {
                            db.updateLocalPath(parsed.fileId, parsed.localPath)
                        }
                    }
                }
            }
            latch.countDown()
        }
        try {
            latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {}
        return resultPair
    }

    fun startStreamingFile(fileId: Int) {
        downloadFileOffset(fileId, offset = 0L, limit = 0L, priority = 32)
    }

    fun send(function: TdApi.Function<*>, handler: (TdApi.Object) -> Unit) {
        val c = client
        if (c == null) {
            Log.e(TAG, "send() called but client is null: ${function.javaClass.simpleName}")
            scope.launch(Dispatchers.Main) {
                handler(TdApi.Error(500, "Telegram Client is initializing, please wait..."))
            }
            return
        }
        Log.i(TAG, "send() -> ${function.javaClass.simpleName}")
        c.send(function) { result ->
            Log.i(TAG, "onResult for ${function.javaClass.simpleName} -> ${result.javaClass.simpleName}")
            handler(result)
        }
    }

    private suspend inline fun <reified T : TdApi.Object> sendSuspend(function: TdApi.Function<*>): T? =
        suspendCancellableCoroutine { cont ->
            send(function) { res ->
                if (res is T) {
                    cont.resume(res)
                } else {
                    cont.resume(null)
                }
            }
        }
}

