package com.moakete

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray

// ─────────────────────────────────────────────────────────────
//  نماذج البيانات
// ─────────────────────────────────────────────────────────────

data class AzkarContent(val id: Int, val title: String, val categoryId: Int)
data class AzkarPart(val order: Int, val text: String, val type: String)
data class AzkarSupplication(val id: Int, val contentId: Int, val parts: List<AzkarPart>)
data class AzkarCategoryUI(val categoryId: Int, val name: String, val emoji: String)

// ─────────────────────────────────────────────────────────────
//  تحميل البيانات من assets/data/
// ─────────────────────────────────────────────────────────────

fun loadAzkarContents(context: Context): List<AzkarContent> {
    return try {
        val json = context.assets.open("data/contents.json").bufferedReader(Charsets.UTF_8).readText()
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            AzkarContent(o.getInt("id"), o.getString("title"), o.getInt("categoryId"))
        }
    } catch (e: Exception) { e.printStackTrace(); emptyList() }
}

fun loadAzkarSupplications(context: Context): List<AzkarSupplication> {
    return try {
        val json = context.assets.open("data/supplications.json").bufferedReader(Charsets.UTF_8).readText()
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val pa = o.getJSONArray("parts")
            val parts = (0 until pa.length()).map { j ->
                val p = pa.getJSONObject(j)
                AzkarPart(p.getInt("order"),
                    p.getString("text").replace("\r\n", "\n").replace("\r", "\n").trim(),
                    p.getString("type"))
            }.sortedBy { it.order }
            AzkarSupplication(o.getInt("id"), o.getInt("contentId"), parts)
        }
    } catch (e: Exception) { e.printStackTrace(); emptyList() }
}

// ─────────────────────────────────────────────────────────────
//  المفضلة
// ─────────────────────────────────────────────────────────────

private const val PREFS_NAME    = "azkar_prefs"
private const val KEY_FAVORITES = "favorites"

fun favKey(supp: AzkarSupplication) = "${supp.contentId}_${supp.id}"

fun loadFavorites(context: Context): MutableSet<String> =
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .getStringSet(KEY_FAVORITES, emptySet())!!.toMutableSet()

fun saveFavorites(context: Context, favs: Set<String>) {
    context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        .edit().putStringSet(KEY_FAVORITES, favs).apply()
}

// ─────────────────────────────────────────────────────────────
//  ألوان الواجهة
// ─────────────────────────────────────────────────────────────

private val BgCream    = AppColors.BgCream
private val IconCircle = AppColors.Gold
private val TextDark   = AppColors.TextDark
private val TextSub    = AppColors.TextSub
private val MainAyah   = Color(0xFF1A0800)
private val SrcColor   = Color(0xFF8B5A2B)
private val CardBg     = AppColors.CardBg
private val GoldLine   = AppColors.Gold
private val FavActive  = Color(0xFFF5C518)
private val FavInactive= AppColors.TextFaint

// ─────────────────────────────────────────────────────────────
//  نص المقدمة
// ─────────────────────────────────────────────────────────────

private val INTRO_TEXT = """
إنَّ الحمد لله، نحمده ونستعينه ونستغفره ونعوذ بالله من شرور أنفسنا، ومن سيئات أعمالنا، من يهده الله فلا مضلَّ له، ومن يضلل فلا هادي له. وأشهد أن لا إله إلا الله وحده لا شريك له وأشهد أن محمداً عبده ورسوله.

اللهم صلِّ على محمد النبي الأمي، وعلى آل محمد، كما صليت على آل إبراهيم، وبارك على محمد النبي الأمي وعلى آل محمد، كما باركت على آل إبراهيم في العالمين، إنك حميد مجيد.

أما بعد:

فهذه الأذكار النبوية جمعتها مما صححه الإمام العلامة المحدث محمد ناصر الدين الألباني من كتبه المطبوعة رجاء أن يعم نفعها المسلمين والمسلمات.

وأسأل الله العظيم رب العرش الكريم أن ينفع بها من كتبها وقرأها ونشرها وجميع المسلمين.

راجي عفو ربه
أبو الحسن محمد بن حسن بن عبد الحميد الشيخ
غفر الله له ولوالديه ولجميع المسلمين
""".trimIndent()

// ─────────────────────────────────────────────────────────────
//  قائمة التصنيفات
// ─────────────────────────────────────────────────────────────

private val CATEGORIES = listOf(
    AzkarCategoryUI(-2, "مقدمة",             "📖"),
    AzkarCategoryUI(-3, "المفضلة",            "⭐"),
    AzkarCategoryUI(-1, "جميع\nالدعاء",      "♾"),
    AzkarCategoryUI(1,  "الصباح\nوالمساء",   "🌤"),
    AzkarCategoryUI(2,  "النوم",              "🌙"),
    AzkarCategoryUI(3,  "الطهارة",            "🚿"),
    AzkarCategoryUI(4,  "الأذان\nوالمسجد",   "🕌"),
    AzkarCategoryUI(5,  "الصلاة",             "🕐"),
    AzkarCategoryUI(6,  "السفر",              "✈"),
    AzkarCategoryUI(7,  "اللباس",             "👕"),
    AzkarCategoryUI(8,  "السلام",             "🤝"),
    AzkarCategoryUI(9,  "الطعام\nوالشراب",   "🍽"),
    AzkarCategoryUI(10, "المعاملة",           "🏦"),
    AzkarCategoryUI(11, "الزواج",             "💍"),
    AzkarCategoryUI(12, "الخوف\nوالكرب",     "🫀"),
    AzkarCategoryUI(13, "المرض",              "🏥"),
    AzkarCategoryUI(14, "الموت",              "⏳"),
    AzkarCategoryUI(15, "الحج\nوالعمرة",     "🏡"),
    AzkarCategoryUI(16, "الحالات\nالمختلفة", "🔆"),
    AzkarCategoryUI(17, "الآداب",             "🎓"),
    AzkarCategoryUI(18, "القرآن",             "📗"),
    AzkarCategoryUI(19, "الجمعة",             "📅"),
    AzkarCategoryUI(20, "العيد",              "🎊"),
    AzkarCategoryUI(21, "أحوال\nالجوية",     "☁")
)

// ─────────────────────────────────────────────────────────────
//  الشاشة الرئيسية
// ─────────────────────────────────────────────────────────────

@Composable
fun AzkarListScreen(onBack: () -> Unit) {
    val context       = LocalContext.current
    val contents      = remember { loadAzkarContents(context) }
    val supplications = remember { loadAzkarSupplications(context) }
    var favorites by remember { mutableStateOf(loadFavorites(context)) }

    val onToggleFav: (AzkarSupplication) -> Unit = { supp ->
        val key = favKey(supp)
        val updated = favorites.toMutableSet()
        if (key in updated) updated.remove(key) else updated.add(key)
        favorites = updated
        saveFavorites(context, updated)
    }

    var selCategory by remember { mutableStateOf<AzkarCategoryUI?>(null) }
    var selContent  by remember { mutableStateOf<AzkarContent?>(null) }
    var showSearch  by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    when {
        showSearch -> SearchScreen(
            query = searchQuery, onQueryChange = { searchQuery = it },
            supplications = supplications, contents = contents,
            favorites = favorites, onToggleFav = onToggleFav,
            onBack = { showSearch = false; searchQuery = "" }
        )
        selContent != null -> SupplicationsScreen(
            title = selContent!!.title,
            supplications = supplications.filter { it.contentId == selContent!!.id },
            favorites = favorites, onToggleFav = onToggleFav,
            onBack = { selContent = null }
        )
        selCategory != null -> {
            val catId = selCategory!!.categoryId
            if (catId == -2) {
                IntroScreen(onBack = { selCategory = null })
            } else {
                val list = when (catId) {
                    -1 -> contents
                    -3 -> {
                        val favIds = supplications.filter { favKey(it) in favorites }.map { it.contentId }.distinct()
                        contents.filter { it.id in favIds }
                    }
                    else -> contents.filter { it.categoryId == catId }
                }
                ContentsScreen(
                    category = selCategory!!, contents = list,
                    supplications = supplications, favorites = favorites,
                    onToggleFav = onToggleFav,
                    onContentClick = { selContent = it },
                    onBack = { selCategory = null }
                )
            }
        }
        else -> CategoriesScreen(
            categories = CATEGORIES, supplications = supplications, favorites = favorites,
            onCategoryClick = { selCategory = it },
            onSearchClick   = { showSearch = true },
            onBack          = onBack
        )
    }
}

// ─────────────────────────────────────────────────────────────
//  شاشة التصنيفات
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoriesScreen(
    categories: List<AzkarCategoryUI>,
    supplications: List<AzkarSupplication>,
    favorites: Set<String>,
    onCategoryClick: (AzkarCategoryUI) -> Unit,
    onSearchClick: () -> Unit,
    onBack: () -> Unit
) {
    val favCount = supplications.count { favKey(it) in favorites }

    Column(Modifier.fillMaxSize().background(BgCream)) {
        Spacer(Modifier.height(35.dp))
        TopAppBar(
            title = { Text("الأذكار والأدعية", color = TextDark, fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark) }
            },
            actions = {
                IconButton(onClick = onSearchClick) { Icon(Icons.Default.Search, null, tint = TextDark) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCream)
        )
        HorizontalDivider(color = TextDark.copy(0.08f))

        LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val chunked = categories.chunked(3)
            items(chunked) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { cat ->
                        val badge = if (cat.categoryId == -3 && favCount > 0) "$favCount" else null
                        CategoryCell(cat = cat, badge = badge, onClick = { onCategoryClick(cat) }, modifier = Modifier.weight(1f))
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}

@Composable
fun CategoryCell(cat: AzkarCategoryUI, badge: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(16.dp))
            .background(CardBg.copy(0.75f))
            .border(1.dp, GoldLine.copy(0.18f), RoundedCornerShape(16.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(cat.emoji, fontSize = 30.sp)
            Spacer(Modifier.height(4.dp))
            Text(cat.name, color = TextDark, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, lineHeight = 15.sp)
        }
        if (badge != null) {
            Box(
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(20.dp)
                    .clip(CircleShape).background(FavActive),
                contentAlignment = Alignment.Center
            ) { Text(badge, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  شاشة المقدمة
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntroScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(BgCream)) {
        Spacer(Modifier.height(35.dp))
        TopAppBar(
            title = { Text("مقدمة", color = TextDark, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCream)
        )
        HorizontalDivider(color = TextDark.copy(0.08f))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            Text(INTRO_TEXT, color = TextDark, fontSize = 16.sp, lineHeight = 28.sp, textAlign = TextAlign.Justify)
            Spacer(Modifier.height(40.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  شاشة قائمة العناوين
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentsScreen(
    category: AzkarCategoryUI, contents: List<AzkarContent>,
    supplications: List<AzkarSupplication>, favorites: Set<String>,
    onToggleFav: (AzkarSupplication) -> Unit,
    onContentClick: (AzkarContent) -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(BgCream)) {
        Spacer(Modifier.height(35.dp))
        TopAppBar(
            title = { Text(category.name.replace("\n", " "), color = TextDark, fontWeight = FontWeight.Bold) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCream)
        )
        HorizontalDivider(color = TextDark.copy(0.08f))

        if (contents.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("لا توجد أذكار في هذا التصنيف", color = TextSub)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(contents, key = { it.id }) { content ->
                    val suppCount = supplications.count { it.contentId == content.id }
                    val favCount  = supplications.count { it.contentId == content.id && favKey(it) in favorites }
                    ContentCard(content = content, suppCount = suppCount, favCount = favCount, onClick = { onContentClick(content) })
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
fun ContentCard(content: AzkarContent, suppCount: Int, favCount: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(CardBg.copy(0.75f))
            .border(0.8.dp, GoldLine.copy(0.18f), RoundedCornerShape(14.dp))
            .clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(content.title, color = TextDark, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("$suppCount دعاء" + if (favCount > 0) " · ⭐ $favCount" else "", color = TextSub, fontSize = 12.sp)
        }
        Text("›", color = GoldLine, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ─────────────────────────────────────────────────────────────
//  شاشة نصوص الأدعية
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplicationsScreen(
    title: String, supplications: List<AzkarSupplication>,
    favorites: Set<String>, onToggleFav: (AzkarSupplication) -> Unit, onBack: () -> Unit
) {
    val listState = rememberLazyListState()
    val scope     = rememberCoroutineScope()
    val showUp    by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    Box(Modifier.fillMaxSize().background(BgCream)) {
        Column {
            Spacer(Modifier.height(35.dp))
            TopAppBar(
                title = { Text(title, color = TextDark, fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCream)
            )
            HorizontalDivider(color = TextDark.copy(0.08f))
            LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(supplications, key = { favKey(it) }) { s ->
                    ZikrCard(supplication = s, isFav = favKey(s) in favorites, onToggleFav = { onToggleFav(s) })
                }
                item { Spacer(Modifier.height(70.dp)) }
            }
        }
        if (showUp) {
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp).size(44.dp),
                containerColor = IconCircle, contentColor = Color.White
            ) {
                Icon(Icons.Default.KeyboardArrowUp, null, modifier = Modifier.size(22.dp))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  شاشة البحث
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    query: String, onQueryChange: (String) -> Unit,
    supplications: List<AzkarSupplication>, contents: List<AzkarContent>,
    favorites: Set<String>, onToggleFav: (AzkarSupplication) -> Unit, onBack: () -> Unit
) {
    val contentTitles = remember(contents) { contents.associateBy { it.id } }
    val results = remember(query, supplications) {
        if (query.isBlank()) emptyList()
        else supplications.filter { supp ->
            supp.parts.any { it.text.contains(query, ignoreCase = true) } ||
            (contentTitles[supp.contentId]?.title?.contains(query, ignoreCase = true) == true)
        }
    }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().background(BgCream)) {
        Spacer(Modifier.height(35.dp))
        TopAppBar(
            title = { AzkarSearchBar(query = query, onQueryChange = onQueryChange, modifier = Modifier.fillMaxWidth()) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = BgCream)
        )
        HorizontalDivider(color = TextDark.copy(0.08f))

        when {
            query.isBlank() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔍", fontSize = 48.sp); Spacer(Modifier.height(14.dp))
                    Text("اكتب للبحث في نصوص الأذكار", color = TextSub, fontSize = 16.sp, textAlign = TextAlign.Center)
                }
            }
            results.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😔", fontSize = 48.sp); Spacer(Modifier.height(14.dp))
                    Text("لا توجد نتائج لـ «$query»", color = TextSub, fontSize = 15.sp, textAlign = TextAlign.Center)
                }
            }
            else -> LazyColumn(state = listState, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    Text("نتائج البحث: ${results.size}", color = GoldLine.copy(0.70f), fontSize = 13.sp,
                        textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                }
                items(results, key = { favKey(it) }) { s ->
                    val sectionTitle = contentTitles[s.contentId]?.title ?: ""
                    Column {
                        if (sectionTitle.isNotEmpty())
                            Text(sectionTitle, color = GoldLine, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp))
                        ZikrCard(supplication = s, isFav = favKey(s) in favorites, onToggleFav = { onToggleFav(s) })
                    }
                }
                item { Spacer(Modifier.height(60.dp)) }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  شريط البحث
// ─────────────────────────────────────────────────────────────

@Composable
fun AzkarSearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(40.dp).clip(RoundedCornerShape(10.dp))
            .background(TextDark.copy(0.08f)).padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (query.isEmpty()) Text("ابحث في الأذكار...", color = TextSub, fontSize = 14.sp)
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(
                value = query, onValueChange = onQueryChange, singleLine = true,
                textStyle = TextStyle(color = TextDark, fontSize = 15.sp, textDirection = TextDirection.Rtl),
                cursorBrush = SolidColor(GoldLine), modifier = Modifier.weight(1f)
            )
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, null, tint = TextSub, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  بطاقة الذكر الواحد
// ─────────────────────────────────────────────────────────────

@Composable
fun ZikrCard(supplication: AzkarSupplication, isFav: Boolean, onToggleFav: () -> Unit) {
    val hasMain = supplication.parts.any { it.type == "main" }

    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFav) CardBg.copy(0.85f) else CardBg.copy(0.60f))
            .border(
                if (isFav) 1.5.dp else if (hasMain) 1.dp else 0.5.dp,
                if (isFav) FavActive.copy(0.60f) else if (hasMain) GoldLine.copy(0.30f) else TextDark.copy(0.09f),
                RoundedCornerShape(14.dp)
            ).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)
    ) {
        supplication.parts.forEach { part ->
            val idx = supplication.parts.indexOf(part)
            when (part.type) {
                "normal" -> Text(part.text, color = TextSub, fontSize = 15.sp,
                    lineHeight = 26.sp, textAlign = TextAlign.Justify,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp))
                "main" -> {
                    if (idx > 0 && supplication.parts[idx - 1].type == "normal")
                        HorizontalDivider(color = GoldLine.copy(0.22f), modifier = Modifier.padding(vertical = 6.dp))
                    Text(part.text, color = MainAyah, fontSize = 20.sp,
                        fontWeight = FontWeight.Bold, lineHeight = 36.sp, textAlign = TextAlign.Justify,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                }
                "from" -> {
                    Spacer(Modifier.height(6.dp))
                    Text(part.text, color = SrcColor, fontSize = 13.sp,
                        fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth())
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = GoldLine.copy(0.12f))
        Row(Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onToggleFav, modifier = Modifier.size(36.dp)) {
                Text(if (isFav) "⭐" else "☆", fontSize = 20.sp,
                    color = if (isFav) FavActive else FavInactive)
            }
        }
    }
}
