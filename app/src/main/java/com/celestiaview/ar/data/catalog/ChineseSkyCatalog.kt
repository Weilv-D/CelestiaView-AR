package com.celestiaview.ar.data.catalog

import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.Vec3
import com.celestiaview.ar.data.model.Star

/** 星图文化体系。 */
enum class CultureMode(val labelZh: String) {
    IAU("国际 IAU"),
    CHINESE("古星官"),
    DUAL("双语融合");

    fun next(): CultureMode = CultureMode.entries[(ordinal + 1) % CultureMode.entries.size]
}

/**
 * 东方古天文“三垣二十八宿”星官体系。
 * 二十八宿按四象分组，星官成员优先引用 [BrightStarCatalog]（以中文名解析），
 * 亮表中缺失的宿星以历元 J2000 坐标内联补齐（id 从 9001 起，与亮表 id 空间隔离）。
 */
object ChineseSkyCatalog {

    // ------------------------------------------------------------------
    // 四象
    // ------------------------------------------------------------------

    enum class FourSymbol(val nameZh: String, val motto: String, val argb: Long) {
        AZURE_DRAGON("东方苍龙", "角亢氐房心尾箕，春暮苍龙抬头于东方", 0xFF3FD6A0),
        BLACK_TORTOISE("北方玄武", "斗牛女虚危室壁，冬夜龟蛇镇北天", 0xFF5B8DEF),
        WHITE_TIGER("西方白虎", "奎娄胃昴毕觜参，秋夕白虎踞西关", 0xFFE8ECF4),
        VERMILION_BIRD("南方朱雀", "井鬼柳星张翼轸，夏夜朱雀翔南天", 0xFFFF6B5E)
    }

    // ------------------------------------------------------------------
    // 内联补充星（亮表中未收录的宿星，J2000）
    // ------------------------------------------------------------------

    private class S(val nameZh: String, val raHours: Double, val decDeg: Double, val mag: Double)

    private val inlineStars: Map<String, Star> = listOf(
        // 亢宿（室女座 κ ι λ ψ）
        S("亢宿一", 14.2144, -10.5195, 4.18), S("亢宿二", 14.5467, -6.0011, 4.08),
        S("亢宿三", 14.3130, -13.6479, 4.70), S("亢宿四", 14.4792, -3.6513, 4.74),
        // 氐宿二（氐宿二 γ Lib）
        S("氐宿二", 15.5922, -14.7914, 3.91),
        // 房宿一 π Sco
        S("房宿一", 15.9808, -26.1140, 2.89),
        // 尾宿一 μ¹ Sco / 尾宿二 ε Sco / 尾宿四 η Sco
        S("尾宿一", 16.8644, -38.0233, 3.00), S("尾宿二", 16.8361, -34.2933, 2.29),
        S("尾宿四", 17.2025, -43.2392, 3.33),
        // 斗宿一 φ Sgr
        S("斗宿一", 18.7608, -26.9908, 3.17),
        // 女宿（宝瓶座 ε μ 4 3）
        S("女宿一", 20.7944, -9.4978, 3.77), S("女宿二", 20.8736, -6.5689, 4.70),
        S("女宿三", 20.9578, -5.7428, 5.55), S("女宿四", 20.8494, -3.9833, 4.42),
        // 虚宿二 α Equ / 危宿二 θ Aqr
        S("虚宿二", 21.7364, 5.2489, 3.92), S("危宿二", 22.1583, -7.1022, 3.65),
        // 奎宿一 η And / 奎宿二 τ And
        S("奎宿一", 0.8167, 38.5527, 4.40), S("奎宿二", 0.9753, 34.5222, 4.94),
        // 胃宿一 43 Ari / 胃宿二 δ Ari
        S("胃宿一", 3.1864, 27.8750, 5.30), S("胃宿二", 3.1939, 19.4548, 4.35),
        // 昴宿一二四五（昴宿六 Alcyone 已在亮表）
        S("昴宿一", 3.7478, 24.1133, 3.70), S("昴宿二", 3.7536, 24.4672, 4.30),
        S("昴宿四", 3.7639, 24.3678, 3.87), S("昴宿五", 3.7722, 23.9484, 4.18),
        // 井宿一 μ Gem
        S("井宿一", 6.3761, 22.5136, 2.87),
        // 鬼宿一 θ Cnc
        S("鬼宿一", 8.5267, 18.0903, 5.33),
        // 柳宿一 δ Hya
        S("柳宿一", 8.6275, 5.7053, 4.14),
        // 张宿一 υ¹ Hya / 张宿二 λ Hya / 张宿三 μ Hya
        S("张宿一", 10.8272, -14.8211, 4.11), S("张宿二", 10.8967, -12.3508, 3.61),
        S("张宿三", 10.4297, -16.8361, 3.81),
        // 翼宿三 ζ Crt
        S("翼宿三", 11.8750, -18.3056, 4.71)
    ).mapIndexed { i, s ->
        s.nameZh to Star(
            id = 9001 + i, raHours = s.raHours, decDeg = s.decDeg, mag = s.mag,
            bv = 0.6, distLy = 0.0, spectral = "",
            nameEn = s.nameZh, nameZh = s.nameZh, constellation = "MAN",
            description = "二十八宿补充星官"
        )
    }.toMap()

    private val brightByZh: Map<String, Star> =
        BrightStarCatalog.stars.associateBy { it.nameZh }

    /** 中文名解析：亮表优先，内联补充兜底；两者皆无则启动期失败（数据完整性守护）。 */
    private fun star(zh: String): Star =
        brightByZh[zh] ?: inlineStars[zh]
        ?: throw IllegalArgumentException("Unknown Chinese star officer: $zh")

    // ------------------------------------------------------------------
    // 二十八宿
    // ------------------------------------------------------------------

    data class Mansion(
        val abbr: String,
        val nameZh: String,
        val symbol: FourSymbol,
        val description: String,
        val members: List<Star>,
        /** 成员下标连线。 */
        val edges: List<Pair<Int, Int>>
    ) {
        val raDeg: Double get() = centerRaDec(this).raDeg
        val decDeg: Double get() = centerRaDec(this).decDeg
    }

    private class MDef(
        val abbr: String, val nameZh: String, val symbol: FourSymbol,
        val description: String,
        val members: List<String>,
        val edges: List<Pair<Int, Int>> = emptyList()
    )

    private val mansionDefs = listOf(
        // ---------------- 东方苍龙七宿 ----------------
        MDef("角", "角宿", FourSymbol.AZURE_DRAGON,
            "苍龙之角，为二十八宿之首。角宿二星（室女座 α、ζ）为天关，日月五星必经之道；“二月二，龙抬头”即角宿一黄昏初见于东方。", listOf("角宿一", "角宿二")),
        MDef("亢", "亢宿", FourSymbol.AZURE_DRAGON,
            "苍龙咽喉。《史记·天官书》：“亢为疏庙，主疾。”四星在室女座，排成微弯的弧线。", listOf("亢宿一", "亢宿二", "亢宿三", "亢宿四"), listOf(0 to 1, 1 to 2, 2 to 3)),
        MDef("氐", "氐宿", FourSymbol.AZURE_DRAGON,
            "苍龙胸膛，“氐”即“根”。天秤座三星构成直角三角形，氐宿四曾以绿豆般的色泽闻名望远镜观测者。", listOf("氐宿一", "氐宿二", "氐宿三", "氐宿四"), listOf(0 to 1, 1 to 2, 2 to 3)),
        MDef("房", "房宿", FourSymbol.AZURE_DRAGON,
            "苍龙之腹，天蝎座四星排成一条直线，古称“天驷”，主车马。心宿二右侧即见其列。", listOf("房宿一", "房宿二", "房宿三", "房宿四"), listOf(0 to 1, 1 to 2, 2 to 3)),
        MDef("心", "心宿", FourSymbol.AZURE_DRAGON,
            "苍龙之心，三星中央的红色大火星（心宿二，Antares）即商星。《诗经》“七月流火”所指；杜甫“人生不相见，动如参与商”之商即此。", listOf("心宿一", "心宿二", "心宿三"), listOf(0 to 1, 1 to 2)),
        MDef("尾", "尾宿", FourSymbol.AZURE_DRAGON,
            "苍龙长尾，天蝎座九星蜿蜒如钩，尾端尾宿八、九即“针尖”Shaula 与 Lesath。东方传说“鱼跃龙门，尾点北斗”之尾即此。", listOf("尾宿一", "尾宿二", "尾宿三", "尾宿四", "尾宿五", "尾宿六", "尾宿七", "尾宿八", "尾宿九"), listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8)),
        MDef("箕", "箕宿", FourSymbol.AZURE_DRAGON,
            "苍龙尾梢的簸箕，人马座四星构成簸箕形。《诗经·小雅》：“维南有箕，不可以簸扬。”", listOf("箕宿一", "箕宿二", "箕宿三"), listOf(0 to 1, 1 to 2, 2 to 0)),

        // ---------------- 北方玄武七宿 ----------------
        MDef("斗", "斗宿", FourSymbol.BLACK_TORTOISE,
            "南斗六星，人马座的“斗”形，与北斗遥相呼应。道教称南斗注生，北斗注死。", listOf("斗宿一", "斗宿二", "斗宿三", "斗宿四", "斗宿五", "斗宿六"), listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 2)),
        MDef("牛", "牛宿", FourSymbol.BLACK_TORTOISE,
            "牵牛星官，摩羯座双星。牛宿之北的河鼓三星才是七夕的牛郎星；牛宿为牛郎的“牛身”。", listOf("牛宿一", "牛宿二"), listOf(0 to 1)),
        MDef("女", "女宿", FourSymbol.BLACK_TORTOISE,
            "婺女星官，宝瓶座四星。“须女四星，天女也，主布帛裁制。”", listOf("女宿一", "女宿二", "女宿三", "女宿四"), listOf(0 to 1, 1 to 2, 2 to 3)),
        MDef("虚", "虚宿", FourSymbol.BLACK_TORTOISE,
            "北方玄武中段，“虚”主空虚冢宰，秋风肃杀之夜二星孤悬，最合“虚”意。", listOf("虚宿一", "虚宿二"), listOf(0 to 1)),
        MDef("危", "危宿", FourSymbol.BLACK_TORTOISE,
            "危者，高而不安。三星跨宝瓶与飞马，“危”字取人立于屋脊之势。", listOf("危宿一", "危宿二", "危宿三"), listOf(0 to 1, 1 to 2)),
        MDef("室", "室宿", FourSymbol.BLACK_TORTOISE,
            "营室二星即飞马座四边形西边两星，秋夜方落先见于东方，古人以“定之方中，作于楚宫”定营造之时。", listOf("室宿一", "室宿二"), listOf(0 to 1)),
        MDef("壁", "壁宿", FourSymbol.BLACK_TORTOISE,
            "东壁二星，主文章图书。壁宿二（仙女座 α）恰是飞马四边形东北角，壁宿一（γ Peg）为著名的蓝变星。", listOf("壁宿一", "壁宿二"), listOf(0 to 1)),

        // ---------------- 西方白虎七宿 ----------------
        MDef("奎", "奎宿", FourSymbol.WHITE_TIGER,
            "白虎之尾，“奎主文章”，奎星屈曲相钩似文字之画，后世“魁星点斗”意象源此。奎宿九即仙女座 ν，旁有仙女座大星云 M31。", listOf("奎宿一", "奎宿二", "奎宿五", "奎宿九"), listOf(0 to 1, 1 to 2, 2 to 3)),
        MDef("娄", "娄宿", FourSymbol.WHITE_TIGER,
            "白羊座三星三角，“娄”意为聚众，主苑牧牺牲。娄宿三（α Ari，Hamal）是二等亮星。", listOf("娄宿一", "娄宿二", "娄宿三"), listOf(0 to 1, 1 to 2, 2 to 0)),
        MDef("胃", "胃宿", FourSymbol.WHITE_TIGER,
            "白虎之胃，白羊座三星。“胃为天仓”，主五谷收藏，深秋天仓满而冬藏。", listOf("胃宿一", "胃宿二", "胃宿三"), listOf(0 to 1, 1 to 2)),
        MDef("昴", "昴宿", FourSymbol.WHITE_TIGER,
            "昴星团（M45，七姊妹），《诗经》“嘒彼小星，维参与昴”。昴宿为西方白虎之毛发，冬夜疏散星团肉眼可见六七颗。", listOf("昴宿一", "昴宿二", "昴宿四", "昴宿五", "昴宿六"), listOf(0 to 4, 1 to 4, 2 to 4, 3 to 4)),
        MDef("毕", "毕宿", FourSymbol.WHITE_TIGER,
            "毕为捕兔之网，金牛座 V 形叉内藏毕星团。毕宿五（Aldebaran）橙红如眼，《西游记》昴日星官即化形于此。", listOf("毕宿一", "毕宿三", "毕宿四", "毕宿五", "毕宿八"), listOf(0 to 1, 1 to 2, 2 to 3, 2 to 4)),
        MDef("觜", "觜宿", FourSymbol.WHITE_TIGER,
            "白虎之嘴，猎户座 λ 一星独点，位于参宿四与参宿六之间，为二十八宿中最小的星官。", listOf("觜宿一")),
        MDef("参", "参宿", FourSymbol.WHITE_TIGER,
            "猎户座主体。“三星高照”即参宿中央腰带三星；参商不相见之“参”即此。腰三星斜排即天球赤道穿越处。", listOf("参宿一", "参宿二", "参宿三", "参宿四", "参宿五", "参宿六", "参宿七"), listOf(3 to 4, 0 to 1, 1 to 2, 2 to 5, 5 to 6, 3 to 0, 4 to 2, 6 to 5)),

        // ---------------- 南方朱雀七宿 ----------------
        MDef("井", "井宿", FourSymbol.VERMILION_BIRD,
            "朱雀之首，双子座众星，“井”为水事。井宿三是全天最亮的双子 β（北河三），井宿区亦藏冬季六边形一角。", listOf("井宿一", "井宿二", "井宿三", "井宿五"), listOf(0 to 1, 0 to 2, 2 to 3)),
        MDef("鬼", "鬼宿", FourSymbol.VERMILION_BIRD,
            "巨蟹座鬼星团（M44 蜂巢星团）所在，“舆鬼”主丧葬祠事；鬼宿中央白雾状积尸气，肉眼即见朦胧一团。", listOf("鬼宿一", "鬼宿三", "鬼宿四"), listOf(0 to 1, 1 to 2)),
        MDef("柳", "柳宿", FourSymbol.VERMILION_BIRD,
            "朱雀之喙，长蛇座三星弯曲如柳叶，“柳为鸟注”，主草木。", listOf("柳宿一", "柳宿三", "柳宿五"), listOf(0 to 1, 1 to 2)),
        MDef("星", "星宿", FourSymbol.VERMILION_BIRD,
            "星宿一即长蛇座 α（Alphard，孤星），“星宿”七子唯此独明，孤悬于春夜南方大空。", listOf("星宿一")),
        MDef("张", "张宿", FourSymbol.VERMILION_BIRD,
            "朱雀嗉囊，长蛇座三星张翼欲飞，“张主珍宝”。", listOf("张宿三", "张宿一", "张宿二"), listOf(0 to 1, 1 to 2)),
        MDef("翼", "翼宿", FourSymbol.VERMILION_BIRD,
            "朱雀双翼，巨爵座诸星排成风车状，为二十八宿中星数最多者（古录廿二星）。", listOf("翼宿一", "翼宿三"), listOf(0 to 1)),
        MDef("轸", "轸宿", FourSymbol.VERMILION_BIRD,
            "朱雀尾梢，乌鸦座四星构成平整四边形，“轸”为车后横木，主风。轸宿四旁即著名的乌鸦座环状星系群。", listOf("轸宿一", "轸宿二", "轸宿三", "轸宿四"), listOf(0 to 1, 1 to 3, 3 to 2, 2 to 0))
    )

    val mansions: List<Mansion> = mansionDefs.map { d ->
        Mansion(
            d.abbr, d.nameZh, d.symbol, d.description,
            d.members.map { star(it) },
            d.edges
        )
    }

    val byAbbr: Map<String, Mansion> = mansions.associateBy { it.abbr }

    // ------------------------------------------------------------------
    // 三垣
    // ------------------------------------------------------------------

    data class Enclosure(
        val nameZh: String,
        val description: String,
        val members: List<Star>,
        val edges: List<Pair<Int, Int>>
    )

    private class EDef(
        val nameZh: String,
        val description: String,
        val members: List<String>,
        val edges: List<Pair<Int, Int>>
    )

    private val enclosureDefs = listOf(
        EDef("紫微垣",
            "天帝与后妃所居宫城，居北天极而众星拱之。北极五星、勾陈六星环绕，两旁左右垣十五星环卫如宫墙。北极星（勾陈一）今距极不足 1°；帝星（北极二）则是周代极星。",
            listOf("北极星", "勾陈二", "勾陈三", "北极一·太子", "北极二·帝星", "右枢", "上弼", "少尉", "王良四", "策", "阁道三", "阁道二"),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7, 7 to 8, 8 to 9, 9 to 10, 10 to 11, 11 to 0)),
        EDef("太微垣",
            "天庭政府所在，三公九卿议政之廷。五帝座一居中，左右执法为南门，东西次相次将绵延如两班朝臣。太阳行经太微垣时正值春分前后。",
            listOf("左执法", "东次将", "东次相", "东上相", "西上相", "西次相", "西次将", "右执法", "五帝座一"),
            listOf(0 to 1, 1 to 2, 2 to 3, 4 to 5, 5 to 6, 6 to 7, 7 to 8, 8 to 0)),
        EDef("天市垣",
            "天上市集，万民交易之地。帝座（武仙座 α）居中市楼，侯（蛇夫座 α）为市侯监市，左右垣二十二星列肆而居，货泉水泽皆备于此。",
            listOf("天市右垣七", "天市右垣八", "天市右垣九", "天市右垣十", "天市右垣十一", "帝座", "侯", "天市左垣十一"),
            listOf(0 to 1, 1 to 2, 2 to 3, 3 to 4, 4 to 5, 5 to 6, 6 to 7))
    )

    val enclosures: List<Enclosure> = enclosureDefs.map { d ->
        Enclosure(d.nameZh, d.description, d.members.map { star(it) }, d.edges)
    }

    // ------------------------------------------------------------------
    // 几何辅助
    // ------------------------------------------------------------------

    /** 星官群中心（单位矢量平均 → 赤经赤纬）。 */
    fun centerRaDec(members: List<Star>): AstrometryEngine.RaDec {
        var x = 0.0; var y = 0.0; var z = 0.0
        for (s in members) {
            val ra = Math.toRadians(s.raDeg)
            val dec = Math.toRadians(s.decDeg)
            x += kotlin.math.cos(dec) * kotlin.math.cos(ra)
            y += kotlin.math.cos(dec) * kotlin.math.sin(ra)
            z += kotlin.math.sin(dec)
        }
        val v = Vec3(x, y, z).normalized()
        return AstrometryEngine.RaDec(
            raDeg = AstrometryEngine.normalizeDeg(Math.toDegrees(kotlin.math.atan2(v.y, v.x))),
            decDeg = Math.toDegrees(kotlin.math.asin(v.z.coerceIn(-1.0, 1.0)))
        )
    }

    private fun centerRaDec(m: Mansion): AstrometryEngine.RaDec = centerRaDec(m.members)

    /** 当前日期所属当值四象（按黄昏东升的宿段粗略轮值，供 UI 提示）。 */
    fun reigningSymbol(simTimeMillis: Long): FourSymbol {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = simTimeMillis }
        val doy = cal.get(java.util.Calendar.DAY_OF_YEAR)
        return when ((doy / 91) % 4) {
            0 -> FourSymbol.AZURE_DRAGON
            1 -> FourSymbol.BLACK_TORTOISE
            2 -> FourSymbol.WHITE_TIGER
            else -> FourSymbol.VERMILION_BIRD
        }
    }
}
