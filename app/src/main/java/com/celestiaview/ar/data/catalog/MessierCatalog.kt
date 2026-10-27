package com.celestiaview.ar.data.catalog

import com.celestiaview.ar.data.model.DeepSkyObject
import com.celestiaview.ar.data.model.DsoType

/**
 * 梅西耶深空天体目录：30 个经典目标（星系 / 星云 / 球状星团 / 疏散星团 / 行星状星云 / 超新星遗迹）。
 * 坐标 J2000；视直径取主轴（角分）。
 */
object MessierCatalog {

    private fun dso(
        id: String, type: DsoType, raHours: Double, decDeg: Double, mag: Double,
        major: Double, minor: Double, distKly: Double, nameEn: String, nameZh: String,
        con: String, desc: String
    ) = DeepSkyObject(id, type, raHours, decDeg, mag, major, minor, distKly, nameEn, nameZh, con, desc)

    val objects: List<DeepSkyObject> = listOf(
        dso("M1", DsoType.SUPERNOVA_REMNANT, 5.5758, 22.0145, 8.4, 6.0, 4.0, 6.5,
            "Crab Nebula", "蟹状星云", "TAU",
            "1054 年超新星爆发遗迹，中国宋代天官完整记录了“天关客星”。中心藏有每秒自转 30 次的脉冲星。"),
        dso("M8", DsoType.NEBULA, 18.0611, -24.3867, 6.0, 90.0, 40.0, 4.1,
            "Lagoon Nebula", "礁湖星云", "SGR",
            "人马座巨大发射星云，暗带如礁湖横贯，内部孕育新生恒星。"),
        dso("M13", DsoType.GLOBULAR_CLUSTER, 16.6949, 36.4613, 5.8, 20.0, 20.0, 22.2,
            "Great Hercules Cluster", "武仙座大星团", "HER",
            "北天最壮观的球状星团，数十万颗古老恒星聚成光球。1974 年人类由此向宇宙发送了阿雷西博信息。"),
        dso("M16", DsoType.NEBULA, 18.3132, -13.8070, 6.4, 35.0, 28.0, 7.0,
            "Eagle Nebula", "鹰状星云", "SER",
            "哈勃“创生之柱”的故乡，恒星诞生的宏伟摇篮。"),
        dso("M17", DsoType.NEBULA, 18.3514, -16.1715, 6.9, 46.0, 37.0, 5.5,
            "Omega Nebula", "欧米伽星云", "SGR",
            "又称天鹅星云/马蹄星云，银河系最明亮的恒星形成区之一。"),
        dso("M20", DsoType.NEBULA, 18.0387, -22.9706, 6.3, 28.0, 22.0, 4.1,
            "Trifid Nebula", "三叶星云", "SGR",
            "尘埃暗带将其分割成三瓣，集发射、反射与暗星云于一身。"),
        dso("M27", DsoType.PLANETARY_NEBULA, 19.9936, 22.7211, 7.4, 8.0, 5.7, 1.25,
            "Dumbbell Nebula", "哑铃星云", "VUL",
            "最明亮的行星状星云之一，类太阳恒星抛落的外壳正在消散。"),
        dso("M31", DsoType.GALAXY, 0.7123, 41.2692, 3.4, 190.0, 60.0, 2540.0,
            "Andromeda Galaxy", "仙女座大星系", "AND",
            "肉眼可见的最遥远天体（250 万光年），本星系群最大成员，40 亿年后将与银河系相撞并合。"),
        dso("M33", DsoType.GALAXY, 1.5641, 30.6602, 5.7, 70.0, 40.0, 2730.0,
            "Triangulum Galaxy", "三角座星系", "TRI",
            "本星系群第三大旋涡星系，黑暗夜空中的肉眼挑战。"),
        dso("M42", DsoType.NEBULA, 5.5881, -5.3911, 4.0, 85.0, 60.0, 1.34,
            "Orion Nebula", "猎户座大星云", "ORI",
            "全天最壮观的恒星诞生区，肉眼可见的宇宙育婴房，1400 光年外的发光氢云。"),
        dso("M44", DsoType.OPEN_CLUSTER, 8.6731, 19.9833, 3.7, 95.0, 95.0, 0.61,
            "Beehive Cluster", "鬼星团", "CNC",
            "又称蜂巢星团，中国古代“积尸气”——肉眼看似雾气的疏散星团。"),
        dso("M45", DsoType.OPEN_CLUSTER, 3.7830, 24.1167, 1.6, 110.0, 110.0, 0.44,
            "Pleiades", "昴星团（七姊妹）", "TAU",
            "全天最著名的疏散星团，蓝白幼星裹于反射星云中，日本 Subaru 望远镜与汽车徽标皆源于此。"),
        dso("M51", DsoType.GALAXY, 13.4979, 47.3360, 8.4, 11.0, 7.0, 31000.0,
            "Whirlpool Galaxy", "涡状星系", "CVN",
            "第一个被辨认出旋涡结构的星系，与伴星系 NGC 5195 共舞。"),
        dso("M57", DsoType.PLANETARY_NEBULA, 18.8931, 33.0289, 8.8, 1.4, 1.0, 2.3,
            "Ring Nebula", "环状星云", "LYR",
            "织女星旁的“烟圈”，垂死恒星吐出的发光气体壳。"),
        dso("M81", DsoType.GALAXY, 9.9268, 69.0654, 6.9, 27.0, 14.0, 12000.0,
            "Bode's Galaxy", "波德星系", "UMA",
            "大熊座明亮旋涡星系，与雪茄星系 M82 组成春季经典组合。"),
        dso("M82", DsoType.GALAXY, 9.9310, 69.6800, 8.4, 11.0, 4.0, 12000.0,
            "Cigar Galaxy", "雪茄星系", "UMA",
            "爆发星暴星系，正以十倍于银河系的速率疯狂造星。"),
        dso("M97", DsoType.PLANETARY_NEBULA, 11.2417, 55.0192, 9.9, 3.4, 3.3, 2.0,
            "Owl Nebula", "猫头鹰星云", "UMA",
            "两只“眼睛”望向地球的行星状星云。"),
        dso("M101", DsoType.GALAXY, 14.0583, 54.3542, 7.9, 29.0, 27.0, 21000.0,
            "Pinwheel Galaxy", "风车星系", "UMA",
            "正面朝向我们的宏伟旋涡，跨度近两个满月。"),
        dso("M104", DsoType.GALAXY, 12.6667, -11.6167, 8.0, 9.0, 4.0, 31000.0,
            "Sombrero Galaxy", "草帽星系", "VIR",
            "侧对我们的旋涡星系，尘埃带勾勒出宽檐草帽轮廓。"),
        dso("M35", DsoType.OPEN_CLUSTER, 6.1521, 24.3333, 5.3, 28.0, 28.0, 2.7,
            "Shoe Buckle Cluster", "鞋扣星团", "GEM",
            "双子座脚边的疏散星团，背景中嵌着更遥远的 NGC 2158。"),
        dso("M36", DsoType.OPEN_CLUSTER, 5.6042, 34.1333, 6.3, 12.0, 12.0, 4.1,
            "Pinwheel Cluster", "冬夜风车星团", "AUR", "御夫座三重奏之一。"),
        dso("M37", DsoType.OPEN_CLUSTER, 5.8703, 32.5500, 6.2, 24.0, 24.0, 4.5,
            "Salt & Pepper Cluster", "盐胡椒星团", "AUR", "御夫座最富的疏散星团。"),
        dso("M38", DsoType.OPEN_CLUSTER, 5.4899, 35.8500, 7.4, 21.0, 21.0, 4.2,
            "Starfish Cluster", "海星星团", "AUR", "十字形疏散星团。"),
        dso("M34", DsoType.OPEN_CLUSTER, 2.7057, 42.7167, 5.5, 35.0, 35.0, 1.5,
            "Spiral Cluster", "英仙疏星团", "PER", "双筒望远镜中的珍珠链。"),
        dso("M39", DsoType.OPEN_CLUSTER, 21.5300, 48.4333, 5.5, 32.0, 32.0, 1.0,
            "Cygnus Cluster", "天鹅疏星团", "CYG", "天津四东北的松散星团。"),
        dso("M15", DsoType.GLOBULAR_CLUSTER, 21.5000, 12.1667, 6.2, 18.0, 18.0, 33.6,
            "Great Pegasus Cluster", "飞马座球状星团", "PEG", "银岁最古老的球状星团之一（约 120 亿年）。"),
        dso("M2", DsoType.GLOBULAR_CLUSTER, 21.5588, -0.8233, 6.5, 16.0, 16.0, 37.5,
            "Aquarius Cluster", "宝瓶座球状星团", "AQR", "致密而古老的恒星之球。"),
        dso("M22", DsoType.GLOBULAR_CLUSTER, 18.6056, -23.9042, 5.1, 24.0, 24.0, 10.6,
            "Sagittarius Cluster", "人马座大星团", "SGR", "北半球可见的最亮球状星团之一。"),
        dso("M4", DsoType.GLOBULAR_CLUSTER, 16.3933, -26.5256, 5.6, 26.0, 26.0, 7.2,
            "Cat's Eye Cluster", "心宿二球状星团", "SCO", "紧邻心宿二的银色光球。"),
        dso("M11", DsoType.OPEN_CLUSTER, 18.8544, -6.2667, 6.3, 14.0, 14.0, 6.1,
            "Wild Duck Cluster", "野鸭星团", "SCT", "V 字形如飞鸭掠过的富星团。"),
        dso("M6", DsoType.OPEN_CLUSTER, 17.6772, -32.2167, 4.6, 25.0, 25.0, 1.6,
            "Butterfly Cluster", "蝴蝶星团", "SCO", "天蝎尾部的振翅蝴蝶。"),
        dso("M7", DsoType.OPEN_CLUSTER, 17.9011, -34.7933, 4.1, 80.0, 80.0, 0.98,
            "Ptolemy Cluster", "托勒密星团", "PUP", "托勒密于公元 130 年记录的肉眼星团。"),
        dso("M3", DsoType.GLOBULAR_CLUSTER, 13.7036, 28.3772, 6.2, 18.0, 18.0, 33.9,
            "Canes Cluster", "猎犬座球状星团", "CVN", "含变星最多的球状星团之一。"),
        dso("M5", DsoType.GLOBULAR_CLUSTER, 15.3106, 2.0831, 5.6, 20.0, 20.0, 24.5,
            "Rose Cluster", "巨蛇球状星团", "SER", "被认为是最美丽的球状星团之一。"),
        dso("M92", DsoType.GLOBULAR_CLUSTER, 17.2717, 43.1358, 6.4, 14.0, 14.0, 26.7,
            "Hercules Cluster II", "武仙第二星团", "HER", "被 M13 光环掩盖的珍宝。")
    )

    private val byId: Map<String, DeepSkyObject> = objects.associateBy { it.catalogId }

    fun find(id: String): DeepSkyObject? = byId[id]

    val andromeda: DeepSkyObject get() = find("M31")!!
    val orion: DeepSkyObject get() = find("M42")!!
    val pleiades: DeepSkyObject get() = find("M45")!!
}
