package com.celestiaview.ar.data.catalog

import com.celestiaview.ar.core.math.AstrometryEngine
import com.celestiaview.ar.core.math.Vec3
import com.celestiaview.ar.data.model.Constellation

/**
 * 星座目录：48 个星座的连线拓扑（以亮星英文名定义，加载期解析为 id 对）
 * 与古希腊神话 / 中国三垣二十八宿星官双重文化典故。
 */
object ConstellationCatalog {

    private class Def(
        val abbr: String,
        val nameEn: String,
        val nameZh: String,
        val storyWestern: String,
        val storyChinese: String,
        vararg val edges: Pair<String, String>
    )

    private val defs = listOf(
        Def(
            "UMA", "Ursa Major", "大熊座",
            "宙斯将情人卡利斯托化为大熊升上天空，追随的猎犬即猎犬座。北斗七星构成熊的背与尾。",
            "北斗所在。北斗七星在先秦典籍《鹖冠子》中已有“斗柄东指，天下皆春”的记载，古人以斗柄定四季；天枢、天璇又称“魁”，主文章。",
            "Dubhe" to "Merak", "Merak" to "Phecda", "Phecda" to "Megrez", "Megrez" to "Dubhe",
            "Megrez" to "Alioth", "Alioth" to "Mizar", "Mizar" to "Alkaid",
            "Merak" to "Tania Borealis", "Tania Borealis" to "Tania Australis", "Phecda" to "Talitha"
        ),
        Def(
            "UMI", "Ursa Minor", "小熊座",
            "大熊卡利斯托之子阿卡 斯，被宙斯一同化为小熊。北极星正位于小熊的尾梢。",
            "紫微垣核心所在。北极五星中最亮的“勾陈一”即今北极星；帝星（北极二）曾在周代担当极星，《论语》“譬如北辰，居其所而众星共之”或即指它。",
            "Polaris" to "Yildun", "Yildun" to "Epsilon Ursae Minoris",
            "Epsilon Ursae Minoris" to "Eta Ursae Minoris", "Eta Ursae Minoris" to "Zeta Ursae Minoris",
            "Zeta Ursae Minoris" to "Kochab", "Kochab" to "Pherkad", "Pherkad" to "Eta Ursae Minoris"
        ),
        Def(
            "DRA", "Draco", "天龙座",
            "守护金苹果的巨龙拉冬，被赫拉克勒斯杀死后升天，蜿蜒盘旋于大小熊之间。",
            "紫微右垣环绕极区。右枢（天龙 α）曾是金字塔时代的北极星（约前 2800 年），四千年前埃及人以它对准正北修建金字塔。",
            "Eltanin" to "Rastaban", "Rastaban" to "Grumium", "Grumium" to "Edasich",
            "Edasich" to "Thuban", "Thuban" to "Altais", "Altais" to "Eltanin"
        ),
        Def(
            "CAS", "Cassiopeia", "仙后座",
            "埃塞俄比亚王后卡西俄珀亚因夸耀美貌被缚上天空，绕极旋转以示惩罚，标志性的 W 永不沉入海中。",
            "王良五星属奎宿天区。王良为周天子御者，五星连线如“W”横跨银河；其中“策”星为著名气壳变星。",
            "Caph" to "Schedar", "Schedar" to "Gamma Cassiopeiae",
            "Gamma Cassiopeiae" to "Ruchbah", "Ruchbah" to "Segin"
        ),
        Def(
            "CEP", "Cepheus", "仙王座",
            "埃塞俄比亚国王刻甫斯，与仙后、仙女、英仙组成同一家族星座群。",
            "天钩诸星属紫微垣外。其中“造父一”（δ Cep 未收录此处）是人类发现的第一颗造父变星，宇宙测距的标尺即以它命名。",
            "Alderamin" to "Alfirk", "Alfirk" to "Errai", "Errai" to "Iota Cephei",
            "Iota Cephei" to "Zeta Cephei", "Zeta Cephei" to "Alderamin"
        ),
        Def(
            "AND", "Andromeda", "仙女座",
            "被献祭给海怪的女儿安德洛墨达，被珀尔修斯拯救后成婚升天。",
            "奎宿与壁宿天区。仙女座大星系 M31 肉眼可见的最远天体（250 万光年），秋夜“飞马四边形”东侧的璀璨光斑。",
            "Alpheratz" to "Delta Andromedae", "Delta Andromedae" to "Mirach", "Mirach" to "Almach"
        ),
        Def(
            "PER", "Perseus", "英仙座",
            "斩下美杜莎首级的英雄珀尔修斯，手持宝剑提着魔首，“魔星”大陵五即魔首之眼。",
            "大陵五星属胃宿天官，古称“积尸”，因其诡异变光被视为不祥；天船三星则象征天帝的舟船。",
            "Gamma Persei" to "Mirfak", "Mirfak" to "Epsilon Persei", "Epsilon Persei" to "Zeta Persei",
            "Mirfak" to "Algol"
        ),
        Def(
            "PEG", "Pegasus", "飞马座",
            "英雄珀尔修斯从美杜莎血泊中放出的双翼神马，四颗亮星构成著名的“秋季四边形”。",
            "室宿与壁宿即“营室”——天子宫殿，四星如檐角；壁宿二星主天下图书文章，故又称“文墨之宫”。",
            "Markab" to "Scheat", "Scheat" to "Alpheratz", "Alpheratz" to "Algenib", "Algenib" to "Markab",
            "Markab" to "Baham", "Baham" to "Homam", "Homam" to "Enif", "Scheat" to "Matar"
        ),
        Def(
            "AUR", "Auriga", "御夫座",
            "跛足的雅典王埃里克托尼奥斯发明四马战车，感动宙斯升上天空。怀中的“母羊kids”即五车二。",
            "五车五星属毕宿天区，象征天帝的五辆兵车；柱三星与咸池相应，传说为日出之所浴。",
            "Capella" to "Menkalinan", "Menkalinan" to "Mahasim", "Mahasim" to "Elnath",
            "Elnath" to "Hassaleh", "Hassaleh" to "Capella", "Capella" to "Almaaz", "Almaaz" to "Haedus"
        ),
        Def(
            "TAU", "Taurus", "金牛座",
            "宙斯化作雪白公牛驮走欧罗巴公主。毕宿星团是牛面，昴星团骑在牛背上，火红的毕宿五是怒目。",
            "毕宿为二十八宿之西方白虎第五宿，主边兵将军；昴宿（七姊妹）主狱事，胡人星官称“髦头”，古时以昴星团疏密占兵事。",
            "Aldebaran" to "Gamma Tauri", "Gamma Tauri" to "Ain", "Ain" to "Elnath",
            "Aldebaran" to "Tianguan", "Aldebaran" to "Lambda Tauri"
        ),
        Def(
            "ORI", "Orion", "猎户座",
            "海神波塞冬之子、狂傲的猎人俄里翁，被毒蝎蜇死后与天蝎座永世相隔——猎户升起时天蝎便落下。",
            "参宿为西方白虎七宿的主宿：“参”本义即三星连珠（参宿一二三腰带），《诗经·唐风》“三星在天”即指此；参商不相见典故中，商星指心宿（天蝎之心）。",
            "Betelgeuse" to "Bellatrix", "Bellatrix" to "Mintaka", "Betelgeuse" to "Alnitak",
            "Mintaka" to "Alnilam", "Alnilam" to "Alnitak", "Alnitak" to "Saiph", "Mintaka" to "Rigel",
            "Betelgeuse" to "Meissa", "Bellatrix" to "Meissa", "Alnilam" to "Eta Orionis"
        ),
        Def(
            "CMA", "Canis Major", "大犬座",
            "猎人俄里翁的忠犬莱拉普斯，紧随主人升天。胸前的天狼星是全天最亮的恒星。",
            "天狼星在秦汉星占中主侵掠，《史记·天官书》“狼比地有大星，曰南极老人”；苏轼“会挽雕弓如满月，西北望，射天狼”即咏此星。",
            "Sirius" to "Mirzam", "Sirius" to "Wezen", "Wezen" to "Adhara",
            "Adhara" to "Furud", "Wezen" to "Aludra"
        ),
        Def(
            "CMI", "Canis Minor", "小犬座",
            "俄里翁的另一只猎犬，也有人说是双头犬。南河三是冬季大三角的成员。",
            "南河三星属井宿天区，与北河（双子座）隔银河相望，主水旱之事。",
            "Procyon" to "Gomeisa"
        ),
        Def(
            "GEM", "Gemini", "双子座",
            "斯巴达王后勒达所生的孪生兄弟卡斯托尔与波吕克斯，一凡一神，情深不渝，宙斯让他们共享不朽。",
            "北河三星属井宿。井宿八星如“东井”，主水事；北河二、三即“阴阳”，古人以二星颜色占卜兵象。",
            "Castor" to "Pollux", "Castor" to "Wasat", "Pollux" to "Wasat", "Wasat" to "Alhena",
            "Castor" to "Mebsuta", "Mebsuta" to "Tejat"
        ),
        Def(
            "LEO", "Leo", "狮子座",
            "赫拉克勒斯十二伟业中被扼杀的涅墨亚狮子，刀枪不入，升天成为黄道第一巨兽。",
            "轩辕十七星属星官轩辕，象征黄帝与后宫；“轩辕十四”古称“女主”，是黄道上最亮的星。五帝座一居太微垣中，象征天庭。",
            "Regulus" to "Eta Leonis", "Eta Leonis" to "Algieba", "Algieba" to "Adhafera",
            "Adhafera" to "Rasalas", "Rasalas" to "Algenubi", "Algieba" to "Zosma",
            "Zosma" to "Denebola", "Denebola" to "Chertan", "Chertan" to "Regulus"
        ),
        Def(
            "VIR", "Virgo", "室女座",
            "丰收与正义女神得墨忒耳（或其女珀耳塞福涅），手持麦穗——即最亮星角宿一。",
            "角宿二星为东方苍龙第一宿，“角”为龙角，主天子威仪；《史记》以“苍龙连蜷于左”描述春夜龙角初升。",
            "Zavijava" to "Zaniah", "Zaniah" to "Porrima", "Porrima" to "Auva",
            "Auva" to "Vindemiatrix", "Porrima" to "Spica", "Spica" to "Heze", "Heze" to "Auva"
        ),
        Def(
            "LIB", "Libra", "天秤座",
            "正义女神阿斯特赖亚的天平，衡量人间善恶。两千年前秋分点在此，昼夜均分。",
            "氐宿四星为苍龙之胸（“氐”通“柢”，根本之意）。氐宿四罕见的绿色光泽令观星者津津乐道。",
            "Zubenelgenubi" to "Zubeneschamali", "Zubeneschamali" to "Zubenelakrab",
            "Zubenelakrab" to "Brachium", "Brachium" to "Zubenelgenubi"
        ),
        Def(
            "SCO", "Scorpius", "天蝎座",
            "奉大地女神之命蜇死猎人俄里翁的巨蝎，火红的心宿二是它的心脏。",
            "心宿三星即“商星”。心宿二古名“大火”，《诗经》“七月流火”记录的正是它西沉预示的季节流转；东方苍龙之体尾宿如龙尾摆银河。",
            "Acrab" to "Dschubba", "Dschubba" to "Fang", "Dschubba" to "Alniyat", "Alniyat" to "Antares",
            "Antares" to "Paikauhale", "Paikauhale" to "Larawag", "Larawag" to "Xamidimura",
            "Xamidimura" to "Zeta2 Scorpii", "Zeta2 Scorpii" to "Eta Scorpii", "Eta Scorpii" to "Sargas",
            "Sargas" to "Qiyu", "Qiyu" to "Girtab", "Girtab" to "Shaula", "Shaula" to "Lesath"
        ),
        Def(
            "SGR", "Sagittarius", "人马座",
            "半人马贤者喀戎，众多英雄之师，手持弓箭直指天蝎之心。茶壶造型的星群是银河最浓处。",
            "斗宿六星古称“南斗”，与北斗并列主寿命——“南斗注生，北斗注死”（搜神记）；箕宿四星如簸箕，主风。《诗经》“维南有箕，不可以簸扬”。",
            "Alnasl" to "Kaus Media", "Kaus Media" to "Kaus Australis", "Kaus Australis" to "Ascella",
            "Kaus Media" to "Kaus Borealis", "Kaus Borealis" to "Albaldah", "Albaldah" to "Nunki",
            "Nunki" to "Tau Sagittarii", "Tau Sagittarii" to "Ascella"
        ),
        Def(
            "CAP", "Capricornus", "摩羯座",
            "牧神潘为逃避堤丰跳入尼罗河，半身化作鱼尾的山羊，即“海山羊”。",
            "牛宿与垒壁阵属北方玄武天区，牛宿六星如牵牛之牛（牛郎织女的“牛”另指河鼓），冬至点曾在牛宿初度。",
            "Algedi" to "Dabih", "Dabih" to "Nashira", "Nashira" to "Deneb Algedi",
            "Deneb Algedi" to "Zeta Capricorni", "Zeta Capricorni" to "Theta Capricorni",
            "Theta Capricorni" to "Dabih"
        ),
        Def(
            "AQR", "Aquarius", "宝瓶座",
            "宙斯的斟酒美少年伽倪墨得斯，宝瓶倾倒的琼浆化作秋夜星空。",
            "危宿（玄乌龟尾）与虚宿（玄武之身）所在，“虚”主哭泣丧亡，古星占视为肃杀之地；垒壁阵十二星横亘如长城。",
            "Sadalsuud" to "Sadalmelik", "Sadalmelik" to "Sadachbia", "Sadachbia" to "Sadaltager",
            "Sadaltager" to "Ancha", "Ancha" to "Skat"
        ),
        Def(
            "ARI", "Aries", "白羊座",
            "拯救弗里克索斯与赫勒的金毛羊，羊皮后来成为阿尔戈英雄追寻的金羊毛。",
            "娄宿三星属西方白虎，主牧养牺牲以供祭祀；娄通“系马”，古人以娄宿占牛马繁息。",
            "Hamal" to "Sheratan", "Sheratan" to "Mesarthim", "Hamal" to "Bharani", "Hamal" to "Botein"
        ),
        Def(
            "PSC", "Pisces", "双鱼座",
            "阿佛洛狄忒与厄洛斯化作双鱼跃入尼罗河逃脱堤丰，以丝带相连。春分点现居此处。",
            "奎宿（白虎之足）与外屏、云雨诸官所在。奎宿十六星“天之武库”，主文事亦主沟渎；古人以奎宿明亮占文章之盛。",
            "Gamma Piscium" to "Lambda Piscium", "Lambda Piscium" to "Omega Piscium",
            "Omega Piscium" to "Iota Piscium", "Iota Piscium" to "Delta Piscium",
            "Delta Piscium" to "Epsilon Piscium", "Epsilon Piscium" to "Omicron Piscium",
            "Omicron Piscium" to "Eta Piscium", "Eta Piscium" to "Alrescha"
        ),
        Def(
            "CNC", "Cancer", "巨蟹座",
            "赫拉派去干扰赫拉克勒斯的巨蟹，被一脚踩碎后升天。鬼星团 M44 藏于蟹壳之中。",
            "鬼宿四星围“积尸气”（鬼星团 M44）——古星占认为其明则天下凶；柳宿八星为朱鸟之喙，主草木。",
            "Asellus Australis" to "Asellus Borealis", "Asellus Australis" to "Acubens",
            "Asellus Borealis" to "Iota Cancri", "Asellus Australis" to "Tarf"
        ),
        Def(
            "BOO", "Boötes", "牧夫座",
            "发明犁的牧夫或被熊撕碎的酒神侍者，大角是北极的守望者，北斗斗柄弧线直指牧夫。",
            "大角星在《史记·天官书》中“直斗杓旁”——“大角者，天王帝廷”；亢池、摄提六星拱卫，是苍龙之角所在区域。",
            "Arcturus" to "Izar", "Izar" to "Delta Bootis", "Delta Bootis" to "Nekkar",
            "Nekkar" to "Seginus", "Seginus" to "Muphrid", "Muphrid" to "Arcturus"
        ),
        Def(
            "CRB", "Corona Borealis", "北冕座",
            "酒神狄俄尼索斯赠给新娘阿里阿德涅的七彩花冠，防止悲伤侵蚀她的心。",
            "贯索九星如天之牢狱，“贱人之牢”——主法律禁暴；贯索四（北冕 α）为其中最亮的“贯索”主星。",
            "Theta Coronae Borealis" to "Nusakan", "Nusakan" to "Alphecca",
            "Alphecca" to "Gamma Coronae Borealis", "Gamma Coronae Borealis" to "Delta Coronae Borealis"
        ),
        Def(
            "HER", "Hercules", "武仙座",
            "完成十二伟业的大英雄赫拉克勒斯，跪姿倒挂于北天，一脚踩在天龙的头上。",
            "天纪、七公、帝座诸官所在。“帝座”（ Rasalgethi 附近）传为天帝听政之座；武仙座球状星团 M13 是北天最壮观的球状星团。",
            "Zeta Herculis" to "Eta Herculis", "Eta Herculis" to "Pi Herculis",
            "Pi Herculis" to "Sarin", "Sarin" to "Zeta Herculis",
            "Kornephoros" to "Sarin", "Kornephoros" to "Rasalgethi",
            "Zeta Herculis" to "Mu Herculis", "Pi Herculis" to "Xi Herculis"
        ),
        Def(
            "LYR", "Lyra", "天琴座",
            "俄耳甫斯的七弦琴，琴声能让顽石落泪、冥王动容。织女星是北天第二亮星。",
            "织女三星与中国七夕传说相连：“迢迢牵牛星，皎皎河汉女”；渐台二为著名食双星，古人视其为天上织女的机杼。",
            "Vega" to "Zeta Lyrae", "Zeta Lyrae" to "Sheliak", "Sheliak" to "Sulafat",
            "Sulafat" to "Delta2 Lyrae", "Delta2 Lyrae" to "Zeta Lyrae"
        ),
        Def(
            "CYG", "Cygnus", "天鹅座",
            "宙斯化作天鹅接近勒达，亦说是音乐家俄耳甫斯化鸟。北十字是夏夜银河上的十字架。",
            "天津九星横跨银河——“天津”即天上的渡口，七夕鹊桥传说所在；辇道增七是望远镜时代最美的金蓝双星之一。",
            "Deneb" to "Sadr", "Sadr" to "Albireo", "Sadr" to "Fawaris", "Sadr" to "Aljanah",
            "Fawaris" to "Kappa Cygni", "Deneb" to "Iota Cygni", "Iota Cygni" to "Kappa Cygni",
            "Aljanah" to "Zeta Cygni"
        ),
        Def(
            "AQL", "Aquila", "天鹰座",
            "为宙斯衔取雷霆的神鹰，牛郎星（河鼓二）是夏夜银河东岸的明珠。",
            "河鼓三星即“牵牛”，《古诗十九首》“迢迢牵牛星，盈盈一水间”；河鼓二两旁的旗星被称为“扁担星”，牛郎挑着一双儿女。",
            "Altair" to "Tarazed", "Altair" to "Alshain", "Tarazed" to "Okab",
            "Altair" to "Delta Aquilae", "Delta Aquilae" to "Libertas", "Delta Aquilae" to "Althalimain"
        ),
        Def(
            "OPH", "Ophiuchus", "蛇夫座",
            "医药之神阿斯克勒庇俄斯，手持巨蛇，蛇毒既可杀人亦可救人，连黄道十三星座也有他一席。",
            "天市垣所在——天上的集市，左右垣诸星如城墙，“帝座”居中，侯、宗、宦者诸官环列，是三垣体系中人间城市的投影。",
            "Rasalhague" to "Cebalrai", "Cebalrai" to "Kappa Ophiuchi", "Kappa Ophiuchi" to "Eta Ophiuchi",
            "Eta Ophiuchi" to "Sabik", "Sabik" to "Yed Prior", "Yed Prior" to "Yed Posterior"
        ),
        Def(
            "SER", "Serpens", "巨蛇座",
            "医药之神手中的巨蛇，被蛇夫拦腰分为头尾两段，唯一被分成两部分的星座。",
            "天市右垣诸官与周增星所在。蛇头诸星临近天市西垣，是“天庭集市”的西墙。",
            "Beta Serpentis" to "Delta Serpentis", "Delta Serpentis" to "Unukalhai",
            "Unukalhai" to "Mu Serpentis", "Mu Serpentis" to "Eta Serpentis",
            "Eta Serpentis" to "Xi Serpentis"
        ),
        Def(
            "CEN", "Centaurus", "半人马座",
            "贤者喀戎（或野蛮的半人马涅索斯），南门二是离太阳最近的恒星系统。",
            "库楼、骑官、南门诸星官所在。“南门”即天门，主边兵出入；马腹一与南门二并称“ pointers”，指向南十字。",
            "Rigil Kentaurus" to "Hadar", "Hadar" to "Izar Centauri", "Izar Centauri" to "Ma Wei",
            "Ma Wei" to "Fawaris Centauri", "Fawaris Centauri" to "Rigil Kentaurus",
            "Muhlifain" to "Iota Centauri", "Iota Centauri" to "Menkent", "Menkent" to "Fawaris Centauri"
        ),
        Def(
            "CRU", "Crux", "南十字座",
            "大航海时代的导航圣物，四星构成南天最紧凑的十字，延长四倍半即达南天极。",
            "十字架诸星在明清星图中被称为“十字”（近南极星官），郑和下西洋的牵星过洋术曾以邻近星体导航。",
            "Acrux" to "Gacrux", "Mimosa" to "Imai", "Imai" to "Ginan"
        ),
        Def(
            "CAR", "Carina", "船底座",
            "南船阿尔戈号的龙骨，载着伊阿宋寻取金羊毛。老人星是南天第二亮星。",
            "老人星即“南极老人”，主寿昌，见则天下安，《史记》“狼比地有大星，曰南极老人”；古都长安因纬度太高极难见到，故有“寿星现，天下安”之说。",
            "Canopus" to "Regor", "Canopus" to "Avior", "Avior" to "Aspidiske",
            "Aspidiske" to "Theta Carinae", "Theta Carinae" to "Vathorz", "Vathorz" to "Miaplacidus"
        ),
        Def(
            "VEL", "Vela", "船帆座",
            "南船号的船帆，帆上挂着“假十字”。天社一是个罕见的沃尔夫-拉叶双星。",
            "天社、东瓯诸星官所在，“天社”为祭祀之所；船帆座超新星遗迹是全天最明亮的 X 射线源之一。",
            "Regor" to "Alsephina", "Alsephina" to "Markeb", "Markeb" to "Suhail",
            "Suhail" to "Mu Velorum", "Regor" to "N Velorum"
        ),
        Def(
            "PUP", "Puppis", "船尾座",
            "南船号的船尾。弧矢增廿二（Naos）是银河系中极热极亮的 O 型超巨星。",
            "弧矢诸星属井宿天区，“弧矢九星在天狼东南，天弓也”——正是射天狼的弓！苏轼词中意象的天文实体。",
            "Ahadi" to "Sigma Puppis", "Sigma Puppis" to "Naos", "Naos" to "Tureis",
            "Ahadi" to "Tau Puppis"
        ),
        Def(
            "HYA", "Hydra", "长蛇座",
            "九头蛇许德拉——赫拉克勒斯第二伟业，被斩一头生两头，最终以火烧尽。全天最长的星座。",
            "星宿居朱鸟之颈——“星”即天之核心，柳、星、张三宿构成朱鸟之嗉与咽；《史记》以星宿明暗占王室。",
            "Epsilon Hydrae" to "Zeta Hydrae", "Zeta Hydrae" to "Alphard",
            "Alphard" to "Nu Hydrae", "Nu Hydrae" to "Gamma Hydrae", "Gamma Hydrae" to "Pi Hydrae"
        ),
        Def(
            "CET", "Cetus", "鲸鱼座",
            "要吞噬安德洛墨达的海怪刻托。蒭藁增二是第一颗被发现的变星，“怪物”之名实至名归。",
            "天仓、天囷诸官司粮廪，“天仓”主藏粟，“天囷”为天下粮仓；土司空一星明亮橙红，为秋季南天路标。",
            "Menkar" to "Kaffaljidhma", "Menkar" to "Mira", "Mira" to "Baten Kaitos",
            "Baten Kaitos" to "Tau Ceti", "Tau Ceti" to "Diphda"
        ),
        Def(
            "ERI", "Eridanus", "波江座",
            "法厄同坠落的燃烧之河，从猎户脚边蜿蜒流向南天尽头的水委一。",
            "天苑、天囷绵延天区。“天苑”为天子之苑囿，养禽兽之所；波江座 ε 星（天苑四）是科幻作品中人类的近邻家园。",
            "Cursa" to "Rana", "Rana" to "Epsilon Eridani", "Epsilon Eridani" to "Zaurak",
            "Zaurak" to "Acamar", "Acamar" to "Achernar"
        ),
        Def(
            "CRV", "Corvus", "乌鸦座",
            "阿波罗的圣鸦，因谎报消息被罚永远口渴，杯子（长蛇座水杯）近在咫尺却够不着。",
            "轸宿四星为南方朱雀第七宿，主车骑之事；轸宿旁“长沙”一星（未收录）主子孙，星占家以轸占丧车。",
            "Gienah Corvi" to "Algorab", "Algorab" to "Kraz", "Kraz" to "Minkar", "Minkar" to "Gienah Corvi"
        ),
        Def(
            "TRI", "Triangulum", "三角形座",
            "因形得名的古老星座，托勒密四十八星座之一，也有人视其为尼罗河三角洲的象征。",
            "天大将军诸星所在。三角形座内藏 M33 风车星系——本星系群第三大成员，黑暗夜空中的肉眼挑战目标。",
            "Mothallah" to "Beta Trianguli"
        ),
        Def(
            "DEL", "Delphinus", "海豚座",
            "音乐家阿里翁遇海盗，跃海被海豚所救。星名 Sualocin/Rotanev 是其发现者名字倒拼的恶作剧。",
            "瓠瓜五星如葫芦，主果园草野；败瓜四星在其南，主瓜果之属。小巧的海豚紧邻银河之滨。",
            "Sualocin" to "Rotanev", "Sualocin" to "Gamma Delphini",
            "Rotanev" to "Epsilon Delphini", "Epsilon Delphini" to "Gamma Delphini"
        ),
        Def(
            "MON", "Monoceros", "麒麟座",
            "从狮子座与大犬座之间“发现”的独角兽，虽是现代星座却占据冬夜银河最华丽的段落。",
            "四渎、阙丘诸星官所在，“四渎”即江、河、淮、济四条大河的天上映像；麒麟座 β 是精致的肉眼看三合星。",
            "Beta Monocerotis" to "Gamma Monocerotis", "Gamma Monocerotis" to "Alpha Monocerotis"
        ),
        Def(
            "LEP", "Lepus", "天兔座",
            "被猎户俄里翁追猎的野兔，脚下的厕一、厕二暗指猎人的休息处。",
            "厕星官所在，主疾病灾害；《军井》《厕》诸星占候军中疫病，是古代星占术的独特一角。",
            "Arneb" to "Nihal"
        ),
        Def(
            "GRU", "Grus", "天鹤座",
            " Bayer 于 1603 年划分的南方星座，取代原来的“南鱼之尾”。",
            "败瓜延伸的南方天区，明清之际传教士带来的近南极星官补充了这片中原不可见的天空。",
            "Aldhanab" to "Alnair", "Alnair" to "Tiaki"
        ),
        Def(
            "TRA", "Triangulum Australe", "南三角座",
            "航海家凯泽与普兰修斯命名的南天星座，与北天三角形遥相呼应。",
            "近南极星官之一，清代《仪象考成》将其纳入“三角形”增星体系。",
            "Atria" to "Beta Trianguli Australis", "Beta Trianguli Australis" to "Gamma Trianguli Australis",
            "Gamma Trianguli Australis" to "Atria"
        ),
        Def(
            "ARA", "Ara", "天坛座",
            "诸神盟誓的祭坛，宙斯在此与诸神立誓讨伐提坦。",
            "杵、龟诸星官所在，属近南极星官；“杵”主舂粮，“龟”主卜筮，名物皆取象于人间。",
            "Alpha Arae" to "Beta Arae", "Beta Arae" to "Gamma Arae", "Gamma Arae" to "Alpha Arae"
        ),
        Def(
            "LUP", "Lupus", "豺狼座",
            "被半人马刺杀献祭的野兽，或曰是吕卡翁王变形的狼。",
            "骑官、从官诸星所在，属天市垣南门外；骑官十星主天子宿卫骑从。",
            "Kakkab" to "Beta Lupi", "Beta Lupi" to "Gamma Lupi", "Gamma Lupi" to "Delta Lupi",
            "Kakkab" to "Zeta Lupi", "Zeta Lupi" to "Delta Lupi"
        )
    )

    /** 解析后的星座列表：edges 已映射为亮星 id 对。 */
    val constellations: List<Constellation> = defs.map { d ->
        val edges = d.edges.map { (a, b) -> BrightStarCatalog.idOf(a) to BrightStarCatalog.idOf(b) }
        val members = edges.flatMap { listOf(it.first, it.second) }.distinct()
        Constellation(d.abbr, d.nameEn, d.nameZh, d.storyWestern, d.storyChinese, edges, members)
    }

    /** 星座中心（成员星单位向量均值方向）。 */
    fun centerRaDec(c: Constellation): AstrometryEngine.RaDec {
        var sum = Vec3.ZERO
        for (id in c.memberStarIds) {
            val st = BrightStarCatalog.byId(id)
            val ra = Math.toRadians(st.raDeg)
            val dec = Math.toRadians(st.decDeg)
            sum += Vec3(kotlin.math.cos(dec) * kotlin.math.cos(ra),
                kotlin.math.cos(dec) * kotlin.math.sin(ra), kotlin.math.sin(dec))
        }
        val u = sum.normalized()
        val dec = Math.asin(u.z.coerceIn(-1.0, 1.0))
        val ra = AstrometryEngine.normalizeDeg(Math.toDegrees(kotlin.math.atan2(u.y, u.x)))
        return AstrometryEngine.RaDec(ra, Math.toDegrees(dec))
    }

    fun byAbbr(abbr: String): Constellation? = constellations.firstOrNull { it.abbr == abbr }
}
