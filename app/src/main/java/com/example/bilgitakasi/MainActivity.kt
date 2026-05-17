package com.example.bilgitakasi

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BilgiTakasiApp()
        }
    }
}

data class Listing(
    val id: String = "",
    val userId: String = "",
    val username: String = "",
    val city: String = "",
    val title: String = "",
    val description: String = "",
    val offeredSkill: String = "",
    val wantedSkill: String = "",
    val category: String = "",
    val level: String = "",
    val matchCount: Int = 0,
    val emoji: String = "✨",
    val createdAt: Long = System.currentTimeMillis()
)

data class AppUser(
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val username: String = "",
    val city: String = "",
    val knownSkills: List<String> = emptyList(),
    val wantedSkills: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class Chat(
    val id: String = "",
    val users: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val listingId: String = "",
    val listingTitle: String = "",
    val lastMessage: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

data class ChatMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

enum class Screen(val title: String) {
    HOME("Ana Sayfa"),
    CREATE("İlan Ver"),
    MATCHES("Eşleşmeler"),
    MESSAGES("Mesajlar"),
    PROFILE("Profil")
}

class FirebaseRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun currentUserId(): String? = auth.currentUser?.uid

    fun currentEmail(): String = auth.currentUser?.email ?: "kullanici@mail.com"

    suspend fun register(email: String, password: String) {
        auth.createUserWithEmailAndPassword(email, password).await()
    }

    suspend fun login(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password).await()
    }

    fun logout() {
        auth.signOut()
    }

    suspend fun saveUserProfile(user: AppUser) {
        val uid = currentUserId() ?: return

        db.collection("users")
            .document(uid)
            .set(user.copy(id = uid))
            .await()
    }

    suspend fun getUserProfile(): AppUser? {
        val uid = currentUserId() ?: return null

        val doc = db.collection("users")
            .document(uid)
            .get()
            .await()

        return doc.toObject(AppUser::class.java)?.copy(id = doc.id)
    }

    suspend fun updateUserProfile(
        fullName: String,
        username: String,
        city: String,
        knownSkills: List<String>,
        wantedSkills: List<String>
    ) {
        val uid = currentUserId() ?: return

        db.collection("users")
            .document(uid)
            .update(
                mapOf(
                    "fullName" to fullName,
                    "username" to username,
                    "city" to city,
                    "knownSkills" to knownSkills,
                    "wantedSkills" to wantedSkills
                )
            )
            .await()
    }

    suspend fun addListing(listing: Listing) {
        db.collection("listings")
            .add(listing)
            .await()
    }

    suspend fun getListings(): List<Listing> {
        val snapshot = db.collection("listings")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Listing::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun addFavorite(listingId: String) {
        val uid = currentUserId() ?: return

        db.collection("users")
            .document(uid)
            .collection("favorites")
            .document(listingId)
            .set(
                mapOf(
                    "listingId" to listingId,
                    "createdAt" to System.currentTimeMillis()
                )
            )
            .await()
    }

    suspend fun removeFavorite(listingId: String) {
        val uid = currentUserId() ?: return

        db.collection("users")
            .document(uid)
            .collection("favorites")
            .document(listingId)
            .delete()
            .await()
    }

    suspend fun getFavorites(): List<String> {
        val uid = currentUserId() ?: return emptyList()

        val snapshot = db.collection("users")
            .document(uid)
            .collection("favorites")
            .get()
            .await()

        return snapshot.documents.map { it.id }
    }

    suspend fun createOrOpenChat(listing: Listing): String {
        val currentUid = currentUserId() ?: return ""

        val existing = db.collection("chats")
            .whereArrayContains("users", currentUid)
            .get()
            .await()
            .documents
            .firstOrNull { doc ->
                val chat = doc.toObject(Chat::class.java)
                chat?.listingId == listing.id && chat.users.contains(listing.userId)
            }

        if (existing != null) {
            return existing.id
        }

        val currentProfile = getUserProfile()

        val chat = Chat(
            users = listOf(currentUid, listing.userId),
            participantNames = mapOf(
                currentUid to (currentProfile?.username ?: currentEmail().substringBefore("@")),
                listing.userId to listing.username
            ),
            listingId = listing.id,
            listingTitle = listing.title,
            lastMessage = "",
            updatedAt = System.currentTimeMillis()
        )

        val newChat = db.collection("chats")
            .add(chat)
            .await()

        return newChat.id
    }

    suspend fun getMyChats(): List<Chat> {
        val uid = currentUserId() ?: return emptyList()

        val snapshot = db.collection("chats")
            .whereArrayContains("users", uid)
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(Chat::class.java)?.copy(id = doc.id)
        }.sortedByDescending { it.updatedAt }
    }

    suspend fun getMessages(chatId: String): List<ChatMessage> {
        val snapshot = db.collection("chats")
            .document(chatId)
            .collection("messages")
            .orderBy("createdAt")
            .get()
            .await()

        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(ChatMessage::class.java)?.copy(id = doc.id)
        }
    }

    suspend fun sendMessage(chatId: String, text: String) {
        val uid = currentUserId() ?: return

        val message = ChatMessage(
            senderId = uid,
            text = text,
            createdAt = System.currentTimeMillis()
        )

        db.collection("chats")
            .document(chatId)
            .collection("messages")
            .add(message)
            .await()

        db.collection("chats")
            .document(chatId)
            .update(
                mapOf(
                    "lastMessage" to text,
                    "updatedAt" to System.currentTimeMillis()
                )
            )
            .await()
    }
}

@Composable
fun BilgiTakasiApp() {
    val repo = remember { FirebaseRepository() }

    var isLoggedIn by remember { mutableStateOf(repo.currentUserId() != null) }
    var profileChecked by remember { mutableStateOf(false) }
    var profileCompleted by remember { mutableStateOf(false) }

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            val profile = repo.getUserProfile()
            profileCompleted = profile != null
            profileChecked = true
        } else {
            profileChecked = true
            profileCompleted = false
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF15C8D8),
            secondary = Color(0xFF8A5CFF),
            background = Color(0xFFF7FAFF),
            surface = Color.White,
            onPrimary = Color.White,
            onSurface = Color(0xFF111936)
        )
    ) {
        when {
            !profileChecked -> {
                LoadingScreen()
            }

            !isLoggedIn -> {
                AuthScreen(
                    repo = repo,
                    onSuccess = {
                        isLoggedIn = true
                        profileChecked = false
                    }
                )
            }

            !profileCompleted -> {
                ProfileSetupScreen(
                    repo = repo,
                    onFinished = {
                        profileCompleted = true
                    }
                )
            }

            else -> {
                MainAppScreen(
                    repo = repo,
                    onLogout = {
                        repo.logout()
                        isLoggedIn = false
                        profileCompleted = false
                        profileChecked = true
                    }
                )
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush()),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun AuthScreen(
    repo: FirebaseRepository,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoginMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush())
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(34.dp),
            color = Color.White.copy(alpha = 0.96f),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Bilgi Takası",
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF101936)
                )

                Text(
                    text = "Bildiğini paylaş, öğrenmek istediğini bul.",
                    color = Color(0xFF6D7894)
                )

                Spacer(modifier = Modifier.height(28.dp))

                AppTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "E-posta",
                    placeholder = "ornek@mail.com"
                )

                Spacer(modifier = Modifier.height(14.dp))

                Column {
                    Text(
                        text = "Şifre",
                        color = Color(0xFF101936),
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(7.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        placeholder = { Text("En az 6 karakter") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF15C8D8),
                            unfocusedBorderColor = Color(0xFFE1E6F2),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = {
                        if (email.isBlank() || password.length < 6) {
                            Toast.makeText(
                                context,
                                "E-posta ve en az 6 karakter şifre gir.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@Button
                        }

                        scope.launch {
                            loading = true
                            try {
                                if (isLoginMode) {
                                    repo.login(email, password)
                                } else {
                                    repo.register(email, password)
                                }
                                onSuccess()
                            } catch (e: Exception) {
                                Toast.makeText(
                                    context,
                                    e.message ?: "Hata oluştu",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            loading = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    GradientButtonContent(
                        text = if (loading) "Bekle..." else if (isLoginMode) "Giriş Yap" else "Kayıt Ol"
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                TextButton(
                    onClick = { isLoginMode = !isLoginMode }
                ) {
                    Text(
                        text = if (isLoginMode) "Hesabın yok mu? Kayıt ol" else "Zaten hesabın var mı? Giriş yap",
                        color = Color(0xFF6E5CFF),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(
    repo: FirebaseRepository,
    onFinished: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("İstanbul") }
    var knownText by remember { mutableStateOf("") }
    var wantedText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush()),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenTitle(
                title = "Profilini Tamamla",
                subtitle = "Sana uygun bilgi takaslarını bulabilmemiz için birkaç bilgi gerekli."
            )
        }

        item {
            FormCard {
                AppTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = "Ad Soyad",
                    placeholder = "Örn. Rümeysa Acar"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Kullanıcı Adı",
                    placeholder = "Örn. rumeysa.dev"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = "Yaşadığın Şehir",
                    placeholder = "Örn. İstanbul"
                )
            }
        }

        item {
            FormCard {
                AppTextField(
                    value = knownText,
                    onValueChange = { knownText = it },
                    label = "Bildiklerin",
                    placeholder = "Örn. Kotlin, İngilizce, Excel"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = wantedText,
                    onValueChange = { wantedText = it },
                    label = "Öğrenmek İstediklerin",
                    placeholder = "Örn. Photoshop, Video Edit, UI/UX"
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Virgülle ayırarak yazabilirsin.",
                    color = Color(0xFF6D7894),
                    fontSize = 13.sp
                )
            }
        }

        item {
            Button(
                onClick = {
                    if (fullName.isBlank() || username.isBlank()) {
                        Toast.makeText(
                            context,
                            "Ad soyad ve kullanıcı adı zorunlu.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    scope.launch {
                        loading = true
                        try {
                            val user = AppUser(
                                email = repo.currentEmail(),
                                fullName = fullName,
                                username = username,
                                city = city,
                                knownSkills = splitSkills(knownText),
                                wantedSkills = splitSkills(wantedText)
                            )

                            repo.saveUserProfile(user)
                            onFinished()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "Profil kaydedilemedi",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        loading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                GradientButtonContent(
                    text = if (loading) "Kaydediliyor..." else "Profili Kaydet"
                )
            }
        }
    }
}

@Composable
fun MainAppScreen(
    repo: FirebaseRepository,
    onLogout: () -> Unit
) {
    var selectedScreen by remember { mutableStateOf(Screen.HOME) }
    var showEditProfile by remember { mutableStateOf(false) }

    var listings by remember { mutableStateOf<List<Listing>>(emptyList()) }
    var favorites by remember { mutableStateOf<List<String>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    fun refreshData() {
        scope.launch {
            loading = true
            try {
                listings = repo.getListings()
                favorites = repo.getFavorites()
            } catch (e: Exception) {
                Toast.makeText(
                    context,
                    e.message ?: "Veriler alınamadı",
                    Toast.LENGTH_LONG
                ).show()
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush())
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 88.dp)
        ) {
            when {
                loading -> {
                    LoadingScreen()
                }

                showEditProfile -> {
                    EditProfileScreen(
                        repo = repo,
                        onBack = { showEditProfile = false },
                        onSaved = {
                            showEditProfile = false
                            refreshData()
                        }
                    )
                }

                else -> {
                    when (selectedScreen) {
                        Screen.HOME -> HomeScreen(
                            listings = listings,
                            favorites = favorites,
                            onFavoriteClick = { listingId ->
                                scope.launch {
                                    if (favorites.contains(listingId)) {
                                        repo.removeFavorite(listingId)
                                    } else {
                                        repo.addFavorite(listingId)
                                    }
                                    favorites = repo.getFavorites()
                                }
                            },
                            onMessageClick = { listing ->
                                scope.launch {
                                    if (listing.userId == repo.currentUserId()) {
                                        Toast.makeText(
                                            context,
                                            "Kendi ilanınla mesajlaşamazsın.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else if (listing.userId.isBlank()) {
                                        Toast.makeText(
                                            context,
                                            "Bu ilanın sahibi bulunamadı.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        repo.createOrOpenChat(listing)
                                        Toast.makeText(
                                            context,
                                            "Mesajlar sekmesinden konuşmaya devam edebilirsin.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        selectedScreen = Screen.MESSAGES
                                    }
                                }
                            }
                        )

                        Screen.CREATE -> CreateListingScreen(
                            repo = repo,
                            onAdded = {
                                Toast.makeText(
                                    context,
                                    "İlan yayınlandı",
                                    Toast.LENGTH_SHORT
                                ).show()
                                refreshData()
                                selectedScreen = Screen.HOME
                            }
                        )

                        Screen.MATCHES -> MatchesScreen(
                            listings = listings,
                            onMessageClick = { listing ->
                                scope.launch {
                                    if (listing.userId == repo.currentUserId()) {
                                        Toast.makeText(
                                            context,
                                            "Kendi ilanınla mesajlaşamazsın.",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    } else {
                                        repo.createOrOpenChat(listing)
                                        selectedScreen = Screen.MESSAGES
                                    }
                                }
                            }
                        )

                        Screen.MESSAGES -> MessagesScreen(repo = repo)

                        Screen.PROFILE -> ProfileScreen(
                            repo = repo,
                            listings = listings,
                            favorites = favorites,
                            onLogout = onLogout,
                            onEditProfile = { showEditProfile = true }
                        )
                    }
                }
            }
        }

        if (!showEditProfile) {
            FloatingBottomBar(
                selectedScreen = selectedScreen,
                onScreenSelected = { selectedScreen = it },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
fun HomeScreen(
    listings: List<Listing>,
    favorites: List<String>,
    onFavoriteClick: (String) -> Unit,
    onMessageClick: (Listing) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Tümü") }
    var searchText by remember { mutableStateOf("") }

    val categories = listOf("Tümü", "Yazılım", "Dil", "Tasarım", "Ders", "Müzik")

    val filteredListings = listings.filter { listing ->
        val categoryOk = selectedCategory == "Tümü" || listing.category == selectedCategory
        val searchOk = searchText.isBlank() ||
                listing.title.contains(searchText, ignoreCase = true) ||
                listing.offeredSkill.contains(searchText, ignoreCase = true) ||
                listing.wantedSkill.contains(searchText, ignoreCase = true)

        categoryOk && searchOk
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            HomeHeader()
        }

        item {
            AppSearchField(
                value = searchText,
                onValueChange = { searchText = it }
            )
        }

        item {
            FeaturedCard()
        }

        item {
            CategoryChips(
                categories = categories,
                selectedCategory = selectedCategory,
                onCategorySelected = { selectedCategory = it }
            )
        }

        if (filteredListings.isEmpty()) {
            item {
                EmptyState(
                    title = "İlan bulunamadı",
                    text = "İlan eklemek için İlan Ver sekmesini kullanabilirsin."
                )
            }
        } else {
            items(filteredListings) { listing ->
                ListingCard(
                    listing = listing,
                    isFavorite = favorites.contains(listing.id),
                    onFavoriteClick = { onFavoriteClick(listing.id) },
                    onMessageClick = onMessageClick
                )
            }
        }
    }
}

@Composable
fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "Bilgi Takası",
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF101936)
            )
            Text(
                text = "Bildiğini paylaş, öğrenmek istediğini bul",
                fontSize = 15.sp,
                color = Color(0xFF6D7894)
            )
        }

        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.96f),
            shadowElevation = 8.dp
        ) {
            Icon(
                imageVector = Icons.Default.Tune,
                contentDescription = null,
                tint = Color(0xFF101936),
                modifier = Modifier.padding(13.dp)
            )
        }
    }
}

@Composable
fun AppSearchField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 8.dp
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            placeholder = {
                Text("İlan, konu veya beceri ara")
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
fun FeaturedCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        shadowElevation = 12.dp,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF16D6C6),
                            Color(0xFF3C8DFF),
                            Color(0xFF935CFF)
                        )
                    )
                )
                .padding(22.dp)
        ) {
            Column {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.White.copy(alpha = 0.20f)
                ) {
                    Text(
                        text = "★ Öne Çıkan",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Bilgini paylaş,\nyeni fırsatlar kazan",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    lineHeight = 34.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Topluluk içinde öğren, öğret ve geliş.",
                    color = Color.White.copy(alpha = 0.90f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "👥  🔁  📚  ✨",
                    fontSize = 28.sp
                )
            }
        }
    }
}

@Composable
fun CategoryChips(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        categories.forEach { category ->
            val selected = category == selectedCategory

            Surface(
                modifier = Modifier.clickable { onCategorySelected(category) },
                shape = RoundedCornerShape(50),
                color = if (selected) Color.Transparent else Color.White,
                shadowElevation = if (selected) 8.dp else 4.dp
            ) {
                Box(
                    modifier = Modifier
                        .then(
                            if (selected) {
                                Modifier.background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF13C8D8), Color(0xFF8B5CFF))
                                    )
                                )
                            } else Modifier
                        )
                        .padding(horizontal = 22.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = category,
                        color = if (selected) Color.White else Color(0xFF101936),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun ListingCard(
    listing: Listing,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onMessageClick: (Listing) -> Unit = {}
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvatarCircle(text = listing.emoji)

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = listing.username,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                            color = Color(0xFF101936)
                        )
                        Text(
                            text = "📍 ${listing.city}",
                            fontSize = 13.sp,
                            color = Color(0xFF6D7894)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFE9FFF7)
                    ) {
                        Text(
                            text = "🔥 %${calculateMatchRate(listing)} Uyum",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = Color(0xFF0B9B75),
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }

                    IconButton(onClick = onFavoriteClick) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = null,
                            tint = if (isFavorite) Color(0xFF8A5CFF) else Color(0xFF7C849A)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = listing.title,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF101936)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = listing.description,
                fontSize = 14.sp,
                color = Color(0xFF66708A),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SkillMiniCard(
                    label = "BİLDİĞİ",
                    skill = listing.offeredSkill,
                    subText = listing.level,
                    emoji = "🎓",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFF13BFAF)
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF2EEFF),
                    border = BorderStroke(2.dp, Color(0xFFE2D8FF)),
                    modifier = Modifier.padding(horizontal = 10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = Color(0xFF6E5CFF),
                        modifier = Modifier.padding(12.dp)
                    )
                }

                SkillMiniCard(
                    label = "İSTEDİĞİ",
                    skill = listing.wantedSkill,
                    subText = "Öğrenmek",
                    emoji = "🎯",
                    modifier = Modifier.weight(1f),
                    accentColor = Color(0xFF8A5CFF)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "👥 ${listing.matchCount} Eşleşme",
                    color = Color(0xFF6D7894),
                    fontSize = 14.sp,
                    modifier = Modifier.weight(1f)
                )

                Button(
                    onClick = { onMessageClick(listing) },
                    modifier = Modifier
                        .width(150.dp)
                        .height(48.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues()
                ) {
                    GradientButtonContent(text = "Mesaj Gönder")
                }
            }
        }
    }
}

@Composable
fun CreateListingScreen(
    repo: FirebaseRepository,
    onAdded: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Yazılım") }
    var offeredSkill by remember { mutableStateOf("") }
    var wantedSkill by remember { mutableStateOf("") }
    var level by remember { mutableStateOf("Başlangıç") }
    var city by remember { mutableStateOf("İstanbul") }
    var profile by remember { mutableStateOf<AppUser?>(null) }
    var loading by remember { mutableStateOf(false) }

    val categories = listOf("Yazılım", "Dil", "Tasarım", "Ders", "Müzik")
    val levels = listOf("Başlangıç", "Orta", "İleri")

    LaunchedEffect(Unit) {
        profile = repo.getUserProfile()
        city = profile?.city ?: "İstanbul"
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenTitle(
                title = "Yeni İlan",
                subtitle = "Ne bildiğini ve ne öğrenmek istediğini yaz."
            )
        }

        item {
            FormCard {
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "İlan Başlığı",
                    placeholder = "Örn. Kotlin başlangıç seviyesi öğretebilirim"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "Açıklama",
                    placeholder = "Ne öğretebilirsin? Nasıl yardımcı olacaksın?",
                    minLines = 3
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Kategori", fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    categories.forEach {
                        SelectableSmallChip(
                            text = it,
                            selected = category == it,
                            onClick = { category = it }
                        )
                    }
                }
            }
        }

        item {
            FormCard {
                AppTextField(
                    value = offeredSkill,
                    onValueChange = { offeredSkill = it },
                    label = "Bildiğim Konu",
                    placeholder = "Örn. Kotlin, Excel, Photoshop"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = wantedSkill,
                    onValueChange = { wantedSkill = it },
                    label = "Öğrenmek İstediğim Konu",
                    placeholder = "Örn. İngilizce, Video Edit, UI/UX"
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Seviye", fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    levels.forEach {
                        SelectableSmallChip(
                            text = it,
                            selected = level == it,
                            onClick = { level = it }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = "Konum",
                    placeholder = "Şehir gir"
                )
            }
        }

        item {
            Button(
                onClick = {
                    if (title.isBlank() || offeredSkill.isBlank() || wantedSkill.isBlank()) {
                        Toast.makeText(
                            context,
                            "Başlık, bildiğin konu ve öğrenmek istediğin konu zorunlu.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@Button
                    }

                    scope.launch {
                        loading = true
                        try {
                            val latestProfile = repo.getUserProfile()

                            val listing = Listing(
                                userId = repo.currentUserId() ?: "",
                                username = latestProfile?.username ?: repo.currentEmail().substringBefore("@"),
                                city = city.ifBlank { latestProfile?.city ?: "İstanbul" },
                                title = title,
                                description = description.ifBlank { "Bu konuda birlikte pratik yapabiliriz." },
                                offeredSkill = offeredSkill,
                                wantedSkill = wantedSkill,
                                category = category,
                                level = level,
                                matchCount = 0,
                                emoji = when (category) {
                                    "Yazılım" -> "💻"
                                    "Dil" -> "🗣️"
                                    "Tasarım" -> "🎨"
                                    "Ders" -> "📚"
                                    "Müzik" -> "🎵"
                                    else -> "✨"
                                }
                            )

                            repo.addListing(listing)
                            onAdded()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "İlan eklenemedi",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        loading = false
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                GradientButtonContent(
                    text = if (loading) "Yayınlanıyor..." else "✈️  İlanı Yayınla"
                )
            }
        }
    }
}

@Composable
fun MatchesScreen(
    listings: List<Listing>,
    onMessageClick: (Listing) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenTitle(
                title = "Eşleşmeler",
                subtitle = "Sana uygun bilgi takasları"
            )
        }

        if (listings.isEmpty()) {
            item {
                EmptyState(
                    title = "Henüz eşleşme yok",
                    text = "İlanlar eklendikçe burada uygun kişiler görünecek."
                )
            }
        } else {
            items(listings) { listing ->
                MatchCard(
                    listing = listing,
                    onMessageClick = onMessageClick
                )
            }
        }
    }
}

@Composable
fun MatchCard(
    listing: Listing,
    onMessageClick: (Listing) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 10.dp
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFE9FFF7)
                ) {
                    Text(
                        text = "🔥 %${calculateMatchRate(listing)} Uyum",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Color(0xFF0B9B75),
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color(0xFFF2EEFF)
                ) {
                    Text(
                        text = "Karşılıklı Takas",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = Color(0xFF6E5CFF),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                MatchUserBlock(
                    emoji = listing.emoji,
                    username = listing.username,
                    city = listing.city,
                    skill = listing.offeredSkill,
                    modifier = Modifier.weight(1f)
                )

                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF2EEFF)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = Color(0xFF6E5CFF),
                        modifier = Modifier.padding(12.dp)
                    )
                }

                MatchUserBlock(
                    emoji = "👩‍💻",
                    username = "sen",
                    city = "İstanbul",
                    skill = listing.wantedSkill,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { onMessageClick(listing) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                GradientButtonContent(text = "Mesajlaş")
            }
        }
    }
}

@Composable
fun MatchUserBlock(
    emoji: String,
    username: String,
    city: String,
    skill: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AvatarCircle(text = emoji)

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = username,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF101936)
        )

        Text(
            text = "📍 $city",
            color = Color(0xFF6D7894),
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = Color(0xFFF6F8FE)
        ) {
            Text(
                text = skill,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                fontWeight = FontWeight.Bold,
                color = Color(0xFF101936)
            )
        }
    }
}

@Composable
fun MessagesScreen(
    repo: FirebaseRepository
) {
    var chats by remember { mutableStateOf<List<Chat>>(emptyList()) }
    var selectedChatId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(selectedChatId) {
        if (selectedChatId == null) {
            loading = true
            chats = repo.getMyChats()
            loading = false
        }
    }

    if (selectedChatId == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ScreenTitle(
                    title = "Mesajlar",
                    subtitle = "Bilgi takası için konuşmaların burada."
                )
            }

            if (loading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (chats.isEmpty()) {
                item {
                    EmptyState(
                        title = "Henüz mesaj yok",
                        text = "Bir ilandan mesaj göndererek konuşma başlatabilirsin."
                    )
                }
            } else {
                items(chats) { chat ->
                    ChatListItem(
                        repo = repo,
                        chat = chat,
                        onClick = { selectedChatId = chat.id }
                    )
                }
            }
        }
    } else {
        ChatDetailScreen(
            repo = repo,
            chatId = selectedChatId!!,
            onBack = { selectedChatId = null }
        )
    }
}

@Composable
fun ChatListItem(
    repo: FirebaseRepository,
    chat: Chat,
    onClick: () -> Unit
) {
    val currentUid = repo.currentUserId()
    val otherName = chat.participantNames
        .filterKeys { it != currentUid }
        .values
        .firstOrNull()
        ?: "Kullanıcı"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(26.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarCircle(text = "💬")

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = otherName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = Color(0xFF101936)
                )

                Text(
                    text = chat.listingTitle.ifBlank { "Bilgi takası konuşması" },
                    color = Color(0xFF8A5CFF),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = chat.lastMessage.ifBlank { "Henüz mesaj yok" },
                    color = Color(0xFF6D7894),
                    maxLines = 1
                )
            }

            Text(
                text = "›",
                fontSize = 28.sp,
                color = Color(0xFF8A5CFF)
            )
        }
    }
}

@Composable
fun ChatDetailScreen(
    repo: FirebaseRepository,
    chatId: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    fun refreshMessages() {
        scope.launch {
            loading = true
            messages = repo.getMessages(chatId)
            loading = false
        }
    }

    LaunchedEffect(chatId) {
        refreshMessages()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush())
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.clickable { onBack() },
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 6.dp
            ) {
                Text(
                    text = "←",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "Konuşma",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF101936)
            )
        }

        Spacer(modifier = Modifier.height(18.dp))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            shape = RoundedCornerShape(30.dp),
            color = Color.White.copy(alpha = 0.94f),
            shadowElevation = 8.dp
        ) {
            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (messages.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Henüz mesaj yok. İlk mesajı sen yaz ✨",
                        color = Color(0xFF6D7894)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages) { message ->
                        val isMe = message.senderId == repo.currentUserId()

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 20.dp,
                                    topEnd = 20.dp,
                                    bottomStart = if (isMe) 20.dp else 4.dp,
                                    bottomEnd = if (isMe) 4.dp else 20.dp
                                ),
                                color = if (isMe) Color(0xFF15C8D8) else Color(0xFFF2F4FA)
                            ) {
                                Text(
                                    text = message.text,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                    color = if (isMe) Color.White else Color(0xFF101936),
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Mesaj yaz...") },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF15C8D8),
                    unfocusedBorderColor = Color(0xFFE1E6F2),
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White
                )
            )

            Spacer(modifier = Modifier.width(10.dp))

            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        val messageText = text
                        text = ""

                        scope.launch {
                            repo.sendMessage(chatId, messageText)
                            refreshMessages()
                        }
                    }
                },
                shape = CircleShape,
                contentPadding = PaddingValues(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF8A5CFF)
                )
            ) {
                Text("➤", color = Color.White)
            }
        }
    }
}

@Composable
fun ProfileScreen(
    repo: FirebaseRepository,
    listings: List<Listing>,
    favorites: List<String>,
    onLogout: () -> Unit,
    onEditProfile: () -> Unit
) {
    val myId = repo.currentUserId()
    val myListings = listings.filter { it.userId == myId }

    var profile by remember { mutableStateOf<AppUser?>(null) }

    LaunchedEffect(Unit) {
        profile = repo.getUserProfile()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ScreenTitle(
                title = "Profil",
                subtitle = "Profilini yönet, katkılarını keşfet."
            )
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(32.dp),
                color = Color.Transparent,
                shadowElevation = 12.dp
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF8A5CFF), Color(0xFF15C8D8))
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AvatarCircle(text = "👩‍💻")

                            Spacer(modifier = Modifier.width(14.dp))

                            Column {
                                Text(
                                    text = profile?.username ?: repo.currentEmail().substringBefore("@"),
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 25.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "📍 ${profile?.city ?: "İstanbul"}",
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = profile?.fullName ?: "Bilgi takası kullanıcısı",
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(22.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            ProfileStat("İlan", myListings.size)
                            ProfileStat("Favori", favorites.size)
                            ProfileStat("Puan", 48)
                        }
                    }
                }
            }
        }

        item {
            SkillSection(
                title = "Bildikleri",
                skills = profile?.knownSkills ?: emptyList()
            )
        }

        item {
            SkillSection(
                title = "Öğrenmek İstedikleri",
                skills = profile?.wantedSkills ?: emptyList()
            )
        }

        item {
            Button(
                onClick = onEditProfile,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15C8D8))
            ) {
                Text(
                    text = "Profili Düzenle",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        item {
            OutlinedButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50)
            ) {
                Text("Çıkış Yap")
            }
        }

        item {
            Text(
                text = "İlanlarım",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                color = Color(0xFF101936)
            )
        }

        if (myListings.isEmpty()) {
            item {
                EmptyState(
                    title = "Henüz ilan yayınlamadın",
                    text = "İlan Ver sekmesinden ilk ilanını oluştur."
                )
            }
        } else {
            items(myListings) { listing ->
                ListingCard(
                    listing = listing,
                    isFavorite = favorites.contains(listing.id),
                    onFavoriteClick = {},
                    onMessageClick = {}
                )
            }
        }
    }
}

@Composable
fun EditProfileScreen(
    repo: FirebaseRepository,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var fullName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var knownText by remember { mutableStateOf("") }
    var wantedText by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val profile = repo.getUserProfile()
        if (profile != null) {
            fullName = profile.fullName
            username = profile.username
            city = profile.city
            knownText = profile.knownSkills.joinToString(", ")
            wantedText = profile.wantedSkills.joinToString(", ")
        }
        loading = false
    }

    if (loading) {
        LoadingScreen()
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackgroundBrush()),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.clickable { onBack() },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 6.dp
                ) {
                    Text(
                        text = "←",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = "Profili Düzenle",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF101936)
                    )
                    Text(
                        text = "Bilgilerini güncelle.",
                        fontSize = 15.sp,
                        color = Color(0xFF6D7894)
                    )
                }
            }
        }

        item {
            FormCard {
                AppTextField(
                    value = fullName,
                    onValueChange = { fullName = it },
                    label = "Ad Soyad",
                    placeholder = "Adını yaz"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = "Kullanıcı Adı",
                    placeholder = "Kullanıcı adını yaz"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = "Şehir",
                    placeholder = "Şehrini yaz"
                )
            }
        }

        item {
            FormCard {
                AppTextField(
                    value = knownText,
                    onValueChange = { knownText = it },
                    label = "Bildiklerim",
                    placeholder = "Kotlin, İngilizce, Excel"
                )

                Spacer(modifier = Modifier.height(14.dp))

                AppTextField(
                    value = wantedText,
                    onValueChange = { wantedText = it },
                    label = "Öğrenmek İstediklerim",
                    placeholder = "Photoshop, UI/UX, Video Edit"
                )
            }
        }

        item {
            Button(
                onClick = {
                    if (fullName.isBlank() || username.isBlank()) {
                        Toast.makeText(
                            context,
                            "Ad soyad ve kullanıcı adı boş olamaz.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@Button
                    }

                    scope.launch {
                        try {
                            repo.updateUserProfile(
                                fullName = fullName,
                                username = username,
                                city = city,
                                knownSkills = splitSkills(knownText),
                                wantedSkills = splitSkills(wantedText)
                            )

                            Toast.makeText(
                                context,
                                "Profil güncellendi",
                                Toast.LENGTH_SHORT
                            ).show()
                            onSaved()
                        } catch (e: Exception) {
                            Toast.makeText(
                                context,
                                e.message ?: "Profil güncellenemedi",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues()
            ) {
                GradientButtonContent(text = "Kaydet")
            }
        }
    }
}

@Composable
fun AppBackgroundBrush(): Brush {
    return Brush.verticalGradient(
        listOf(
            Color(0xFFF8FBFF),
            Color(0xFFF2ECFF),
            Color(0xFFF7FEFF)
        )
    )
}

@Composable
fun AvatarCircle(text: String) {
    Box(
        modifier = Modifier
            .size(58.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFFE7FCFF), Color(0xFFF1E8FF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, fontSize = 28.sp)
    }
}

@Composable
fun SkillMiniCard(
    label: String,
    skill: String,
    subText: String,
    emoji: String,
    modifier: Modifier = Modifier,
    accentColor: Color
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = accentColor.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.14f))
    ) {
        Row(
            modifier = Modifier.padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = emoji, fontSize = 24.sp)

            Spacer(modifier = Modifier.width(8.dp))

            Column {
                Text(
                    text = label,
                    color = accentColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = skill,
                    color = Color(0xFF101936),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(
                    text = subText,
                    color = Color(0xFF6D7894),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    minLines: Int = 1
) {
    Column {
        Text(
            text = label,
            color = Color(0xFF101936),
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(7.dp))

        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(text = placeholder, color = Color(0xFF9AA4BC))
            },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            shape = RoundedCornerShape(18.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Sentences
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFF15C8D8),
                unfocusedBorderColor = Color(0xFFE1E6F2),
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )
    }
}

@Composable
fun SelectableSmallChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(50),
        color = if (selected) Color.Transparent else Color(0xFFF6F8FE),
        border = if (selected) null else BorderStroke(1.dp, Color(0xFFE1E6F2))
    ) {
        Box(
            modifier = Modifier
                .then(
                    if (selected) {
                        Modifier.background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF13C8D8), Color(0xFF8B5CFF))
                            )
                        )
                    } else Modifier
                )
                .padding(horizontal = 15.dp, vertical = 9.dp)
        ) {
            Text(
                text = text,
                color = if (selected) Color.White else Color(0xFF101936),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun ScreenTitle(
    title: String,
    subtitle: String
) {
    Column {
        Text(
            text = title,
            fontSize = 38.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF101936)
        )
        Text(
            text = subtitle,
            fontSize = 15.sp,
            color = Color(0xFF6D7894)
        )
    }
}

@Composable
fun SkillSection(
    title: String,
    skills: List<String>
) {
    Column {
        Text(
            text = title,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 18.sp,
            color = Color(0xFF101936)
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (skills.isEmpty()) {
            Text(
                text = "Henüz eklenmemiş.",
                color = Color(0xFF6D7894)
            )
        } else {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                skills.forEach {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.White,
                        shadowElevation = 5.dp
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF101936)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileStat(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value.toString(),
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 24.sp
        )
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    text: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = Color.White,
        shadowElevation = 8.dp
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "✨", fontSize = 42.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            )
            Text(
                text = text,
                color = Color(0xFF6D7894)
            )
        }
    }
}

@Composable
fun GradientButtonContent(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF12C6D8), Color(0xFF8A5CFF))
                ),
                RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun FloatingBottomBar(
    selectedScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .fillMaxWidth(),
        shape = RoundedCornerShape(34.dp),
        color = Color.White.copy(alpha = 0.96f),
        shadowElevation = 18.dp
    ) {
        Row(
            modifier = Modifier
                .height(74.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomBarItem(Screen.HOME, selectedScreen, Icons.Default.Home, onScreenSelected)
            BottomBarItem(Screen.CREATE, selectedScreen, Icons.Default.Add, onScreenSelected)
            BottomBarItem(Screen.MATCHES, selectedScreen, Icons.Outlined.Groups, onScreenSelected)
            BottomBarItem(Screen.MESSAGES, selectedScreen, Icons.Default.ChatBubbleOutline, onScreenSelected)
            BottomBarItem(Screen.PROFILE, selectedScreen, Icons.Default.Person, onScreenSelected)
        }
    }
}

@Composable
fun BottomBarItem(
    screen: Screen,
    selectedScreen: Screen,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: (Screen) -> Unit
) {
    val selected = screen == selectedScreen

    val iconColor by animateColorAsState(
        targetValue = if (selected) Color(0xFF15C8D8) else Color(0xFF69728C),
        label = "iconColor"
    )

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable { onClick(screen) }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            shape = CircleShape,
            color = if (selected) Color(0xFFE9F9FF) else Color.Transparent
        ) {
            Icon(
                imageVector = icon,
                contentDescription = screen.title,
                tint = iconColor,
                modifier = Modifier.padding(7.dp)
            )
        }

        Text(
            text = screen.title,
            fontSize = 11.sp,
            color = iconColor,
            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Normal
        )
    }
}

fun splitSkills(text: String): List<String> {
    return text.split(",")
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

fun calculateMatchRate(listing: Listing): Int {
    val base = 88
    val bonus = abs(listing.title.hashCode()) % 10
    return base + bonus
}