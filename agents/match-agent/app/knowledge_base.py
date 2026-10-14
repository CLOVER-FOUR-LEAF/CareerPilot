"""
技能词表与领域词库。

这是 match-agent 的"知识库"：在没有 LLM 的情况下，靠这里的词表完成
技能抽取、技能覆盖判定、证书缺失识别和业务指标识别。
覆盖互联网 / 金融 / 制造业三大类岗位，兼顾中文与英文表述。
"""

# ─── 技能词表 ──────────────────────────────────────────────────
# 结构：规范名 -> (category, 别名元组)
# category: technical 技术栈 / soft_skill 软技能 / tool 工具 / language 语言 / domain 业务领域

SKILL_LEXICON: dict[str, tuple[str, tuple[str, ...]]] = {
    # ── 编程语言 ──
    "分布式系统": ("domain", ("分布式系统", "distributed system", "分布式架构")),
    "微服务": ("domain", ("微服务", "microservice", "micro service", "服务拆分")),
    "高并发": ("domain", ("高并发", "high concurrency", "大流量")),
    "高可用": ("domain", ("高可用", "high availability", "容灾", "故障转移")),
    "系统设计": ("domain", ("系统设计", "架构设计", "system design", "技术架构")),
    "性能优化": ("domain", ("性能优化", "性能调优", "performance tuning", "调优")),

    "Java": ("technical", ("java", "jdk", "j2ee", "javase", "javaee")),
    "Python": ("technical", ("python", "py3", "python3")),
    "Go": ("technical", ("golang", "go lang", "go语言")),
    "C++": ("technical", ("c++", "cpp", "c plus plus")),
    "C#": ("technical", ("c#", "csharp", ".net core", "dotnet")),
    "Rust": ("technical", ("rust",)),
    "Scala": ("technical", ("scala",)),
    "Kotlin": ("technical", ("kotlin",)),
    "PHP": ("technical", ("php",)),
    "SQL": ("technical", ("sql", "t-sql", "plsql")),
    "Shell": ("technical", ("shell", "bash", "shell脚本")),

    # ── 后端框架 ──
    "Spring Boot": ("technical", ("spring boot", "springboot", "spring-boot")),
    "Spring Cloud": ("technical", ("spring cloud", "springcloud")),
    "Spring": ("technical", ("spring",)),
    "Django": ("technical", ("django",)),
    "Flask": ("technical", ("flask",)),
    "FastAPI": ("technical", ("fastapi",)),
    "Node.js": ("technical", ("node.js", "nodejs", "node")),
    "Express": ("technical", ("express", "express.js")),
    "NestJS": ("technical", ("nestjs", "nest.js")),
    "Gin": ("technical", ("gin框架", "gin")),

    # ── 前端 ──
    "Vue": ("technical", ("vue", "vue.js", "vue3", "vue2")),
    "React": ("technical", ("react", "react.js", "reactjs")),
    "Angular": ("technical", ("angular",)),
    "TypeScript": ("technical", ("typescript", "ts")),
    "JavaScript": ("technical", ("javascript", "js", "es6")),
    "HTML/CSS": ("technical", ("html", "css", "html5", "css3", "sass", "less")),
    "Webpack": ("technical", ("webpack", "vite", "rollup")),
    "Flutter": ("technical", ("flutter", "dart")),
    "小程序": ("technical", ("小程序", "微信小程序", "miniprogram")),

    # ── 数据存储 ──
    "MySQL": ("technical", ("mysql", "mariadb")),
    "PostgreSQL": ("technical", ("postgresql", "postgres", "pg")),
    "Oracle": ("technical", ("oracle", "oracle db")),
    "MongoDB": ("technical", ("mongodb", "mongo")),
    "Redis": ("technical", ("redis",)),
    "Elasticsearch": ("technical", ("elasticsearch", "es搜索", "elastic search")),
    "ClickHouse": ("technical", ("clickhouse",)),
    "HBase": ("technical", ("hbase",)),
    "Hive": ("technical", ("hive",)),
    "Hadoop": ("technical", ("hadoop", "hdfs")),
    "Spark": ("technical", ("spark", "pyspark")),
    "Flink": ("technical", ("flink",)),
    "数据仓库": ("technical", ("数据仓库", "数仓", "data warehouse", "数仓建模")),

    # ── 消息与中间件 ──
    "Kafka": ("technical", ("kafka",)),
    "RocketMQ": ("technical", ("rocketmq",)),
    "RabbitMQ": ("technical", ("rabbitmq",)),
    "消息队列": ("technical", ("消息队列", "mq", "message queue")),

    # ── 云与运维 ──
    "Docker": ("technical", ("docker", "容器化", "containerd")),
    "Kubernetes": ("technical", ("kubernetes", "k8s")),
    "CI/CD": ("technical", ("ci/cd", "cicd", "持续集成", "持续交付", "jenkins", "gitlab ci")),
    "Linux": ("technical", ("linux", "unix", "centos", "ubuntu")),
    "Nginx": ("technical", ("nginx",)),
    "Git": ("technical", ("git", "svn")),
    "Prometheus": ("technical", ("prometheus",)),
    "Grafana": ("technical", ("grafana",)),
    "Arthas": ("technical", ("arthas",)),
    "Istio": ("technical", ("istio", "服务网格", "service mesh")),

    # ── 云厂商 ──
    "AWS": ("technical", ("aws", "amazon web services")),
    "阿里云": ("technical", ("阿里云", "aliyun", "alibaba cloud")),
    "腾讯云": ("technical", ("腾讯云", "tencent cloud")),
    "华为云": ("technical", ("华为云", "huawei cloud")),
    "Azure": ("technical", ("azure",)),

    # ── 移动端 ──
    "Android": ("technical", ("android", "安卓")),
    "iOS": ("technical", ("ios", "objective-c", "swift")),
    "HarmonyOS": ("technical", ("harmonyos", "鸿蒙")),

    # ── 测试 ──
    "单元测试": ("technical", ("单元测试", "unit test", "junit", "pytest")),
    "性能测试": ("technical", ("性能测试", "压测", "jmeter", "loadrunner", "stress test")),
    "自动化测试": ("technical", ("自动化测试", "selenium", "appium", "playwright")),

    # ── 数据与算法 ──
    "机器学习": ("technical", ("机器学习", "machine learning", "ml")),
    "深度学习": ("technical", ("深度学习", "deep learning", "dl")),
    "NLP": ("technical", ("nlp", "自然语言处理", "自然语言")),
    "大模型": ("technical", ("大模型", "llm", "大语言模型", "gpt", "prompt")),
    "PyTorch": ("technical", ("pytorch", "torch")),
    "TensorFlow": ("technical", ("tensorflow",)),
    "推荐系统": ("technical", ("推荐系统", "推荐算法", "ctr", "召回", "精排")),
    "特征工程": ("technical", ("特征工程", "feature engineering")),
    "A/B Testing": ("technical", ("a/b test", "ab test", "ab测试", "a/b测试", "灰度实验")),
    "数据分析": ("technical", ("数据分析", "data analysis", "取数", "报表分析")),
    "Pandas": ("technical", ("pandas", "numpy")),

    # ── 产品与项目管理 ──
    "产品设计": ("technical", ("产品设计", "原型", "axure", "figma", "产品方案")),
    "需求分析": ("soft_skill", ("需求分析", "需求评审", "需求管理", "prd")),
    "敏捷开发": ("soft_skill", ("敏捷", "agile", "scrum", "看板", "迭代")),
    "项目管理": ("soft_skill", ("项目管理", "project management", "pmp")),
    "用户增长": ("domain", ("用户增长", "growth", "拉新", "留存", "获客")),
    "用户运营": ("domain", ("用户运营", "私域", "社群运营")),

    # ── 金融 ──
    "财务分析": ("domain", ("财务分析", "financial analysis", "财务建模", "三张表")),
    "财务建模": ("domain", ("财务建模", "估值模型", "excel建模", "dcf")),
    "预算管理": ("domain", ("预算管理", "预算编制", "成本控制", "费用管控")),
    "审计": ("domain", ("审计", "内控", "内审", "合规检查")),
    "CPA": ("certification", ("cpa", "注册会计师")),
    "CFA": ("certification", ("cfa", "特许金融分析师")),
    "ACCA": ("certification", ("acca", "英国皇家特许会计师")),
    "FRM": ("certification", ("frm", "金融风险管理师")),
    "基金从业": ("certification", ("基金从业", "基金从业资格")),
    "证券从业": ("certification", ("证券从业", "证券从业资格")),
    "银行从业": ("certification", ("银行从业", "银行从业资格")),
    "期货从业": ("certification", ("期货从业", "期货从业资格")),
    "税务筹划": ("domain", ("税务筹划", "税务", "纳税申报", "tax")),
    "风控建模": ("domain", ("风控", "风控模型", "信用风险", "credit risk", "反欺诈")),
    "资产管理": ("domain", ("资产管理", "投研", "investment research", "买方", "卖方")),
    "量化交易": ("domain", ("量化交易", "量化投资", "quant", "策略回测")),

    # ── 制造业 ──
    "精益生产": ("domain", ("精益生产", "lean", "精益制造", "6s", "六西格玛")),
    "六西格玛": ("domain", ("六西格玛", "6 sigma", "6sigma", "dmaic")),
    "SMT": ("domain", ("smt", "贴片")),
    "CAD": ("tool", ("cad", "solidworks", "autocad", "creo", "catia")),
    "CAE": ("tool", ("cae", "ansys", "仿真")),
    "MES": ("domain", ("mes", "制造执行系统", "mes系统")),
    "ERP": ("domain", ("erp", "sap", "用友", "金蝶")),
    "PLC": ("domain", ("plc", "西门子", "西门子s7", "触摸屏")),
    "SCADA": ("domain", ("scada", "工控机", "组态软件")),
    "SPC": ("domain", ("spc", "统计过程控制", "过程能力", "cpk")),
    "BOM": ("domain", ("bom", "物料清单")),
    "良率提升": ("domain", ("良率", "直通率", "一次合格率", "制程改善")),
    "自动化产线": ("domain", ("自动化产线", "产线", "装配线", "机器人")),
    "IOT": ("technical", ("iot", "物联网", "mqtt")),
    "工业互联网": ("domain", ("工业互联网", "iiot", "数字孪生", "twin")),

    # ── 通用软技能 ──
    "沟通协调": ("soft_skill", ("沟通", "协调", "协作", "communication")),
    "团队管理": ("soft_skill", ("团队管理", "带团队", "人员管理", "leadership")),
    "逻辑思维": ("soft_skill", ("逻辑思维", "逻辑清晰", "结构化思维")),
    "抗压能力": ("soft_skill", ("抗压", "承压", "高压下")),
    "学习能力": ("soft_skill", ("学习能力", "快速学习", "自驱")),
    "英语能力": ("language", ("英语", "英文", "cet-6", "cet6", "六级", "雅思", "托福", "ielts", "toefl")),
    "日语能力": ("language", ("日语", "jlpt", "n1", "n2")),
}

# ─── 证书识别 ──────────────────────────────────────────────────
# 用于 gap_list.missing_cert：JD 里提到但简历里没有的硬证书

CERT_ALIASES: dict[str, tuple[str, ...]] = {
    "注册会计师（CPA）": ("cpa", "注册会计师"),
    "CFA 特许金融分析师": ("cfa",),
    "ACCA": ("acca",),
    "FRM 金融风险管理师": ("frm",),
    "基金从业资格证": ("基金从业",),
    "证券从业资格证": ("证券从业",),
    "银行从业资格证": ("银行从业",),
    "期货从业资格证": ("期货从业",),
    "PMP 项目管理认证": ("pmp",),
    "教师资格证": ("教师资格",),
    "注册建造师": ("建造师", "一级建造师", "二级建造师"),
    "注册安全工程师": ("注册安全工程师",),
    "法律职业资格证": ("法律职业资格", "法考"),
    "CISP 信息安全认证": ("cisp",),
    "软考（中/高级）": ("软考", "系统架构设计师", "系统分析师", "信息系统项目管理师"),
}

# ─── 业务指标关键词 ────────────────────────────────────────────
# 用于 gap_list.weak_metric：JD 强调业务指标，但简历完全没有量化表达

METRIC_KEYWORDS: tuple[str, ...] = (
    "转化率", "留存率", "复购率", "gmv", "roi", "roas", "kpi", "okr",
    "营收", "毛利", "利润", "成本", "降本", "增效", "回款", "坏账",
    "uv", "pv", "dau", "mau", " dau", "渗透率", "市占率", "份额",
    "良率", "直通率", "产能", "交付周期", "库存周转", "稼动率", "oee",
    "产能利用率", "工艺良率", "报废率",
)

# ─── 学历层级 ──────────────────────────────────────────────────

EDUCATION_LEVELS: dict[str, int] = {
    "大专": 1, "专科": 1, "高职": 1,
    "学士": 2, "本科": 2,
    "硕士": 3, "研究生": 3, "碩士": 3,
    "博士": 4,
    "博士后": 5,
}

EDUCATION_ALIASES: tuple[str, ...] = (
    "大专", "专科", "高职", "学士", "本科", "硕士", "研究生", "博士", "博士后",
)

# ─── 简历年限提取用的时间区间正则 ──────────────────────────────

DATE_RANGE_PATTERN = (
    r'(\d{4})\s*[./年\-~至到]\s*(\d{1,2})?\s*月?\s*[-~至到—]{1,2}\s*'
    r'(至今|now|present|\d{4})'
)


def normalize(text: str) -> str:
    """统一小写并压缩空白，便于子串匹配"""
    return " ".join(text.lower().split())


def build_alias_index() -> dict[str, str]:
    """别名 -> 规范技能名 的反向索引"""
    index: dict[str, str] = {}
    for canonical, (_category, aliases) in SKILL_LEXICON.items():
        for alias in aliases:
            index[alias.lower()] = canonical
    return index


ALIAS_INDEX: dict[str, str] = build_alias_index()


def build_subsumption_index() -> dict[str, tuple[str, ...]]:
    """
    计算"被包含"关系。

    若技能 A 的每一个别名都是技能 B 某个别名的子串，则 A 被 B 包含
    （例如 Spring 的别名 spring 完整包含于 Spring Boot 的别名 spring boot）。
    抽取时若 B 已命中且 A 未独立出现，则丢弃 A，避免同一处命中被计两次分。
    """
    aliases_by_skill = {name: tuple(aliases) for name, (_c, aliases) in SKILL_LEXICON.items()}
    subsumed: dict[str, tuple[str, ...]] = {}
    for skill_a, aliases_a in aliases_by_skill.items():
        superseders: list[str] = []
        for skill_b, aliases_b in aliases_by_skill.items():
            if skill_b == skill_a:
                continue
            if all(
                any(alias_a in alias_b for alias_b in aliases_b)
                for alias_a in aliases_a
            ):
                superseders.append(skill_b)
        if superseders:
            subsumed[skill_a] = tuple(sorted(superseders, key=len))
    return subsumed


SUBSUMED_BY: dict[str, tuple[str, ...]] = build_subsumption_index()


def all_aliases() -> list[tuple[str, str]]:
    """返回 (别名, 规范名) 列表，按别名长度倒序，便于优先匹配更长的词"""
    return sorted(ALIAS_INDEX.items(), key=lambda kv: len(kv[0]), reverse=True)
