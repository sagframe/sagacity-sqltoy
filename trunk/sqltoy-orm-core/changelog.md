# v6.0.11 2026-11-19
1、elastic查询统一走rest5客户端:直连httpclient分支移除,单地址/多地址/eql自定义url行为一致;单地址的base path(网关子路径,如http://host:9200/es)由Rest5ClientBuilder.setPathPrefix承载,配置url本身已指向/_sql或/{index}/_search时自动剥离尾段避免重复拼接;连接复用/keep-alive由rest5客户端连接池提供,池中连接被服务端/LB单方面关闭时重建请求重试一次(查询类POST可安全重发);eql自定义url端点按url缓存并由SqlToyContext.destroy统一关闭(原逐次新建端点导致客户端无法复用、无法释放)
2、elastic超时与字符集统一为"endpoint默认+语句覆盖":eql上显式配置的request-timeout/connection-timeout/socket-timeout/charset优先,未配置时取ElasticEndpoint(spring/solon的ElasticConfig)——修复"XML属性只对单地址生效、ElasticConfig超时只对多地址生效、ElasticConfig.charset完全无效"的静默分叉;异步客户端超时只能在构建时确定,故超时组合与endpoint默认值不同的语句使用独立rest5客户端;NoSqlConfigModel新增hasRequestTimeout/hasConnectTimeout/hasSocketTimeout/isCharsetSet用于判定显式配置,既有getter签名与默认值(30s/10s/180s/UTF-8)不变
3、单地址兼容性:原单地址配置(含base path、url已带_sql/_search、eql自定义url)均保持可用,地址解析新增pathPrefix剥离规则;连接由rest5客户端管理(HTTP/1.1 keep-alive,不再发送Connection: close);新增HttpClientUtilsPoolTest(连接复用/无close头/陈旧连接重试)、配置优先级/端点缓存/路径组织单测,以及ElasticRealServerTest(真实ES 9.5.2:单/多地址查询、url尾缀base path、连接复用统计、超时覆盖、游标分页、错误响应)
4、SQL解析反斜杠方言体系修复(MySQL系\'为转义、标准SQL反斜杠不转义,此前按单一语义处理互相误伤):①CTE解析越界——SqlWithAnalysis的字面量掩码与CTE主体配对扫描(bracket pairing)按方言区分引号转义语义,MySQL CTE字面量内的\'原被标准语义误判为闭合引号导致括号配对错位、CTE定义被截断或解析越界;CTE主体括号未闭合时由静默截断/越界改为响亮抛出明确异常;单参构造保持标准语义(false)不变,(sql,dbType)构造按方言判定,count改写/分页/top各调用方同步传入执行库语义;新增SqlWithAnalysisBackslashTest(12例,反斜杠与括号扫描深度矩阵);②函数参数切分——FunctionUtils的参数切分改用方言感知的splitFunctionArgs(包私有,不改变StringUtil.splitExcludeSymMark全局语义):跳过三类括号嵌套与引号字面量,MySQL系按"奇数个连续反斜杠转义后续引号"、标准SQL不转义(支持SQL Server/PostgreSQL的'C:\'这类合法字面量),getSymMarkIndexSkipQuoted增backslashEscape重载;新增FunctionUtilsSplitArgsTest(10例)
5、新增like过滤元素并统一LIKE转义:filters新增<like params="..." mode="both|left|right" escape-mode="raw|legacy" escape-percent="true|false" append-str="..."/>(xsd同步);escape-mode=raw为数据库无关的字面量语义(值中\与_均为字面字符,不转义_、按escape-percent决定%是否转义),legacy为历史幂等转义口径;处理顺序固定为"先转义值、再拼appendStr、最后补%",规避值末尾反斜杠把补入的%转义掉;新元素容器支持统一扩展(CharSequence/String[]/基本类型数组/List/Set/其它Collection,基本类型数组转Object[]避免ArrayStoreException),旧l-like/r-like保持历史容器范围(String/String[]/List/Set)避免存量行为变化;LIKE值的反斜杠转义按目标方言判定(mysql系\为转义符);新增LikeFilterElementTest(12例)与verify工程LikeFilterElementSpringTest(H2真库执行,新旧filter同上下文同参数名互不干扰);注意:sql xml中手写escape子句须按方言书写(MySQL须写ESCAPE '\\'),推荐省略由框架按执行库自动追加方言正确的ESCAPE子句;配套修复中央解析链与lambda like族的转义顺序缺陷:①processLike判定"值是否已含未转义%"由replace("\%","")改为反斜杠奇偶扫描——\\%中的%是未转义通配符(前导\\为字面反斜杠),旧实现误剥离致like过滤器right模式对尾部\的值先转义为\\再补%后被误判"无通配符",补的%被当字面量转义、前缀匹配退化为全模糊(新<like>与旧r-like同病);②lambda的wrapLike/exactLike对齐过滤器"先转义值最后补%"同序——wrapLike对不含%的值先转义反斜杠再补%(含%早退的调用方自带通配符契约不变),exactLike先转义反斜杠再转义%,消除like/notLike/likeRight/likeExact*尾部\把补的%转义为字面量的同类缺陷(单表与join双通道共用AbstractWhere同获修复);新增LikeFilterChainParityTest(6例:新旧like过滤链→processLike→H2真实执行矩阵)与LambdaLikeBackslashH2Test(10例:like族尾部反斜杠方向语义与字面%/_),SqlConfigParseComboEdgeTest增奇偶判定3例、LikeFilterElementTest增right模式尾部反斜杠H2端到端1例
6、hasOrderBy对排序表达式自带括号的误判修复:原判定要求order by位于全串最后一个')'之后,order by nvl(a,0)/(case when ...)等排序表达式自带的收括号会把最外层排序误判为无排序,方言top/随机取数链路据此误包派生表;新增isTopLevelOrderBy相对深度复核——以关键字所在层深度为基准向后扫描,出现使深度低于基准的')'说明关键字处于子查询/over(...)等括号内,排序表达式自身的括号先开后闭不会降低基准深度;复核遍历全部order by候选(最后一个候选可能位于排序表达式内的窗口函数中,如order by a, row_number() over (order by b),此时更靠前的候选才是最外层排序);该分支只把false纠正为true、不把旧true改false,历史口径不变;UPCASE_ORDER标记(强制不包裹)同步受益;新增SqlUtilHasOrderByDeepTest(14例深度矩阵)
7、健壮性与口径收口批次:①DATE/TIME/TIMESTAMP字符串参数绑定——DateUtil.parseString解析失败返回null,原new Date(null.getTime())直接NPE掩盖真实原因(值不可解析),改为抛出带参数值与jdbcType的IllegalArgumentException;②Boot fat-jar嵌套根比对的URL形态加固——Windows盘符下nested:/C:/与nested:C:/两种写法、部署路径含空格/中文时加载器产生的URL编码路径(%xx)与java.class.path的原始字符路径双形态比对,原单形态匹配漏配致外层jar重复补根、同一sqlId以两种URL各命中一次(breakWhenSqlRepeat默认true时阻断启动);③BeanUtil的Short解析口径——原经Double中转在越界时静默回绕("99999"→-31073),改为与Integer/Long同口径(toIntegerText归一后精确解析,越界抛NumberFormatException),属"静默错值→响亮报错"的行为变化,存量依赖回绕值的场景需修正数据;③b Byte分支同步归一——原裸Byte.valueOf(paramValue.toString())游离于文本归一体系之外(空串/带空白/小数/千分位文本全抛NumberFormatException,枚举取name而非key),同样改为enumToString+空串null/0+toIntegerText归一(实测对照:Integer对同输入全部优雅处理而Byte全抛异常),byte范围内不存在合法千分位形态故千分位输入统一按越界显式报错;④DataSourceUtils的dbType→dialect映射switch去重(改用既有映射表,行为等价);新增SqlUtilDateTextParamTest(6例)、SqlResourceScannerRootAndDuplicateTest扩充3例、BeanUtilIntegerTextConvertTest扩充
8、主键生成器缓存的多上下文串用修复(EntityManager.IdGeneratorsInstance由static改为实例字段):原JVM级共享导致首个解析实体的上下文独占初始化生成器——上下文绑定型生成器(如RedisIdGenerator,其SpringRedisIdGenerator在initialize时持有该上下文的redisTemplate)被固化给首个上下文,后续上下文containsKey短路跳过初始化而串用其连接资源;同JVM多Spring上下文场景下后初始化上下文自带的redis被无视(quickstart CI实测:Lesson10先懒解析DeviceOrder元数据将生成器绑给主上下文,Lesson01的嵌入式redis失效,save走了主上下文无redis的STOPPED工厂报"LettuceConnectionFactory has been STOPPED",测试类执行顺序变化即触发,此前全绿属类序运气);EntityManager与元数据缓存entitysMetaMap本为每SqlToyContext一份,生成器改为随上下文走,与cascadeModels改ClassValue同属"静态结构持有上下文资源"治理;无状态生成器(Default/NanoTime/Snowflake/UUID等)行为不变,单上下文应用零影响;新增EntityManagerIdGeneratorScopeTest(3例:跨上下文实例隔离、同上下文按名复用、@bean按所属上下文解析)
9、insert/insertIgnore/mergeIgnore静态sql缓存key补齐上下文/连接维度(多上下文或多数据源串用sql的修复):原key=实体类+表名+dbType+主键策略+rowversion,而sql文本还依赖每SqlToyContext一份的IUnifyFieldsHandler(createSqlTimeFields/forceUpdateFields决定时间列是?还是nvl(?,current_timestamp))以及DBProfile的真实库事实(isOceanBase决定向量列是否string_to_vector包装、DB2的majorVersion/hasGseSchema决定空间列包装);静态缓存跨上下文、跨数据源共享,缺维度会让后一个上下文或数据源串用先落地的sql(多上下文不同unifyFieldsHandler时create_time等字段的"库填/应用给"错位、方向不利时报非空约束或时间列丢值;MySQL与按mysql方言配置的OceanBase共存时向量列函数不存在;DB2 11.5与12.1共存时空间函数形态错误);key新增pf(realDBType/isOceanBase/majorVersion/hasGseSchema)与uf(createSqlTimeFields+forceUpdateFields排序摘要,配置等价的上下文仍共享同一条sql),空handler与全空集合归一为"-"保持历史key形态;新增DialectExtUtilsInsertSqlCacheKeyTest(5例:handler时间字段改变sql、同handler仍命中缓存、forceUpdateFields与profile事实改变key、空handler与空集合共享key)
10、JVM级静态配置首占口径收口(functionConverts/keywordSign):SqlToyContext.setFunctionConverts/setKeywordSign原在setter中即时写FunctionUtils/SqlToyConstants的JVM全局值,last-wins且无告警,多上下文(多Spring上下文/测试上下文/热部署)时后配置者会静默覆盖先初始化上下文的SQL函数转换语义与关键字字段引用符号(测试套件里大量直接改全局转换器、清理方式又不可靠,顺序变化即可能翻转与方言转换相关的断言);改为setter仅记录配置、initialize阶段经applyJvmStaticConfig走claimStaticState首占生效——后初始化上下文携带不同配置时告警并忽略,未配置的上下文不认领key以免永久挡住后续真正配置的上下文;spring starter的functionConverts属性经@Bean(initMethod="initialize")在属性绑定后生效、solon builder同样在initialize前set,集成行为不变;close标记仍表示关闭转换但会认领首占权;属行为变化:运行中再调用setFunctionConverts/setKeywordSign不再即时改变JVM全局,需在initialize前配置;新增SqlToyContextGlobalConfigTest(6例:setter延迟到initialize生效、后配置被忽略、keywordSign首占、未配置不认领、close认领并保持关闭)
11、翻译缓存管理器去JVM级共享(TranslateEhcacheManager/TranslateCaffeineManager的cacheManager由static改为实例字段):默认ehcache实现原JVM内所有SqlToyContext共用一个CacheManager——后初始化context的diskStorePath等配置被静默忽略,且任一context销毁会close共享manager并置null,其它存活context的翻译缓存整体失效(读取返回null后退化为每次实时查库,缓存更新watcher的put变no-op);热重启/热部署时旧context的destroy晚于新context的init,新应用启动即丢缓存;caffeine实现同构且cacheNameSet/registerLock为实例字段,后初始化context对同名cache二次registerCustomCache会替换前一个context的缓存对象(规格以最后注册者为准、已缓存内容丢失);改为随SqlToyContext实例化,init/destroy只影响自身,单context行为不变;ehcache缓存创建锁由synchronized(cacheName.intern())改为实例锁(去掉JVM级锁与缓存名常驻字符串常量池),多context共用diskStorePath的锁冲突日志由error降为warn并说明会回退到唯一缓存文件;兼容性提示:impl类的cacheManager字段由static改为实例,已编译的第三方子类需重新编译(TranslateCacheManager接口与公共方法不变,用户显式注入的translateCacheManager语义不变);新增TranslateCacheManagerScopeTest(3例:实例隔离、destroy互不影响、destroy后可重建)与TranslateCaffeineManagerScopeTest(2例)
12、缓存更新检测器去重集合改为解析会话局部(TranslateConfigParse.cacheCheckers静态字段移除):原先为JVM级共享set且每次parseTranslateConfig入口clear,当同一JVM的两个SqlToyContext并发解析translate配置时,一方clear会抹掉另一方刚登记的检测器,导致同一份合法配置偶发抛"duplicate update checker for cache:[xxx]"使context初始化失败(反向交错也会漏检同一文件内的真重复定义);改为parseTranslateConfig方法内创建局部Set并传参给parseTranslate,各上下文解析完全隔离,同一配置文件内的重复定义检测语义不变;新增TranslateConfigSessionTest(3例:单文件重复仍报错、串行两会话互不影响、并发两会话不互相误报),原依赖反射访问静态字段的TranslateCacheCheckersTest移除
13、Solon的DbManager全局门面加固:setContext切换到不同SqlToyContext时清空按DataSource缓存的DAO/LightDao/Service并告警(原先会继续复用绑定旧上下文的实例,导致方言/翻译/加解密等配置错位),同时移除SqlToyDbInjector中未被使用的静态daoMap/serviceMap字段;设计前提仍为"一个JVM一个SqlToyContext"(@Db注入与DbManager均走全局门面),多上下文场景应直接使用各自上下文获取dao;新增DbManagerScopeTest(1例:上下文切换后重建DAO、同上下文保持缓存)
14、Solon插件参数配置与spring starter对齐补齐:①新增solon.sqltoy.jsonProvider配置——6.0.7引入JSON可插拔时仅spring starter透传了属性,solon侧无配置入口(自定义JsonProvider实现、或强制选择与classpath探测优先级不同的实现等场景只能靠自动探测),现与spring.sqltoy.jsonProvider同语义透传到SqlToyContext;②taskExecutor线程池参数补齐——solon侧此前仅targetPoolName生效,threadNamePrefix/corePoolSize/maxPoolSize/queueCapacity/keepAliveSeconds/waitForTasksToCompleteOnShutdown/awaitTerminationSeconds/rejectedExecutionHandler等8项参数配置静默无效(属性类有字段但无任何消费者);targetPoolName为none/空时现按参数构建默认线程池(对齐spring starter行为:专属命名线程池替代此前core的commonPool兜底,参数可控、不与并行流争抢公共池),execute经TtlRunnable包装保障TransmittableThreadLocal上下文传播(等价spring侧TtlExecutors包装);插件stop时经SolonTaskExecutor类型识别关闭自建池(targetPoolName指向的外部线程池bean不受影响,自建池为非守护线程不关闭会阻滞JVM退出),shutdown语义对齐spring的ThreadPoolTaskExecutor(waitForTasksToCompleteOnShutdown决定shutdown/shutdownNow,awaitTerminationSeconds>0时等待);新增SqlToyContextBuilderParityTest(5例:jsonProvider透传、none/空构建默认池且threadNamePrefix生效、外部池名称解析、外部池缺失不越权自建)
15、@secure-loop的skipBlank语义贯穿到循环内引用的集合数据:此前skipBlank只对循环依据变量判null/空白跳过迭代,循环体内引用的其他集合(:ids[i]、:items[i].prop)的null/空白元素未过滤——空白值原样注册,其所在#[...]条件段无法剔除,在"#[..] or #[..]"段间linkSign连接模式下与循环外的过滤预期不一致;现skipBlank=true时本迭代引用值(裸形态与属性形态)存在null/空白即跳过整个迭代,与循环变量自身的跳过同构——从源头不产生输出,#[...]段间linkSign连接符零残留(不产生多余or、无空括号组);属性值预判阶段提取后迭代内复用,无重复反射;@secure-loop-full(skipBlank=false)不做跳过行为不变,数字0/false等非字符串不受影响;SecureSqlLoopFunctionalTest新增9例锁定(裸/属性空白迭代跳过、or-linkSign模式零残留、引用位置无关整体跳过、full形态空白保留与属性即时提取回退分支等),全模块2594例回归全绿
16、@loop的#[...]段剔除后孤立连接符修复+段判定首字符边界:①循环体内#[...]段被引用集合的null/空白值剔除而内容全空时,原实现在段剔除前已拼linkSign,剔空迭代留下孤立的or/and("#[..] or #[..]"模式中间段消失后产生"or or"非法sql,linkSign=and时间理,实测复现);现全空迭代不拼连接符不输出,与第15条@secure-loop的跳过语义对齐;循环体含段外文本时剔段后非全空正常输出不受影响;②hasNullFilter判定原用indexOf("#[")>0,循环体引号紧贴#[开头时(如'#[ ...]'写法)indexOf==0被判false——段跳过宏内判空、值先行内联后全局管线将段视为无参片段整段剔除,有效条件全灭(实测ids含有效值条件数为0),改为>=0覆盖首字符形态;新增SqlLoopBlankRefResidueTest(3例)与LoopDeepEdgeTest(15例:全空白迭代(首/中/尾)零残留、null与纯空白元素跳过、属性缓存跨跳过迭代复用、多key部分空白、full形态段内空白保留、嵌套#[]、紧贴#[边界、全空与空数组行为一致性对照),全模块2612例回归全绿
17、动态缓存管理器去JVM级共享(FIFODynamicFetchCacheManager的状态Map与过期检测调度器由static改为实例级):原JVM内所有SqlToyContext共用动态缓存数据/调用登记/过期时间与单一检测线程——同名cache跨上下文串用数据且keepAlive/容量以首个创建者为准,任一context destroy会停掉全局检测线程(其它存活context的keepAlive清理静默停止、不再自愈)且静态数据不清理(context重建后沿用旧数据);改为随SqlToyContext实例隔离,destroy只停自身检测线程并清空自身缓存数据与过期登记;检测线程改为按需延迟启动(首个keepAlive>0的动态缓存创建时启动,未使用动态缓存的context不再常驻检测线程);重写FIFODynamicFetchCacheManagerTest(13例:跨实例数据隔离、destroy仅清自身、每实例独立daemon检测线程且销毁互不影响、destroy后重建调度器、过期登记清理、损坏登记隔离、FIFO淘汰与clear语义等)
18、类级@Translate注解配置缓存改为ClassValue(TranslateConfigParse.classTranslateConfigMap静态Map<类名,...>移除):原按类名做key的静态缓存,热部署/多classloader场景下同名类重新加载后不会重新解析注解,会继续沿用旧类的缓存翻译定义(cacheName/cacheType/index等全部错位);改为ClassValue——条目挂Class自身,同名类重新加载即按新Class重新解析、类卸载时条目随Class回收,同一Class仍只解析一次,与BeanUtil.cascadeModels/classFieldTypeCache同一治理口径;新增TranslateClassValueTest(3例:同名类双classloader不串配置、同一Class复用并继承父类字段、基础类型/Map返回null)
19、热部署下两处"按类名缓存"的收口:①BeanUtil.enumClassMap(字符串→枚举Class)原固定用框架类加载器Class.forName且按类名无条件缓存,热部署/多classloader下同名枚举类重新加载后仍返回旧Class(枚举参数转换错位、旧类被静态缓存钉住);改为优先线程上下文类加载器(TCCL,不可见时回退框架加载器)解析,并校验缓存Class的装载器归属,装载器变化即重新解析并覆盖(缓存规模仍受枚举类名数量约束);②MapperUtils.classHasAliasMap(key为"class=类名;direct=方向")改为from/target两个ClassValue:同名类重新加载后按新Class重新解析@SqlToyFieldAlias,不再沿用旧别名映射且类卸载条目可回收;新增BeanUtilEnumClassReloadTest(1例:同名枚举类两个独立classloader各自解析、同装载器命中缓存)与MapperUtilsClassValueTest(2例:同名类双classloader不串别名、同Class复用并支持双向映射)
20、BeanUtil/EnumUtil按Class/ClassKey做key的静态缓存统一改ClassValue(热部署类卸载泄漏收口):原setMethods/getMethods、matchSetMethodsCache/matchGetMethodsCache、mappingPlanCache依赖ClassKey,enumGetKeyMethods/enumGetKeyExists、recordProperties、noArgConstructors、EnumUtil.withoutCustomFieldEnumMap直接以Class为key——虽已保证"同名新类不命中旧加载器Method"的正确性,但静态Map强引用Method/Constructor/BeanMappingPlan/PropertyType,热部署/OSGi/应用重载时旧classloader不可回收,反复重启逐代泄漏(类少时条目到不了容量上限尤甚);统一改为ClassValue:条目挂Class自身,同名类重新加载即按新Class重新解析、类卸载随Class回收;ClassKey类删除,内层key改为属性表/列类型签名(匹配与映射计划恢复全JVM总量上限:各10万/2万,ClassValue只负责按类归属与类卸载回收,跨类合计仍不超限),setMethods/getMethods也纳入全JVM总量上限(各10万,原只有按Class无上限),枚举取key方法探测结果用holder承载以避免ClassValue对null不缓存导致重复探测,无参构造器缓存用holder承载NoSuchMethodException保持原checked异常语义;新增BeanUtilClassValueUnloadTest(1例:动态编译+独立classloader建立noArgConstructors/set-get/match/mappingPlan/enum缓存后释放引用,WeakReference实证类与加载器可回收),并适配原依赖反射静态Map的BeanUtilNewBeanTest/BeanUtilMappingPlanTest/BeanUtilMappingPlanBoundedTest为包内测试访问器;对外行为不变,仅内存回收语义变化
21、JWT token缓存过期清理与容量上限(JwtTokenManager.TOKEN_CACHE):原缓存仅在"同一tokenUrl|username再次请求"时覆盖、或服务端401调用invalidate时移除,永不主动清理——动态key场景(多租户/多账号username、token-url带租户、自签名secureKey轮换导致key中密钥摘要变化)长期运行会持续滞留已过期token及其字符串(功能无缺陷:读取始终校验expireAtMillis);现写入缓存前先清理过期条目(purgeExpiredTokens,在token刷新互斥锁内执行),再经CollectionUtil.putBounded按TOKEN_CACHE_MAX_SIZE=2000做容量上限(超限回收至约3/4水位,单飞门闩避免并发过度淘汰);新增JwtTokenManagerTest两例(过期条目在下次写入时被清理且未过期保留、洪泛2200条后容量不超过2000)

22、第19/21条配套缺陷修复(复查发现,红→绿实证):①JWT提前刷新窗口覆盖缺陷——cacheToken改用putBounded(putIfAbsent语义)后,刷新窗口内(未到期)的旧条目不被purge清理,新token无法入缓存,窗口期内每次调用都重新获取token;修复为写入前先remove旧条目(刷新互斥锁内无并发风险),恢复原put覆盖语义;②BeanUtil.isVisibleFrom对bootstrap装载器(null)定义的类恒判不可见,JDK内置枚举每次调用都反复Class.forName;修复为null装载器直接判可见

23、虚表宏@virtualTable()兼容前置from写法(2026-11-16):新增VIRTUAL_TABLE_FROM_REGEX/VIRTUAL_TABLE_FROM_PATTERN,将"from+空白"作为可选组与宏一并匹配、一并替换——select 1 from @virtualTable()在伪表族(oracle/oracle11/dm/oceanbase→dual、db2→sysibm.sysdummy1、hana→dummy)复用前置from只输出一个from(不再产生from from dual),在无from族(mysql系/pg系/sqlserver/sqlite/h2/clickhouse/impala/tdengine及mongo/es/undefine)连from一并剔除(不再残留悬空from,可继续接where/exists/union);from按完整关键字判定(\bfrom\b),address_from/from_address/from_x/transform等标识符内不触发,仅消费与宏直接相邻的from(外层from t、嵌套子查询内层from table、)from紧邻均正确);from与宏之间允许空白/换行/制表且大小写不敏感,单引号字面量先按方言转义规则掩码,字面量内的from/@virtualTable()不参与展开;宏前不写from的既有写法(@virtualTable()自动补from)及别名@dual()/@dummyTable()行为不变;VirtualTableMacroTest由7例扩充到22例,覆盖伪表族单from、db2/hana、无from族剔除、宏前无from时伪表族自动补/无from族不补、union、完整parseSqlToyConfig管线、全DBType双形态、别名/大小写/换行/零空白、null与未知方言、相似宏名、多宏/子查询/CTE/exists、address_from类列名、)from嵌套、单引号字面量、注释穿插与幂等;VirtualTableRealDbTest新增前置from形态及前置from+union兜底行真库验证,docs/multi-db-adaptation中英文说明同步

24、配套的quickvo-maven-plugin升级至2.0.7(项目地址:https://gitee.com/sagacity/maven-quickvo-plugin,完整功能与配置说明参见其README)

25、数组参数绑定与日志拟合方言分派批次(2026-11-21,四引擎真库矩阵实证):①Object[]数组参数与Collection同路径分派(SqlUtil.processParam)——定型数组(String[]等)直接取组件类型,空数组/全null元素亦可定型绑定{}/[{NULL}](此前被静默置SQL NULL),Object[]/Collection按首个非null元素推断经convertArray转具体类型数组后setArray绑定(此前Object[]直落setObject兜底,pg内核系驱动将数组toString发送报malformed array literal/missing dimension value);kingbase纳入createArrayOf分支(其驱动setObject(数组,Types.ARRAY)报Cannot cast,真库实测);②BeanUtil.convertType的List/Set分支由instanceof Object[]放宽为isArray+CollectionUtil.convertArray归一(int[]等原始数组装箱,修复归一化透传原始数组时setter报argument type mismatch),java.sql.Array.getArray()结果同款归一替代(Object[])强转(主流驱动均返回装箱数组,为假想原始数组返回驱动做稳健兜底);③日志拟合(SqlExecuteStat.fitSqlParams)标量比较符(=、!=、<>、>=、<=、>、<)位置的数组/集合参数按方言分派:pg内核系/H2输出ARRAY[..]、clickhouse输出[..](原生数组方言,日志与程序双向一致),无原生数组语义方言输出各库二进制字面量揭示Java序列化流头ACED0005(mysql系X''、oracle/dm HEXTORAW、sqlserver 0x、db2 BX''、hana TO_VARBINARY,未实测方言null兜底)并附array注释保留原始值——消除旧版单元素数组='a'可copy查出结果而程序序列化绑定恒报错的假阳性;in(、between等位置维持逗号串形态,@loop宏拼接真实sql走4参旧签名不受影响;SqlExecuteStat超时sql参数渲染同步经convertArray归一数组/集合(避免Object[]默认toString输出[Ljava.lang.String;@..形态);④复查修复三项(红→绿A/B+真库实证):D1空数组/全null日志形态与绑定侧对齐——定型空数组输出'{}'(pg系,ARRAY[]无法推断元素类型)/ARRAY[](h2)/[](CH),无法定型(Object[]组件/Collection)的空/全null输出null揭示形态(此前combineArray(empty)="null"产出ARRAY[null]即1元素null数组,H2/kingbase真库copy执行与空数组绑定0命中不一致);D2 SqlUtilsExt.resultUpdate的Object[]分支对齐processParam定型归一(此前裸Object[]直传updateObject,kingbase真库报malformed array literal/Cannot cast),无法定型时按isForcedUpdate走null语义(与Collection分支同口径);D3 createArrayOf类型名统一小写pg规范名(integer/varchar/numeric/bigint/float)——gaussdb华为驱动/openGauss/kingbase/vastbase四引擎真库矩阵实证小写全通,大写VARCHAR/NUMERIC在华为驱动报"Unable to find server array type"(按typname精确匹配),NUMBER为oracle别名og/kingbase拒绝、vastbase的number[]无法赋给numeric[]列(此前BigDecimal[]在该分支普遍不可绑定,属既有缺陷一并修复);新增SqlToArrayLogTest(18例)、BeanUtilArrayConvertTypeTest(3例)、SqlUtilsExtArrayResultUpdateTest(5例,Proxy锁死归一与类型名),四引擎×六类型×绑定/回写双路径产品代码矩阵探针48/48全绿,H2/CH/kingbase空数组日志copy执行与绑定对拍一致

26、数组批次复查 round2(2026-11-21,红→绿A/B+四引擎真库实证):①原始类型数组(int[]/long[]/double[]/char[]等)纳入绑定同路径——processParam由instanceof Object[]放宽为isArray,经CollectionUtil.convertArray装箱后按包装类组件定型(toBoxedComponentName,char[]装箱产物String[]按String定型与convertArray一致),此前原始数组直落setObject兜底pg内核系驱动toString发送报malformed array literal;toSqlLogStr两重载同步放宽isArray+convertArray装箱渲染(此前int[]输出"[I@.."乱码,byte[]除外维持二进制语义),fitSqlParams经5参重载自动覆盖;②setArray gauss系分支补入Double[]→float8、Boolean[]→bool、Short[]→int2(SqlUtil绑定+SqlUtilsExt回写双路径)——此前三类型落else setObject/updateObject(x,ARRAY),华为驱动/kingbase绑定报Cannot cast、回写路径四引擎均失败(Can't infer SQL type/malformed array literal),og/vastbase绑定侥幸可用但口径不一;float8/bool/int2四引擎×三类型×绑定/回写矩阵24红→24绿,double[]/boolean[]/short[]原始参数装箱后亦到达该分支;③SqlUtilsExt.resultUpdate数组分支由instanceof Object[]放宽为isArray+convertArray,与绑定路径同口径接纳原始数组(此前char[]落else updateObject,kingbase真库报malformed array literal "[C@..",int[]/double[]靠驱动原生原始数组支持侥幸可用),定型推断改取装箱后组件类型,空/全null无法定型时维持isForcedUpdate null语义;④非pg系回归A/B实证:mysql9旧setObject(x)与新setObject(x,ARRAY)序列化字节完全一致(52字节ACED0005头),oracle/db2/sqlserver数组绑定新旧路径均报错(ORA-17004/-4461/UNKNOWN转换,数组本不可绑定,无回归);空Object[]由序列化bytes统一为SQL NULL属有意语义对齐;SqlUtilsExtArrayResultUpdateTest扩至10例(补三类型小写名/原始数组回写归一/定型空数组不置null/绑定胶水反射锁死),SqlToArrayLogTest扩至19例(补原始数组分派),原六类型矩阵48/48复验无回归,char[]/int[]/double[]回写kingbase/highgo真库全绿

27、数组批次深水区全链路验证与pgjdbc回写缺陷修复(2026-11-21,verify工程ArrayTypeEndToEndTest扩至13用例×18 profile真库矩阵全绿):①新增深水区用例——@loop宏数组摊平(int[]/List拼接真实sql)、saveOrUpdate(merge into)数组列insert/update双分支、saveAll批量数组写入、updateSaveFetch可更新结果集回写数组列(updateArray真驱动链路);②pg系(pgjdbc:postgresql/pg14/highgo)纳入SqlUtilsExt.setArray的createArrayOf分支——真库实证CONCUR_UPDATABLE结果集上rs.updateObject(数组列,java数组[,Types.ARRAY])+updateRow后,该列读回的PgArray.getArray()报ArrayIndexOutOfBounds(驱动将update值以非规范格式写入行缓冲,updateSaveFetch回读组装即崩,最小复现:updateObject路径崩/updateArray路径干净),改走createArrayOf+updateArray后pg18/highgo真库全绿;③h2数组列merge修复:merge into的using(select ? as 数组列)裸?在h2 prepare期急切推断报Unknown data type(独立JDBC同款SQL同败,与geometry列2026-9-8前科同构),FieldMeta新增arrayComponentType(定型数组取组件类/List与Set取泛型实参,EntityManager构建时提取),H2DialectUtils.wrapSelectFields按组件类型映射h2数组元素类型生成cast(? as VARCHAR/INT/BIGINT/NUMERIC/DOUBLE/REAL/BOOLEAN ARRAY)(真库实证cast(? as VARCHAR ARRAY) prepare+执行+写入全通),未识别组件(日期时间等)维持裸?走@Column(nativeType=..)口子;h2的t21由跳过转正向断言13/13全绿;④pg系/og系wrapSelectFields的else兜底分支统一同款三段优先链(nativeType显式声明>按arrayComponentType组件cast>裸?兜底)——PostgreSqlDialectUtils(服务postgresql/kingbase/highgo)与OpenGaussDialectUtils(服务gaussdb/openGauss/mogdb/vastbase/stardb/oscar)的cast元素类型名与SqlUtil.setArray的createArrayOf口径对齐(varchar/integer/bigint/numeric/float/float8/bool/int2,共享pgArrayElementTypeName),pg系裸?本可靠绑定期定型工作、显式cast令using-select列型与绑定产出一致并兜底弱类型绑定路径;⑤DDL生成数组分派:ColumnMeta透传arrayComponentType(buildTableMeta),convertType按组件生成数组列类型——pg内核系9库elem[]标准建表语法、h2的elem ARRAY(元素名同上口径),此前实体数组字段落default统一VARCHAR(pg系仅String[]靠typeName以[]结尾碰巧生成_VARCHAR,数值/布尔数组错型;h2丢数组维度成标量列与运行期绑定不匹配),无数组列方言与未识别组件维持switch现状由@Column(nativeType)显式指定;顺手修既有缺陷:nativeType显式声明"text[]"经default透传后被末尾_前缀逻辑包成非法"_text[]"(建表必败),已是elem[]数组语法不再加_前缀;dm/oceanbase/hana无需处理(dm=具名VARRAY走nativeType口子即正解、ob/hana无标准数组列,真库矩阵实证);新增WrapSelectFieldsArrayCastTest(5例)、DDLArrayConvertTypeTest(4例)、ArrayTypeEndToEndTest扩至14例(t24实体→DDL→真库建表→数组写读回环,6库全绿);
28、nativeType特殊类型族机制(2026-11-21,用户指出的产品级跨库部署问题:quickvo按开发库生成nativeType=源方言物理类型名,产品部署oracle/dm等目标时原样消费产生非法cast/非法DDL):①解析期分类——新增NativeTypeUtils.specialFamily将nativeType别名(含jdbcType辅助)归一逻辑族json/jsonb/bson/vector/geometry/array(pg数组两形态elem[]与内部前缀_varchar),EntityManager解析期填充FieldMeta.specialFamily、buildTableMeta透传ColumnMeta;②消费点方言翻译——五个wrapSelectFields(pg/og/h2/dm/db2)的族cast前置到方法头(quickvo对json列生成String字段+nativeType=jsonb形态,按jdbcType的VARCHAR分支裸?在pg上实测COALESCE(varchar,jsonb)报错,故族翻译须先于jdbcType分支),specialCastType按目标翻译:pg内核9族域内保源形态(jsonb精度)、h2归一JSON/GEOMETRY、数组族非pg域返回null回落组件类型推断链、dm/db2无对应返回null回落裸?,族不可识别(如dm具名VARRAY/db2gse形态)原样透传保持精确控制;③DDL族化——convertType的JSON/BSON词面特判升级为族分派(pg系保json/jsonb源精度,其余jsonCarrierType承载)、jdbcType=JSON/JSONB补case、数组族nativeType非pg目标视同未配置回落组件链,族字段为空时现场兜底识别(幂等,保障手工构造/反向工程getTableColumns链路——DDLConvertTypeMatrixTest回归暴露);④新增NativeTypeSpecialFamilyTest(2例)、ArrayTypeEndToEndTest扩至15例(t25模拟pg源quickvo实体nativeType=text[]/jsonb跨目标部署pg/h2:DDL形态断言+真库建表+merge执行全绿);⑤顺手修复:UpdateSaveFetchEndToEndTest的select 1探测使db2整类静默0tests(改isValid后暴露db2主键须NOT NULL的套件DDL适配缺失,已补)与db2 updateSaveFetch insert分支"已关闭result set"既有缺陷(-4470,标量字段即可复现,显式skip留档待专项);当日orm-core全量2740/0F/0E,verify数组套件14库15用例全绿
29、pg源数组实体降维部署无数组列方言(2026-11-22,用户场景:postgresql的date/字符串/数值数组切换oracle/mysql/hana/sqlserver/db2):①DDL降维承载修复——此前数组族在无数组列方言落default将pg源text[]/integer[]/date[]形态原样输出为非法建表DDL(或裸VARCHAR在mysql/oracle缺长度非法);现jsonCarrierType按目标分派承载(mysql/oracle 21c+原生JSON、oracle11/db2 CLOB、hana NCLOB、sqlserver NVARCHAR(MAX)),JSON承载亦是数组跨库迁移正路,六方言(mysql9/oracle/hana/sqlserver2025/db2/dm)真库建表执行全绿(t26,三形态数组实体);②运行期边界明确——这些方言无数组列类型,数组参数绑定本就不可用(序列化bytes绑定报错,t11已锁无假阳性),应用层以JSON字符串读写承载列为迁移设计范畴;pg系date[]组件(LocalDate)未映射维持default(nativeType=date[]在pg合法),日期数组绑定在pg系亦属驱动限制(SQLFeatureNotSupported,此前已记录);
30、nativeType族机制复查修复批次(2026-11-22,qwen4-max复查2项+glm5.3复核追加1项,真库A/B+矩阵实证):①geometry族前置cast绕过DM/DB2专属函数包装(产品级回归,qwen4-max修复)——原FAMILY_GEOMETRY对非H2目标一律return nativeType,使族前置在DM/DB2输出cast(? as ST_GEOMETRY)绕过jdbcType=GEOMETRY正路的函数包装(真库A/B:DM 8.1.5报-6801数据错误/DB2 12.1.5内置形态报-408类型不兼容,正路DMGEO.ST_GeomFromText/db2GeomFromTextWrap均OK)——修复为DM/DB2回落null交由jdbcType分支函数包装,pg内核系/h2保持原cast,与changelog28描述自洽;②DDL数组族兜底识别不一致(qwen4-max修复)——数组族条件原取colMeta.getSpecialFamily()原始字段而json/bson族用现场兜底局部量,手工构造/反向工程链路(字段空+nativeType=text[])在非pg目标原样输出非法text[],统一用局部量(MYSQL/DM探针红→绿);③空串nativeType拼出cast(? as )空类型(glm5.3复核追加,qwen修复未覆盖)——@Column的nativeType默认值是空串而非null,族内"保源形态return nativeType"路径返回空串,经cast(? as +x+)拼出cast(? as ),jdbcType判族(VECTOR/GEOMETRY/JSON)且未写nativeType的实体在pg系merge即炸(ExtTypesMatrixTest真库实证cast(? as ) as vec_col)——specialCastType对空白nativeType统一返回null(空源形态无"保"可言,回落jdbcType分支正路cast(? as vector)等),五处wrap前置判空同步isNotBlank双保险;④文档修正:changelog29/DDLUtils注释日期2026-11-24超前改2026-11-22、isPgKernel注释"9库"实列10常量、changelog29条④粘连补分号;验证:族相关单测25/25(含geometry DM/DB2回null、空白nativeType回null、数组族现场兜底锁死)、ExtTypesMatrixTest六方言(pg/dm/db2/h2/kingbase等)10/10全绿(geometry/vector/json全链路)
当日终态orm-core全量2741/0F/0E;④当日累计:绑定(Object[]/原始数组路由+定型+createArrayOf类型名)、回写(resultUpdate归一+updateArray)、日志拟合(方言分派+空数组/揭示形态)、深水区(merge/批量/结果集回写)四层全部18库矩阵闭环
31、nativeType特殊族机制复查修复(2026-11-22,对28/29轮红→绿A/B+DM/DB2真库实证):①geometry族前置cast绕过DM/DB2专属函数包装回归修复——specialCastType的FAMILY_GEOMETRY原对非H2目标一律return nativeType,使五个wrapSelectFields族前置在DM/DB2输出cast(? as <nativeType>)绕过jdbcType=GEOMETRY正路的函数包装(DM DMGEO.ST_GeomFromText(?,0)/DB2 db2GeomFromTextWrap),geometry字段带nativeType(quickvo生成或手写)即触发;真库A/B:DM8.1.5 cast(? as ST_GEOMETRY)报-6801数据错误而DMGEO.ST_GeomFromText(?,0)通过读回POINT(1 2)、DB2 12.1.5内置形态cast(? as ST_GEOMETRY)报-408类型不兼容而db2gse.ST_GeomFromText(?,0)通过;修复geometry族DM/DB2回落null交由jdbcType=GEOMETRY函数包装正路,pg/h2保持原cast不变——亦对齐28轮changelog自身"dm/db2无对应返回null"/"db2gse形态原样透传"描述(原代码与该描述自相矛盾);②DDL数组族兜底识别一致性修复——convertType数组族条件原取colMeta.getSpecialFamily()原始字段,而json/bson分支用现场兜底计算的specialFamily局部量,手工构造/反向工程链路ColumnMeta字段为空但nativeType=text[]时非pg目标原样输出非法text[]而非JSON承载(探针MYSQL/DM text[]红→JSON绿,pg仍text[]),改统一用局部量(产品实体路径buildTableMeta同时透传两字段不受影响,属一致性加固);③顺手清理DB2/DM/H2前置插入遗留的else-if制表符格式glitch;④新增WrapSelectFieldsGeometryTest(3例,消费点锁定DM/DB2函数包装与pg/h2 cast不变)、DDLArrayConvertTypeTest扩至6例(补数组族nativeType非pg现场识别回落JSON承载)、NativeTypeSpecialFamilyTest geometry断言更新(DM/DB2 null/pg原样);vector/json/jsonb/bson/array族复查安全(非pg目标均null回落各自jdbcType正路无绕过);当日orm-core全量2745/0F/0E
32、lambda编程式缓存翻译(2026-11-22,补齐lambda只能靠@Translate注解、无法按查询临时指定cacheName/key/column的缺口):①API——LambdaQuery.translates(Translate...)(与EntityQuery/QueryExecutor同参同语义)、字段引用便捷入口translate(cacheName,key,target)与同列translate(cacheName,column);Translate保存时clone,避免调用方复用/修改污染构建器;②通道语义——单表通道透传EntityQuery.translates,由findEntityBase自动追加"keyColumn as column"查询列后翻译;join/SQL通道透传QueryExecutor.translates(link.Query新增translates入口并在build时挂到QueryExecutor,count不携带),仅支持结果列原地翻译(column须为select结果列别名,keyColumn须省略或与column相同),需要key→target映射时由select(JoinCol.of(key).as(target))显式产出;③同列翻译修复——key与target指向同一属性时不再追加重复列,且因属性名与库列名存在驼峰/下划线差异(如staffLevel/STAFF_LEVEL)会把翻译目标改为真实列名,保证翻译按结果列名命中、字段映射再按columnFieldMap回落属性(此前重复列导致映射取原列值、翻译结果失效);仅结果类型为实体本身走该路径,自定义VO结果维持原别名方式;④边界快速失败——findColumns投影/聚合终端(max/min/sum/avg)不支持翻译;union分支、CTE内层、fromFast内层禁止设置翻译(只在最终结果集翻译一次);join通道keyColumn≠column直接抛DataAccessException并提示select别名写法;⑤注解式@Translate不受影响,显式翻译与其共存;新增LambdaCacheTranslateH2Test(13例:单表find/findPage、同列、多列、cacheType、select裁剪、join原地翻译、注解共存及六类误用);lambda全套326/326、含翻译相关346/346回归全绿
33、独立复审修复批次(2026-11-22,第三方复审+H2/PostgreSQL18/kingbase/openGauss/HANA真库探针):①族cast在pg内核系归一(产品级回归)——specialCastType对pg内核系原样返回nativeType,源库物理类型名不是目标库合法拼写时merge using-select解析期即失败(PostgreSQL18实测:type=JSON+nativeType=CLOB的实体saveOrUpdate报 type "clob" does not exist,生成SQL为cast(? as CLOB);ST_GEOMETRY/SDO_GEOMETRY/floatvector同因),现按族归一:json/jsonb以nativeType词面保精度、geometry仅保留geometry/geography拼写否则归一geometry、vector族按目标归vector/floatvector(gaussdb/vastbase为floatvector),非pg且非h2目标统一回落null交jdbcType分支;②updateFetch空定位值改错行——insertIfAbsent=false时空定位值不再构造"col is null"锁查询(H2实测此前改写了业务键为NULL的无关行),按记录不存在直接返回null;③DDL与merge cast优先链统一——convertType数组分支仅在jdbcType为中性(未声明/OTHER/ARRAY/JAVA_OBJECT/STRUCT)时生效:此前List<String>+type=CLOB被静默改成varchar[]/JSON承载、String[]+type=VARCHAR被改成数组列而merge cast仍为cast(? as VARCHAR(n))(H2实测saveOrUpdate不报错但落库cardinality=1,静默错写);④jsonb精度以nativeType词面为准(type=JSON+nativeType=jsonb此前被静默降级为json,反之升级);⑤typmod归族——vector(3)/floatvector(3)/geometry(Point,4326)按去参数后基础名归族(此前vector(3)漏判致无jdbcType上调/无族cast,h2把向量列建成VARCHAR、merge报Unknown data type),numeric(10,2)等常规类型不误判;⑥其余:UUID族尊重显式nativeType(BINARY(16)等不再强制VARCHAR(36))、HANA保留原生TINYINT且不追加TINYINT(n)(真库建表实证)、pg数组元素名float→显式float8(pgjdbc实测"float"即float8,零行为变化)、未识别数组组件告警留痕(LocalDate[]/String[][]不再静默生成标量列却按数组绑定);⑦不做过度处理:Character[]/List<Character>数组列明确不支持(pgjdbc setObject(Character[],ARRAY)报Cannot cast、List<Character>实际以Object[]到达同样报错,读回亦无法还原为Character型集合),故不移入元素映射、不做绑定归一——需要该形态直接用String[]/List<String>即可,属开发者可自纠的声明失误;char[]保持原有数组语义(装箱为String[]绑定,已有用例锁定);⑧lambda翻译:同列翻译在投影未包含该列时补"key as column"并翻译(此前静默no-op)、组合(union/CTE/fromFast)分支翻译补执行期校验(声明之后再设置不再被静默丢弃)、distinct/groupBy与补列式翻译快速失败、getTranslates返回不可变视图、单表count不再携带翻译、link.Query.getValue携带翻译;⑨Dialect的9参updateSaveFetch在insertIfAbsent=true时回到8参重载,方言/第三方对8参方法的覆写继续生效,TDengine按实际方法名报错;验证:orm-core全量2798/0F/0E/73skip(含本批新增7例:typmod归族与pg cast归一、DDL优先链/jsonb精度/UUID-nativeType/HANA-TINYINT、updateFetch空定位值、lambda同列投影/组合后翻译/distinct拒绝/count),verify ArrayTypeEndToEndTest/UuidWriteBackEndToEndTest/UpdateSaveFetchEndToEndTest于h2/postgresql/kingbase/opengauss全绿,跨库探针(pg:type=JSON+nativeType=CLOB实体saveOrUpdate)由红转绿

34、深度复审与真库边界测试批次(2026-11-22,独立复审+9库真库矩阵):①vastbase boolean绑定形态再修正(回归,23cf9968b引入)——原为适配insert/merge的cast改用setString,但未覆盖"按boolean条件查询":where flag=:flag与flag<>:flag由可用变为报invalid input syntax for type numeric "true",且null在无cast的insert/谓词路径setNull(VARCHAR)报column boolean but expression character varying(真库A/B矩阵);改为setObject("true"/"false",Types.OTHER)、null用setNull(OTHER),unknown由服务器按列型推断,insert/update/merge/where/<>/null四类上下文全通,9库(pg/h2/mysql9/kingbase/dm/db2/hana/sqlserver2025/vastbase)谓词矩阵全绿;②hana boolean update补cast——merge侧已加cast(? as BOOLEAN)(d71c02974),但update的coalesce(?,flag)裸?被推断VARCHAR报[266]inconsistent datatype,真库实证cast(? as boolean)后insert/update/merge三路径全通;③sqlserver VARCHAR→NVARCHAR长度上限——varchar(8000)源列(经典上限)生成NVARCHAR(8000)建表报exceeds the maximum allowed(4000),超4000改NVARCHAR(MAX),真库50/4000/8000三档建表验证;④oracle TIMESTAMPTZ/LTZ归一收敛——TIMESTAMPLTZ无无参offsetDateTimeValue,无参stringValue/timestampValue报Conversion failed、toJdbc()返回自身,归一需会话上下文,原"两者归一"的注释与实现不符;代码/注释收敛为仅TIMESTAMPTZ归一,并以单测锁定LTZ边界(可读值经rs.getTimestamp/getString或SQL端to_char);⑤记录边界(不改代码):starrocks BLOB为directAssign直赋,非forceUpdate的部分更新(null blob)会把原值覆盖为NULL(真库复现;coalesce/cast形态均证伪,与ob vector同类能力边界,业务侧需显式传值);doris BLOB以STRING承载时setBytes落库为hex文本(写{4,5}读回ASCII"0405"),二进制不可无损往返,需文档明示;⑥DM空间类型白名单——真库实测(DM8 20260710+DMGEO)DM列类型仅ST_GEOMETRY可建表,DMGEO.ST_GEOMETRY(带schema)/SDO_GEOMETRY/GEOMETRY/GEOGRAPHY/POINT/MDSYS.SDO_GEOMETRY全部报-3719非法基类名,故显式nativeType先判定DM是否支持、不支持的统一转ST_GEOMETRY(受支持拼写唯一且类型名大小写不敏感,直接输出常量);⑦sqlserver VECTOR标注仅2025+支持(2022及更早无VECTOR类型,须@Column(nativeType=..)指定承载或升级),SqlServerDDLGenerator的NVARCHAR超长兜底上限由8000校正为4000;⑧BooleanSqlPathEndToEndTest纳入hana并补"谓词查询"(命名参数/lambda/count/&lt;&gt;)与"null保存"两类用例,同时修正hana不支持drop table if exists导致重复执行init失败的兼容问题;验证:orm-core全量2824/0F/0E/73skip(含新增OracleTzNormalizeTest与DDL/绑定形态断言),verify 10 profile×6套件矩阵(ArrayType/UuidWriteBack/UpdateSaveFetch/ExtTypes/CrossDbDeploy/BooleanSqlPath)全绿,修复后hana/vastbase/postgresql/h2/sqlserver2025/oracle复跑全绿,BooleanSqlPathEndToEndTest五库(hana/vastbase/postgresql/h2/mysql9)各6/6、dm与sqlserver2025(CrossDbDeploy+ExtTypes+SqlUtilDialect)各23/23

35、发版前完整链路排查收敛批次(2026-11-22,16库真库矩阵+clean门禁):①H2 timestamptz DDL丢时区修复——TIMESTAMP_WITH_TIMEZONE的DDL分支无H2落else TIMESTAMP(真库实证插OffsetDateTime读回"2026-01-15 10:30:00"丢"+08"),而merge cast已cast(? as TIMESTAMP WITH TIME ZONE),DDL/cast分歧且静默丢时区;H2补入TSTZ分支输出TIMESTAMP WITH TIME ZONE(读回保"+08"),与merge cast同口径(CrossDbDeploy h2 ts断言原仅查日期掩盖丢时区);②geometry外部拼写DDL归一——pg内核系type=GEOMETRY+nativeType=ST_GEOMETRY/SDO_GEOMETRY等DDL原样透传报type does not exist,而specialCastType已归一geometry,DDL/cast分歧;DDL GEOMETRY nativeType分支对pg内核系镜像cast归一(仅geometry/geography拼写保精度,外部拼写归一geometry),非pg目标精确控制不变;③BSON族cast与DDL同口径——BSON为mongodb类型,关系混用属边缘场景,cast按nativeType词面(bson)与DDL BSON列型一致(撤回此前误改jsonb);④TranslateCacheMatrixTest init DDL跨库兼容——hana无drop table if exists、doris/starrocks须键模型+分布子句且DDL后关连接,改容错drop+按方言建表+每DDL独立连接,16库CrossDbDeploy/TranslateCache全绿;⑤实证澄清两项观察:oracle TIMESTAMPTZ反射Method缓存早已存在(ORACLE_TZ_METHODS按类缓存,非逐行反射);surefire useSystemClassLoader=false实证有害(全量4F+16E,隔离类加载器破坏本项目测试),回退保持默认;⑥Byte[]/List<Byte>数组形态属不存在场景(一般作blob/binary),不做额外绑定处理;验证:clean门禁2827/0F/0E/73skip,16库CrossDbDeploy全绿,12库×9套件综合矩阵全绿

# v6.0.10 2026-11-11
1、函数跨库适配体系大扩充(2026-11-10/11多轮,全部经docker多库真库对拍实证,新增测试超300例):①新转换器五族——substring_index(mysql系原生/oracle系instr含occurrence与负向/db2 locate_in_string/pg系string_to_array切片,边界组合显式case)、unix_timestamp与from_unixtime互逆族、truncate(oracle系trunc反向互转/sqlserver round(x,d,1)/hana ROUND_DOWN)、current_date()带括号形态(db2转CURRENT DATE带空格)、week/weekday/dayofweek族(week按MySQL mode0锚点法精确、weekday/dayofweek按2000纪元锚取模且负数归一,规避sqlserver @@datefirst与oracle NLS依赖);②多源化反向方言交叉——源拼写不只认MySQL形态:datepart(sqlserver习惯)/to_timestamp(单参epoch为PG习惯,双参解析语义在oracle/db2原生透传)/split_part/trunc(数值),配套修复四处静默误转;③split_part全保真重写——负索引(PG14+从末尾数)与正/负超界返回''的MySQL系/oracle系/CH组合(oracle负索引用负position定位规避ORA-01428,CH用splitByString+arrayElement规避substringIndex单字符分隔符限制);④epoch族会话时区一致性——MySQL unix_timestamp/from_unixtime按会话时区,各目标库对齐该口径:sqlserver/db2减加会话偏移(db2 CURRENT TIMEZONE为hhmmss三段换算)、Oracle用from_tz/sys_extract_utc按入参日期应用会话命名时区DST规则(固定偏移对历史夏令时日期会偏一小时)、H2修正其extract(epoch)对无时区类型恒按UTC与PG相反的口径、PG系全族统一::timestamptz、sqlite按列TEXT/INTEGER双形态运行时判别归一、DM实测原生自带两函数且时区感知正确归原生透传(含fmt形态)、HANA以seconds_between/add_seconds组合补偿;⑤DateTrunc/Truncate第二参引号语义按目标方言分流(MySQL系双引号是字符串,日期单位拦截防静默截断时分秒);⑥CH专项——unix_timestamp→toUnixTimestamp/from_unixtime→fromUnixTimestamp(无该函数实测)、lambda侧裸UNION按CH26的union_default_mode渲染UNION DISTINCT;⑦toMySqlFormat的mm语义按"最近directive为%H跨字面分隔符"判定分钟(HH-mm/HH mm/HH时mm分等java时间上下文),其余为Oracle月份
2、DDL生成器真库缺陷修复:①DB2主键列须显式NOT NULL(否则建表SQLCODE=-542);②Doris/StarRocks无TIMESTAMP类型映射DATETIME、OffsetDateTime在Doris映射TIMESTAMPTZ;③两库不支持列内联PRIMARY KEY,无@MppTable时按键模型收口(Doris UNIQUE KEY+hash分布/StarRocks PRIMARY KEY),UNIQUE/AGGREGATE键模型未显式指定分布时按键列hash(默认随机分布实测报错);④键列按键序前置为schema有序前缀(两库要求,实体@Id非首位时自动重排),注解orderBy与实体列名大小写错配时仍正确前置
3、分页与随机取数对集合运算语句的包裹修复:sqlserver的findTopBySql/findPageBySql无order by派生表包裹判定由只认union扩展为union/intersect/except/minus(hasSetOperation);DefaultDialectUtils的top/分页在CH/Impala上limit/offset绑定集合尾部SELECT(实测A except B limit N的N只修饰B),先包派生表再limit
4、缺陷与性能修复批次(多库真库实测):BeanUtil primitive long经Double中转致雪花ID(>2^53)静默失真改精确解析;count改写器不识别minus/except/intersect致分页记录数静默失真;顶层group by+having子查询误判为内层分组;gaussdb系Long[]数组绑定类型不一致;link多列结果集列元数据提出行循环;loadAllMapping级联装配O(n*m)改索引分组O(n+m);六处缓存键由类名改为Class对象身份规避类重载残留;两处方法匹配缓存加容量上限;findByJdbcQuery setter异常关闭已创建statement;byte[]属性String绑定显式UTF-8;clearDisturbSql免重复掩码;BeanUtil.toIntegerText对巨指数double防护
5、缓存类加载语义:cascadeModels/dtoEntityMapperCache等按Class对象的两级缓存改用ClassValue承载——应用类卸载即条目回收,静态Map不再强引用已卸载类的Class对象(经类卸载/8线程并发/真实注解/反向对照四维度深测)
6、sqlite方言:①锁语义统一降级——sqlite单写者下FOR UPDATE类锁请求全部跳过为普通读(锁谓词在sqlite恒为假,乐观锁CAS由UPDATE...WHERE承担,不再抛异常挡住"MySQL逻辑在sqlite上测试"的常规用法);②日期函数的列形态TEXT/INTEGER双形态运行时判别(ORM setTimestamp写毫秒INTEGER须/1000归一,外部工具写入的TEXT按本地墙钟解析,此前对TEXT列数值化致week族/unix_timestamp静默错值)
7、TDengine 3.3.x方言实现(查询/分页/随机取数/count走通用路径,对象插入与超表标签写入经命名参数透传)
8、DB2方言:无FROM常量查询(自定义countSql "select 99"类)补SYSIBM.SYSDUMMY1虚表——字面量内的from不误判(掩码判定)、只认顶层FROM、已带with ur不重复追加;VALUES形式查询本身合法不补虚表
9、pom为发版做准备:依赖版本全部升级至当前最新正式版并收口properties统一管理;orm-core数据库驱动收口test作用域、spring模块清理无用依赖(反馈#IKUCI2)
10、废弃方法体系化收口(行为全部等价,零API移除):①USF锁游标回读迁移至processResultRow新签名——ResultColumnMeta[]随usfTypeNames/usfKinds在行循环外查询级一次构建,原走废弃桥接签名每次回读逐列重建列元数据;自此废弃签名无任何框架内部调用方,仅保留第三方兼容(getMoreLinkResultSet已于前批次迁移);②废弃shim委派收口,消除与现行方法重复的赋值体:TreeTableModel.rootId→pidValue、Update.forceUpdateProps→forceUpdateFields、SpringAppContext.setContext→setApplicationContext(spring与starter双份),三者在全仓均无子类覆写,多态路由不变;③javadoc迁移指引:LightDao的update()链式示例改用forceUpdateFields(原示例引导调用废弃方法)、SqlToyLazyDao.cacheMatchKeys废弃签名补@see;④core经javac deprecation扫描零"uses or overrides a deprecated API"警告(框架内部不再调用任何废弃方法)

11、发版前缺陷检查修复(2026-11-12,6项P1):①Spring Boot fat-jar空根classpath*:重复加载修复——嵌套根已覆盖外层app.jar时java.class.path不再补外层根,同一SQL不再以nested/外层jar两种URL形态各命中一次(breakWhenSqlRepeat默认true下重复sqlId会阻断启动);②单参to_timestamp(列/占位符)在oracle/dm/OB(Oracle模式)原生透传,不再误按PG epoch语义转换(原生文本解析写法被转成numtodsinterval致ORA-01722);DB2保留epoch转换(真库v12.1.5实测单参原生解析对常见格式一律SQLCODE=-20448不可用);③week/weekday/dayofweek族Oracle/DM/OB字面量日期入参包to_date定型,规避默认NLS_DATE_FORMAT下ORA-01861(与DateDiff/Extract口径对齐),DB2字面量cast为date/timestamp定型(裸串days()/dayofweek()报SQLCODE=-182真库实测);④五个新转换器(unix_timestamp/from_unixtime/substring_index含split_part/truncate/week族)的OCEANBASE方言归属统一为Oracle系,与DateDiff/GroupConcat的框架约定一致(dialect=oceanbase即Oracle模式租户,MySQL模式应配dialect=mysql);⑤分页count缓存会话身份:schema/catalog改为每次从当前连接读取(同一连接池setSchema/setCatalog切换多租户时不再钉死首次会话致count串用),账号读取失败不缓存;⑥集合运算判定排除minus(a,b)等函数调用形态(关键字后紧跟括号且内容非select),修复ClickHouse的minus()算术函数被误判为集合运算致分页误包派生表(内层重复列名报Duplicate column name),except(select ...)等集合写法不受影响

12、发版前缺陷检查修复第二批(2026-11-12,真库实证):①并行分页count租户上下文——count的SQL准备(参数求值+租户/权限拦截器)移至调用线程完成,工作线程只做JDBC执行:应用租户上下文通常是普通ThreadLocal(TTL executor也只传播TransmittableThreadLocal),修复前工作线程构建count SQL丢租户过滤,count值失真且登记进含租户key的缓存造成跨租户串用(H2真库A/B实证:修复前两租户count均为全局8,修复后5/3正确);②租户拦截器分页尾缀定位——无where的实体分页SQL租户条件须插在limit/offset rows/fetch first之前,原实现只考虑group/order by,条件concat到limit之后串行/并行分页均报语法错(H2真库复现);③lambda组合共享成员栈式归还——成员同时被外层union与内层CTE共享引用时,内层渲染结束恢复外层已下发的dbType与递归标记(原无条件置null/清除致外层随后渲染回落-1方言失配);④递归标记覆盖递归成员自身的CTE/@fast内层(原只遍历unions,DB2嵌套场景漏标隐式JOIN改写报SQLCODE=-345),身份去重防共享成员重复记录与循环引用

13、发版前缺陷检查修复第三批(2026-11-12):①Doris/StarRocks DDL键列强制NOT NULL——StarRocks PRIMARY模型键列可空时建表被拒(真库实测"primary key column[id] cannot be nullable"),与DB2主键-542修复同类,orderBy排序键列同样强制,非键列保持可空;②SqlXMLConfigParse打开嵌套jar资源流与扫描侧策略对齐——Spring Boot嵌套jar的JarFile由加载器缓存共享,原无条件setUseCaches(false)会让流关闭时连带关闭共享JarFile(后续读取"zip file closed"风险),嵌套URL不再禁用缓存,普通jar行为不变;③CollectionUtil.putBounded超限回收加CAS单飞门闩——原实现每个写入线程各自回收1/4,并发突发时过度淘汰近乎清空缓存(A/B实证:16线程突发旧键存活177/400→修复后精确300/400),现同一时刻仅一个线程回收且按水位循环回收至3/4(固定配额追不上并发回填会致缓存膨胀,持续8线程写入实测收敛),容量为软上限,读取路径无锁不变,5处消费缓存(codeSqlCache/BeanUtil方法与映射计划/convertSqlMap)统一受益;④DialectUtils.SET_OPERATION_PATTERN与SqlUtil口径对齐——count改写守卫与top/random包裹判定的独立模式同步排除minus(a,b)等函数形态(红→绿A/B锁定),P1-6修复完整覆盖分页rows/count改写/top/random三条链路,CH真库findPage/findTop/findRandom×重复列名端到端验证

14、外部评审(deepseek)复核修复批(2026-11-12,红→绿A/B+真库实证):①租户分页尾缀边界——PAGE_TAIL_PATTERN尾边界改零宽前瞻(?=\W|$)且数字[0-9]+,修复SQL以"limit ?/limit 10/limit 10 offset 20"结尾(无尾随空白,findTop实体top SQL形态)时租户条件仍拼在limit之后的缺陷;②单参to_timestamp数值epoch恢复——Oracle/DM/OB仅列/占位符/非数值形态透传原生,数值字面量(含引号数值)epoch意图明确仍按epoch转换(修复前一刀切透传致to_timestamp(1794362400)报ORA-00932);③Doris键列NOT NULL收窄——仅StarRocks PRIMARY模型强制(真库探针实证:SR UNIQUE/AGGREGATE/DUPLICATE可空键均合法,仅PRIMARY报"primary key column[id] cannot be nullable"),Doris各模型键列可空合法保持原语义(真库实证可空键建表+插NULL成功),避免过度修复破坏存量写入;④OB归属统一补全——DateTrunc/FindInSet同步归Oracle系(与第11条五函数口径一致);⑤健壮性——分页会话身份元数据读取catch Throwable(老驱动getSchema抛AbstractMethodError)、lambda同层重复成员(ctes与unions同实例)下发身份去重防恢复残留、Boot根去重仅classes内层根才跳过外层根(仅lib嵌套根时不漏classes资源)

15、配套的quickvo-maven-plugin升级至2.0.6(项目地址:https://gitee.com/sagacity/maven-quickvo-plugin,完整功能与配置说明参见其README)

【v6.0.10升级说明】
①to_timestamp单参口径:Oracle/DM/OB(Oracle模式)的to_timestamp(列/占位符)按原生文本解析(与6.0.9一致),数值字面量(含引号数值)保留epoch转换;epoch列/占位符场景请改用from_unixtime(如from_unixtime(:epoch)),否则Oracle报ORA-00932/DM报-6118。DB2保留epoch转换(真库v12实测单参原生解析不可用)。MySQL/PG系行为不变。
②BeanUtil数值转换口径收窄:整型文本转换不再经Double中转——越界值(如"99999999999"转int原静默饱和为2147483647)、NaN(原静默为0)、"10d"类后缀(原静默解析)现显式抛NumberFormatException;雪花ID等>2^53大整数保精度(原Double中转静默失真);正常小数/科学计数仍向零截断、首尾空白容忍。存量数据含越界/NaN值时查询将由"静默错值"变"报错",升级前请清洗或调整字段类型。
③RSA字段解密失败抛异常(6.0.6引入,非本次新变更,提示从更老版本升级的用户):库中未加密历史明文/换钥数据会使查询链路硬失败(原静默返回""与空值不可区分),升级前须完成新密钥重加密迁移或清洗历史数据;加密失败同样抛异常阻断写入(原返回""落库致原文永久丢失)。

# v6.0.6 2026-10-13
1、新增虚表宏@virtualTable()(兼容别名@dual()/@dummyTable(),三名共用同一展开逻辑且均忽略大小写):"select 常量行 union 真实查询"类sql(如字典缓存兜底行)在oracle/db2/hana必须挂伪表,而pg/mysql/sqlserver等支持无from的select,此前须在手写from dual(pg/sqlserver语法错)与删除dual(oracle报ORA-00923)之间二选一;现统一写法select '0' id,'全局' name @virtualTable() union select id,name from t,解析期按方言展开(SqlConfigParseUtils.parseSqlToyConfig在函数/保留字转换前处理,随SqlToyConfig固化):伪表映射穷举DataSourceUtils.DBType全部常量并与各Dialect生成merge所用伪表同源(OracleDialect/DB2Dialect/HanaDialect的VIRTUAL_TABLE)——oracle系(oracle/oracle11/dm/oceanbase)→" from dual "、db2→" from sysibm.sysdummy1 "、hana→" from dummy ";其余全部剔除(mysql/mysql57/tidb/doris/starrocks、postgresql/postgresql14/gaussdb/mogdb/openGauss/stardb/vastbase/oscar/kingbase/highgo、sqlserver、sqlite、h2、clickhouse、impala、tdengine原生支持无from select;mongo/es非sql方言、undefine未识别);宏大小写与括号内空白容错,无宏sql零变化;realDialectFirst运行期惰性层不感知宏(仅做函数/保留字形态转换),跨方言多数据源以解析方言展开结果执行;新增VirtualTableMacroTest锁定伪表三组/剔除方言组/别名与大小写等价性/无宏零影响,并以allDbTypesCovered反射穷举DBType全部int常量强制覆盖完整性(新增数据库类型未登记虚表决策时显式失败)
2、配套的quickvo-maven-plugin升级至2.0.3(项目地址:https://gitee.com/sagacity/maven-quickvo-plugin,完整功能与配置说明参见其README)


# v6.0.7 2026-10-15
1、JSON处理抽象为可插拔的JsonProvider(org.sagacity.sqltoy.plugins.json),框架不再硬绑fastjson2:新增JsonProvider接口、fastjson2/jackson3(tools.jackson)/jackson2(com.fasterxml)三个内置实现,以及provider中立的JsonObject/JsonArray视图(分别继承AbstractMap/AbstractList,使任何JSON库都按容器而非bean序列化,避免写出empty等getter派生字段);解析优先级为spring.sqltoy.jsonProvider显式配置>classpath探测fastjson2→jackson3→jackson2(fastjson2居首以保证provider抽象出现之前就跑通JSON列的存量项目行为完全不变),配置取值支持简写别名fastjson2/fastjson/jackson2/jackson-2/jackson3/jackson-3(大小写、连字符、首尾空格不敏感)或自定义实现的全限定类名,取值无法识别时报错列出可用别名;探测到的候选须先通过冒烟解析(对象解析/嵌套取值/类型化数组/序列化)才被采纳——库在classpath上并不等于可用,例如jackson3的databind需要jackson-annotations 2.20+(JsonFormat.Shape.POJO自2.20起),与jackson2共存时jackson2传递带入的旧annotations会因声明在前而在冲突仲裁中胜出并使jackson3抛NoSuchFieldError:POJO——不可用时记WARN并降级到下一个候选,marker类不存在时静默跳过(只装一个JSON库是常态,不刷WARN);候选实现类一律在try内按类名字符串反射加载,不用Xxx.class或Xxx::new静态引用(构造器方法引用在传参时即触发实现类初始化,其静态MAPPER字段引用对应JSON库,在没装该库的项目里会抛NoClassDefFoundError并导致SqlToyContext创建失败);SqlToyContext.initialize首行调用initAtStartup尝试初始化,显式配置非法或不可用时直接失败并保留根因(不静默回落,否则配置形同虚设),仅缺可用JSON库时WARN不阻断启动(不用JSON列与ES特性的项目无需引入任何JSON库),触及JSON功能才抛出带明确指引的IllegalStateException替代原先的NoClassDefFoundError;JSONTypeUtil(json/jsonb列映射)、TranslateFactory(rest/service路径)与ES全栈(HttpClientUtils/ElasticSearchUtils/ElasticSearchPlugin/ElasticSqlPlugin/JwtTokenManager)全部改为委托provider,core内对具体JSON库的引用仅剩三个实现类;ES响应的hits.total同时兼容{value,relation}对象(7.x起)与纯数字(6.x及更早)两种形态,且容器判断统一用instanceof java.util.Map/List而非具体包装类型;starter新增spring.sqltoy.jsonProvider属性透传;core pom新增jackson2与jackson3依赖(均provided+optional,不传递),并以test作用域将jackson-annotations抬到2.21,使三个库能在同一测试classpath共存;新增JsonProviderMatrixTest(53例)覆盖解析优先级、别名、配置优先于探测、非法配置快速失败、不可用候选降级、实现类初始化失败降级、包装层契约、外部JSON库序列化护栏、跨provider语义等价(含小数在fastjson2为BigDecimal而在jackson为浮点数的已知差异)、ES载荷形状与JSONTypeUtil端到端(Map/List/Set/Collection/POJO/数组/byte[]/带引号标量/clickhouse的Map形态)
2、新增@joinFields宏:把运行期传入的字段名列表拼成SQL标识符串,面向页面配置化的报表与接口服务(由配置决定select/group by/order by的列),写法@joinFields(:fields,default)、@joinFields(?,default)或字面量@joinFields({a,b,c});与@value处于同一处理位置(命名参数转?之后按位置索引取值),故命名参数与位置参数两种写法等价,被消费的参数从参数集合中剔除、不影响其余?的对位;fields为null/空/全空白时输出default(取自sql文本、可信、原样输出不做标识符校验,可写t.*或sum(t.amount),含逗号或括号时用{}、''或""包裹),未提供default则输出空串;入参归一:单个字符串或长度为1的数组按逗号切割(页面多选常序列化成["a,b,c"]),长度大于1时元素即字段不做二次切割,故["a,b","c"]会因"a,b"非法而报错而非被静默拆成三个字段,空白段跳过(容忍尾逗号);安全:字段名走字符白名单(字母含中文、数字、下划线,最多三段点号限定schema.table.column),显式排除$与#(与自身的#[]动态段和${}/#{ }参数占位冲突)以及空白、引号、括号、分号、逗号、注释符、反斜杠,非法值直接抛异常并带上具体值、不做静默清洗;合法字段经ReservedWordsUtil.convertWord按方言仅对保留字加引用符(mysql系反引号、oracle/pg系双引号、sqlserver方括号),非保留字保持裸标识符以规避oracle/pg上引号引入的大小写敏感;点号限定名逐段处理(convertWord只按整体匹配保留字集合,t.order整体不在集合里会漏引用导致mysql语法错误);只受理纯标识符,sum(col)/col as alias等表达式仍用@value;MacroUtils开放splitMacroArgs与unwrapMacroArg以复用既有的配对符号切分与包裹剥离约定(default含括号时正则截断会出错,故用getSymMarkIndex定位配对右括号);新增JoinFieldsMacroTest(41例)覆盖归一规则、default回退、白名单校验与注入用例、保留字按方言引用、命名/位置两种写法的参数对齐、多宏共存与参数个数不足的明确报错

3、#[...] 剔除后自动清理括号组内悬挂的 and/or 连接符:如 #[and ( #[t.a=:a] #[or t.b=:b] )] 在 a 为 null 时,既往展开为 and ( or t.b=:b ) 的残缺 SQL;现剔除完成后统一清理括号内 trim 空白后以 and/or 开头、或以 and/or 结尾的悬挂连接符(and|or 之后的 \s+ 或 \) 使 ( order by ...) 这类以 or 开头的关键字不受影响),使括号组各成员可独立可选、任一参数为 null 时其余成员正常保留
# v6.0.8 2026-10-18
1、融入sqltoy-plus并深度重构为原生lambda条件体系(org.sagacity.sqltoy.lambda):基于实体属性方法引用(如User::getStatus)组织查询/更新/删除条件,LightDao新增default工厂与执行方法——lambdaQuery/lambdaUpdate/lambdaDelete(Class)返回绑定当前dao的条件构建器(链式find/getOne/findPage/count/exists/delete/update终端),find/getOne/findPage/count/exists/update/delete增加接收LambdaQuery/LambdaUpdate/LambdaDelete的default重载(先构建后执行,构建器用LambdaQuery.create(entityClass)静态创建),全部default实现内部把条件转换为EntityQuery/EntityUpdate复用既有执行链路(findEntity/findPageEntity/getCount/deleteByQuery/updateByQuery),DefaultLightDaoImpl及spring/solon各实现类零改动即获得能力、非spring场景同样可用;条件引擎移植sqltoy-plus的segments合并机制(相邻条件自动AND、and/or/nested/not嵌套括号、相邻连接符合并保留最后者、首尾悬挂连接符剔除、仅分组排序时以1=1补齐where、多字段元组inb、IfPresent语法糖),列名解析统一收口SqlToyContext.getEntityMeta(EntityManager已有缓存,不再需要sqltoy-plus为跨jar访问而设的6个映射策略类/静态EntityMeta缓存/ChainDao/两套并行DAO,@EnableSqlToyPlus整套胶水作废);条件参数名由sqltoy-plus的"type.valuePairs.MPG1"改为全wrapper唯一且跨嵌套共享序号的:p1..:pN(SQL日志可读,having/inSql的外部参数请避开p纯数字形式);lambda解析保留三级路径(IDEA调试代理/writeReplace反射/序列化镜像SerializedLambda)并增加ConcurrentHashMap缓存(非捕获方法引用jvm内单例,重复构建同类查询直接命中),仅支持getter方法引用(表达式lambda编译为synthetic方法无法提取,快速失败报PropertyNamer异常);值过滤策略由sqltoy-plus的全局静态FiledValueFilterStrategyHolder改为wrapper实例级valueFilter(默认滤null,须在添加条件之前设置);安全边界:按条件update/delete无任何where条件时默认抛DataAccessException、显式matchAll()放行(sqltoy-plus的"1=:DEFAULT_VALUE AND ..."参数计数兜底保留用于where为字面量/无命名参数场景);修复sqltoy-plus update(entity,wrapper)按列名反查属性名(遍历columnFieldMap以key取值)在驼峰属性转下划线列名场景set字段全部丢失的缺陷——该能力不再以lambda形态提供,整对象更新走lightDao.update(entity)既有链路;单表in(Object[])统一归一为List便于数据库侧按集合展开,空集合/空数组不参与sql组装(与接口文档既有承诺对齐,sqltoy-plus会生成IN(:p)空值参数);LambdaUpdate新增setExpr(字段,表达式[,value])支持基于字段当前值的表达式更新(如setExpr(User::getLoginCount,"loginCount + 1")、setExpr(User::getSalary,"salary * ?",倍率)),直接复用updateByQuery既有的"字段=表达式"set形态——表达式中的字段以属性名书写并自动转数据库字段、可引用同表其他字段、单个?或单个:name绑定value、纯字面量表达式value传null、表达式内不支持=字符(由框架组装),实体并入更新时同字段的表达式优先避免产生重复set片段;getOne严格语义(单表与join一致):无匹配返回null,匹配多条视为查询条件存在缺陷直接抛DataAccessException(异常含匹配条数),不再静默取第一条——取第一条会把条件缺陷掩盖成静默错值;terminal命名对齐link家族;字符串条件不在lambda层提供(EntityQuery/EntityUpdate本就是字符串条件的原生入口);测试:LambdaExtractTest(lambda提取/缓存/属性名规则)、LambdaQuerySqlTest(恒等解析28例锁定SQL组装与setExpr校验)、LambdaExecuteH2Test(H2+DefaultLightDaoImpl非spring端到端14例:属性→列名映射/分页/count/exists/更新/实体并入更新/matchAll守卫/无命名参数兜底/删除/setExpr自增与比例计算真实执行/getOne多条抛异常与唯一匹配正常)、LambdaJoinH2Test(join 12例:getOne严格化同款覆盖)
2、多表join的lambda查询并入LambdaQuery统一入口(单表只是join的退化形态,是否join由是否调用join步骤自动判定,无需预先选择构建器类型,设计对齐jOOQ的单链式与MP生态联表方案MPJLambdaWrapper的单wrapper模型):lightDao.lambdaQuery(entityClass)上leftJoin/rightJoin/innerJoin后紧跟on(成对关联字段,支持同join两对条件自动AND)即切换join通道,from(entityClass)可省略(查询场景缺省以结果类型作为主表)、select可省略(缺省:结果类型参与join时查其全部字段并按"列 AS 属性名"输出便于结果映射,否则*)、selectAll()显式声明主表全部字段并可与select(JoinCol...)额外列并存(主表字段在前,额外列在后;结果类型与主表不同时须显式from),selectAll(ExcludeCols.of(User::getStatus,...))支持排除指定字段(排除字段须属于主表实体,装配期校验;排除后无字段快速失败)、JoinCol.of/avg/count/sum/min/max/distinct().as()定义聚合与别名列(select(JoinCol...)存在时也判定走join通道);单表通道把条件转EntityQuery复用findEntity/findPageEntity/getCount实体链路(分片/缓存翻译/加密脱敏/租户过滤免费继承),join通道生成"SELECT 别名.字段 AS 属性 FROM 主表 别名 LEFT JOIN 表 别名 ON 别名.a字段=别名.b字段 WHERE ..."完整sql走find(sql,map,resultType)/findPage/getCount(string,map)既有通道——在单表查询上追加join会切换执行通道,实体级增强不再生效(join查询本就无法套用实体增强,属问题固有而非API引入);join更新/删除同样并入lambdaUpdate()/lambdaDelete()(与查询同款"不调join即单表"自动判定,主表隐式为查询实体无需from;join更新生成MySQL方言"UPDATE 主表 别名 JOIN ... SET 别名.字段=:p WHERE ...",join删除生成"DELETE 别名[,...] FROM 主表 别名 JOIN ... WHERE ...",deleteTables指定删除目标缺省主表,set/setExpr在join通道分别物化为别名限定参数赋值与表达式赋值,表达式原样输出单个?绑定value,update(entity)实体并入不支持join通道)经executeSql执行(H2等不支持多表update/delete语法,跨库支持属sqltoy-verify多库门禁范围);别名按实体类名首字母小写+序号生成(j1/j2...);快速失败:单表条件引用非查询实体、join条件/select/set/deleteTables字段所属实体未参与join、join后缺on、join更新缺from或无条件或无set字段均抛DataAccessException;统一条件引擎:Compare/Aggregate条件接口改为按方法泛型独立推断lambda所属实体(原单表固定R版本与JoinCompare/JoinAggregate两套接口合并为一套),AbstractWhere成为单表与join共用的唯一条件基座(segments合并引擎、参数取号、值过滤、嵌套、延迟组装全共享),join模式下set与where参数由JoinContext统一取号保证全局唯一(修复合并过程中曾出现的set/where同号:p1错绑隐患);代价:字段引用错实体从编译期报错变为装配期报错(信息明确:实体[X]未参与当前查询),与jOOQ的松散度一致;测试LambdaQueryJoinH2Test(12例:缺省select/select别名映射VO/count/exists/分页/嵌套条件/sql文本锁定含多on自动AND、join更新与删除MySQL方言文本、完整性校验、无条件防护、join getOne严格化)
3、sqltoy-plus存量用户迁移对照:import com.sagframe.sagacity.sqltoy.plus.* → org.sagacity.sqltoy.lambda.*;Wrappers.lambdaQuery(entityClass)→LambdaQuery.create(entityClass)或lightDao.lambdaQuery(entityClass)(前者需lightDao.find(query)执行,后者链式终端);LambdaQueryWrapper/LambdaUpdateWrapper→LambdaQuery/LambdaUpdate;SqlToyPlusDao.findList/findOne/findPage/delete/count/exists/update(wrapper)→LightDao同名default方法(find/getOne/findPage/delete/count/exists/update);lambdaQueryChain/lambdaUpdateChain→lightDao.lambdaQuery/lambdaUpdate(绑定dao后直接终端);SqlToyHelperDao(废弃接口)整体删除;多表:Wrappers.lambdaMultiWrapper(toClass).select(...).from(...)→lightDao.lambdaQuery(toClass)[.select(JoinCol...)].leftJoin(...).on(...)(from省略时主表=结果类型,join查询与单表共用LambdaQuery);LambdaColumn→JoinCol;MultiWrapper的String变体(字符串列名+别名)不再提供(用QueryExecutor组织原生sql或EntityQuery);FiledValueFilterStrategy全局静态→wrapper实例.valueFilter(...);SFunction→LambdaFn;FiledNature→record FieldNature(entityClass,fieldName);getParamName的".valuePairs.MPG"前缀参数名→:pN(断言sql文本的测试需同步)
4、GraalVM native/AOT支持重写并经真实native构建端到端验证(spring-starter与solon-plugin对称):修复5.6以来AOT实体反射hints恒为空的缺陷——Spring的refreshForAotProcessing不注册BeanPostProcessor、不预实例化单例,贡献阶段getBean得到的SqlToyContextProperties是未绑定空实例、SqlToyContext以空配置被初始化,getAllEntities()快照必为空;SqltoyBeanFactoryInitializationAotProcessor改为经Binder从Environment(prepareBeanFactory阶段已注册为单例,AOT期可靠)直接绑定spring.sqltoy配置,按packagesToScan/annotatedClasses用EntityScanner扫描实体(与EntityManager.initialize启动扫描语义一致,native场景必须配置packagesToScan),按配置动态注册classpath资源pattern(sqlResourcesDir多目录中英文逗号分号分隔/sqlResources显式文件/translateConfig按.xml文件与目录/**分治/securePublicKey/securePrivateKey,文件系统路径native运行期可直读故跳过),为15类按类名配置的扩展点注册反射(unifyFieldsHandler/typeHandler/sqlInterceptors/sqlFormater含default别名展开/translateCacheManager/dataSourceSelector/connectionFactory/fieldsSecureProvider/desensitizeProvider/customFilterHandler/overTimeSqlHandler/dialectDDLGenerator/firstBizCodeTrace/dynamicCacheFetch/dynamicFecthCacheManager,约定同运行期:含"."视为类名Class.forName注册,否则为bean名称由Spring AOT覆盖;其中sqlFormater对应内置实现DefaultSqlFormater硬依赖druid(core中optional),配置sqlFormater的应用需自行引入druid,否则debug统计路径SqlExecuteStat触发格式化时NoClassDefFoundError(solon-test默认debug模式实测,debug关闭不触发)),functionConverts经新公开的FunctionUtils.resolveFunctionClassNames解析后逐类注册(default/defaults展开为内置函数集、trim/nvl等简写展开、org.sagacity.sqltoy旧包路径修正、小写类名去重让自定义同名函数生效,与运行期实例化语义逐字一致——bean创建期FunctionUtils对解析出的每个函数类Class.forName实例化,未注册时配置了functionConverts的应用native启动即失败),条件注册运行期反射实例化的集成实现类,注册跟随classpath而非构建期配置——cacheType与jsonProvider均支持运行期参数切换而native镜像构建期冻结,按构建期配置注册会让"运行期切到另一实现"因未注册而Class.forName失败:caffeine库在classpath→TranslateCaffeineManager(ehcache管理器为运行期直接new静态引用恒可达无需注册),classpath有mongo驱动→SpringMongoQuery,有spring-data-redis→SpringRedisIdGenerator(依赖缺失时不注册避免native构建类型不可解析),按classpath条件注册扩展类型(json/jsonb/vector/geometry)经反射访问的驱动专属类——PG系PGobject同构类5个(org.postgresql.util.PGobject覆盖postgresql及stardb等PG系兜底、org.opengauss.util.PGobject覆盖opengauss/mogdb、cn.com.vastbase.util.PGobject、com.huawei.gaussdb.jdbc.util.PGobject、com.kingbase8.util.KBobject覆盖kingbase;写路径resolvePGobjectHolder按URL scheme反射构造+读路径extObjectPgType反射getType,PGobject不能跨驱动setObject故每驱动一个同构类,未注册时native运行期报MissingReflectionRegistrationError)、mssql-jdbc的com.microsoft.sqlserver.jdbc.Geometry与13.x的microsoft.sql.Vector(含$VectorDimensionType)、oracle.jdbc.OracleTypes;排查确认无需注册:oracle.sql.TIMESTAMP(BeanUtil直接强转调用timestampValue/dateValue)、oracle/mssql的vector与json对象(按类名识别后走toString虚调用)、JTS(静态引用可达)、postgis-jdbc的PGgeometry(JtsGeometryCodec全程instanceof/toString无反射);JsonProvider注册同样跟随classpath:三个内置实现凡对应JSON库marker在classpath即全部注册(marker与JsonProviders.CANDIDATES一致:fastjson2/jackson3/jackson2),显式配置的自定义实现类按类名注册——只注册"构建期生效者"会让运行期jsonProvider切换与探测降级目标未注册,注册使实现类及其引用的JSON库进入镜像,native运行期Class.forName探测/实例化才可用(sqltoy-aot-verify以fastjson2+jackson2共存classpath实测:JVM下探测取fastjson2矩阵通过;native下fastjson2自身元数据不完整——自带构建期初始化清单漏嵌套类($CacheItem)需应用补--initialize-at-build-time=com.alibaba.fastjson2方可通过构建,且其JDKUtils的Unsafe字段偏移构建期重算失败致运行期探测按设计静默跳过、降级jackson2矩阵通过,--initialize-at-run-time整包覆盖实测不可行(jar内类级清单优先);运行期参数显式切jackson2(configure路径)通过——多实现注册与探测降级机制均按设计工作,native下JSON列建议jackson2/jackson3);SqltoyRuntimeHintsRegistrar补注册sql/翻译配置XML解析经XMLUtil.setAttributes反射赋值的8个模型类(UnpivotModel/ColsChainRelativeModel/RowsChainRelativeModel/ReverseModel/TreeSortModel/DefaultConfig/TranslateConfigModel/CheckerConfigModel)——native下缺失时<unpivot>/<tree-sort>/环比等标签属性静默丢失并解析NPE;并注册java.lang.invoke.SerializedLambda与镜像类org.sagacity.sqltoy.lambda.toolkit.SerializedLambda的java序列化——lambda条件体系经writeReplace/序列化镜像提取属性元数据,native下还需应用按"lambda定义类"注册lambdaCapturingTypes(spring: META-INF/native-image的serialization-config.json或registerLambda(定义类,b->b.withInterfaces(LambdaFn.class));solon: registerLambdaSerialization(定义类)),否则lambdaQuery/lambdaUpdate全部不可用(经sqltoy-aot-verify双框架native实测该配方全通过);sqlToyLazyDao/sqlToyDao/lightDao/sqlToyCRUDService四个@Bean返回类型由接口改为具体实现类并显式@ConditionalOnMissingBean(接口.class)(退让语义逐字不变)——Spring AOT按声明类型预判bean类型,接口声明使事务CGLIB代理不在构建期预生成,native运行期报"CGLIB runtime enhancement not supported on native image";solon侧SqltoyRuntimeNativeRegistrar对称补齐动态资源pattern(solon为正则形式,目录补/.*)、扩展类/集成类(SolonTranslateCacheManager/SolonMongoQuery/SolonRedisIdGenerator按cacheType与classpath条件)及JsonProvider注册;solon SqlToyContextProperties的String[]setter(packagesToScan/annotatedClasses/sqlResources/sqlInterceptors)剥离元素首尾成对引号——solon把yml标量绑定到String[]时元素带引号,带引号包名致实体扫描静默为空(native下实体hints全丢,实测);测试:SqltoyAotHintsTest(3例,模拟AOT构建期beanFactory形态——仅注册environment单例,断言实体扫描/动态资源/扩展类/别名展开/caffeine集成类/JsonProvider注册与不存在类warn跳过、无sqltoy bean时不中断构建)、SqltoyRuntimeHintsRegistrarTest(1例:固定pattern/模型类/SerializedLambda与镜像类序列化注册)、SqltoyRuntimeNativeRegistrarTest(1例,solon pom解除surefire skip,test目录helloworld演示类不匹配*Test约定不受影响);经sqltoy-showcase(Spring Boot 4.1.1/Java 17目标)以GraalVM 25真实native构建+冒烟验证(启动0.05s,实体加载/SQL计数/翻译缓存全通过):GraalVM版本要求25+(17/21/23实测不兼容——17与23不识别新reachability-metadata schema被native-maven-plugin直接拒绝,21不支持typeReached条件元数据(条件视为恒真,spring-aop的AspectJ序列化条目被误注册,无aspectjweaver时构建失败);25.0.4双框架全矩阵验证);新增独立验证工程sqltoy-aot-verify(spring+solon双栈共用验证矩阵,PostgreSQL pgvector+postgis,矩阵含CRUD×json/vector/geometry三形态(String/float[]/JTS)、functionConverts方言转换(PG无原生nvl的查询实证)、ehcache+caffeine双实现缓存翻译、updateSaveFetch代理路径、lambda单表/join/setExpr/matchAll守卫/删除):JVM全矩阵双框架17/17,native冒烟双框架16 PASS+1 KNOWN-LIMITATION——updateSaveFetch的EntityUpdateCallback路径byte-buddy运行期生成实体子类在native报"Could not create type"(结构性限制),其insert分支(行不存在)不经代理native可用;配套sqltoy-docs新增GraalVM Native Image指引(中英,含应用侧职责边界:应用代码自加载资源、SQL按名引用的类、resultType DTO、lambda定义类需应用注册,接口返回类型+initMethod的@Bean应改具体类型声明)

# v6.0.9 2026-10-22
1、CTE物化hint按目标库类型保留/剔除:WITH xx AS [NOT] MATERIALIZED (..)仅PostgreSQL系(postgresql/postgresql14及同内核openGauss/金仓/Vastbase/GAUSSDB/瀚高)原生支持,其余库(mysql/tidb/sqlserver/oracle/db2/sqlite/h2等)语法不识别——此前含物化hint的sql跨库执行在不支持库直接报语法错,只能按方言sqlId各写一份;现SqlWithAnalysis新增(sql,dbType)构造,parse()对各CTE的ext区域(别名as与'('之间,withPattern仅允许其承载[not] materialized,是hint唯一合法位置)做位置级剔除——无正则启发式,派生表别名与字面量文本(findBodyEnd括号配对已规避)绝不误伤,不含materialized关键字走零开销快速通道;公开入口SqlWithAnalysis.adaptCteMaterialized(sql,dbType)(支持库或无with原样返回);SqlToyConfig.getDialectSql收口接入:方言匹配时跳过函数/保留字转换(原零变化行为)但仍适配物化hint且直接返回不入缓存(规避读到setSql后陈旧缓存),方言不一致路径在函数转换后同样适配;剔除只失物化优化、不改查询语义(不支持库本无物化概念,等价于用户未写hint);真库因果链验证:PG18/openGauss5/金仓V9/瀚高/Vastbase(GAUSSDB同内核收录)保留hint渲染执行通过,mysql9.3/tidb8.5/mssql2025实测报语法错反证剔除必要性,8库真库零跳过+74单元测试全绿;lambda侧with(name,q,materialized)渲染门禁与SqlWithAnalysis.supportsCteMaterialized白名单单一事实源对齐
2、函数适配真库实证修正批次(2026-10-19/20多库实测):①to_char/to_date/str_to_date的裸HH token全库统一为24小时制口径(小写hh为12小时、HH24恒为24小时)——DB2 12.1.5与H2 2.4真库实测to_char的裸HH均按12小时输出致静默错值,mysql分支HH→%H同步修正;②to_date目标库时间截断防护:pg系to_date只返回date(格式含时间token时时间部分被静默截断,真库实证),目标pg系改写to_timestamp,HANA同理、ClickHouse改toDateTime、H2按java HH语义归一;③trim(from x)省略剔除字符形态:mysql9.3真库实测报1064语法错、oracle 23ai报ORA-30000(均要求省略剔除字符时不能保留FROM),统一改写为语义等价的单参trim/ltrim/rtrim(H2/SQLServer/SQLite/StarRocks原有专门分支不变);④substr起点0按"从开头截取"统一(框架接管的SQL方言substr首位均为1);sqlite原生无mid别名(真库实测no such function: mid),mid形态在sqlite目标下转substr;⑤mysql的left/right(str,0)与负数长度契约对齐(真库实测返回空串);⑥instr四参形态instr(str,sub,pos,occurrence)适配支持;⑦find_in_set比较符扩展为任意位置口径——=1/=2/>1/<3等按整数位置表达式改写,>0/>=1/!=0/<>0/=0布尔口径族不变;首参字面量含逗号按未命中处理(mysql文档明确含逗号属未定义,实测返回0,规避"补逗号+子串定位"把',a,b,'整段误判成元素);⑧datediff的oceanbase并入oracle分支(sqltoy的OceanBaseDialect按oracle系生成merge into/listagg等,OB oracle模式租户无datediff;mysql模式租户按约定配dialect=mysql走mysql分支,本机OB CE mysql模式实测datediff(day,...)执行通过)
3、分页count缓存按方言隔离:pageOptimize的总数缓存键由sqlId升级为sqlId@dialect——同一sql经redoDataSources或多数据源在不同方言库上执行时,各方言的count结果分别缓存不再互相串用(不同库的count优化策略不同,串用会得到错误总数);remove(sqlId)连带清除该sql全部方言缓存;配套修复CrossDbAdapter自定义countSql改写前先clone sqlToyConfig——原直接改写sqlCache中的共享SqlToyConfig实例,会污染同sqlId的后续查询;适配失败时DataAccessException保留原始cause便于排障
4、SqlScriptLoader线程安全加固:initialized与SQLRESOURCESDIRTOLIST标志volatile化、initialize/destroy方法synchronized,规避多线程并发触发初始化时半初始化状态的可见性问题
5、lambda条件体系细化:①like精确族新增likeExact/likeExactLeft/likeExactRight——值中的%先转义为字面量再按方向补%(用户输入含%时按普通字符匹配而非被当通配符放大命中范围),like/likeLeft/likeRight保持纯方向补%语义不变;②setExpr表达式含引号内字面量'?'时快速失败(更新通道对原文做全量?文本替换,引号内?会被误当参数位破坏字面量);③空groupBy列与空having条件跳过组装(原会生成"GROUP BY "/"HAVING "非法sql片段)

# v6.0.4 2026-10-10
1、缓存翻译rest模式认证增强,rest-translate与rest-checker新增auth-type认证模式:basic(默认,兼容既有username/password)与jwt;jwt获取token三种方式三选一(同配按优先级生效):token-url登录接口表单提交username/password获取token(响应json按token-path提取,支持data.token点路径)并缓存、token-secure-key本地自签名jwt(claims含sub/iat/exp,无需登录接口,支持HS256/HS384/HS512共享密钥与RS256/RS384/RS512的PKCS#8私钥)、authorization直接配置静态长效token;配套token-header(默认Authorization)、token-prefix(默认Bearer,设为none发裸token)、token-expire(默认1800秒,响应含expires_in时以响应为准);token缓存复用、临近过期自动刷新、请求遇401自动失效重登(或重新签名)并重试一次
2、函数适配完善:新增源函数/源形态识别--nvl2(a,b,c)(非oracle系转case when判空逻辑)、isnull(x)单参判空(mysql返回0/1语义,此前错转为≥2参的nvl/coalesce/ifnull报参数个数错)、sys_timestamp(oracle系原生透传,mysql系保留fsp精度,sqlserver用sysdatetime)、strpos(str,sub)(pg系/clickhouse原生透传,其余库转instr)、substr(s,-n)负起点(两参字面量转RIGHT,三参走case守卫,表达式起点原样保留交目标库判定,hana负起点pg语义静默错值同样收敛)、curdate()/CURRENT_DATE新增函数类(oracle系转TRUNC(CURRENT_DATE)保留会话时区,db2用CURRENT DATE关键字体,sqlite取本地时钟);既有转换修正与增强--group_concat/listagg/string_agg支持within group (order by …)尾部子句、参数内嵌order by、DISTINCT前缀识别(clickhouse groupArray不支持时显式响亮保留),分隔符字面量内"order by"文本不再误判为排序子句;datediff(month,…)月差口径统一为年月分量差(MONTHS_BETWEEN的oracle小数口径不再作为源函数注册);to_char转sqlserver以FORMAT承担,修复大写格式token('YYYY-MM-DD HH24:MI:SS')因.NET大小写敏感的错值并归一带引号格式串字面量保护;sqlite目标的now()/CURRENT_DATE/CURRENT_TIMESTAMP统一取本地时钟
3、修复SubStr负起点映射与mysql契约偏离的静默错值:|n|超过串长时mysql的substr(s,-n[,len])起点算出<1返回空串,原两参RIGHT短串返回整串、三参case守卫短串分支返回substring(s,1,len)均与之偏离(pg系/sqlserver/h2/db2/hana共四处);统一加长度守卫、短串分支返回空串(db2的守卫长度函数经Length适配器细化为char_length,字节/字符语义对齐);正起点与mysql/oracle原生透传路径不变,FunctionConvertMatrixTest断言同步更新
4、修复java风格独立HH时间token在oracle模型目标库的静默错值:小时分桶等无分钟格式(如'yyyy-MM-dd HH')不含冒号邻接的HH:mm,既有归一不命中,pg/oracle系将HH按12小时制解释致分桶边界错乱;DateFormat/ToChar的pg/oracle系分支在冒号归一后补\bHH\b→hh24词边界归一(\b保证不伤oracle原生HH24,HH:mm已被前步消费不重复命中);mysql/H2/CH/sqlserver分支的映射链本就正确不受影响;FunctionConvertMatrixTest新增独立HH用例并锁HH24不误伤回归
5、配套的quickvo-maven-plugin升级至2.0.2(项目地址:https://gitee.com/sagacity/maven-quickvo-plugin,完整功能与配置说明参见其README)
6、补齐SqlUtil.processResultSet裸List路径(findByJdbcQuery/loadByJdbcQuery等直接jdbc查询API)的ext类型处理遗漏:此前该路径无列读取策略预分类与normalizeExtTypeValue归一(Map/VO路径与ResultUtils管线早已接入),oracle的JSON/VECTOR列getObject直接抛ORA-17004/18722、h2 json与mysql系geometry/vector等EXT_BYTE列以byte[]、pg系以PGobject等驱动专属形态静默进入结果行;现于行循环外做查询级预分类(readColumnTypeNames+buildColumnKinds),行循环内按TEXT_READ策略文本化读取并经normalizeExtTypeValue归一,与ResultUtils.processResultRow/reflectResultToVO同口径;解密按定位不在裸路径补齐;新增SqlUtilExtTypeRawPathTest以H2 JSON列端到端锁定归一链路

# v5.6.94.RC6 2026-09-05
1、树表封装wrapTreeTableRoute支持统一更新字段：配置了unifyFieldsHandler时,自动将updateUnifyFields()(如最后修改人、最后修改时间)按实体属性名转列名后附加到路由级联、叶子标记重建的全部update语句并绑定参数,与updateByQuery等更新流程的统一字段语义保持一致;仅模型指定实体时生效,实体中不存在的字段自动忽略,无实体模型不处理公共更新字段
2、修复oracle目标下datediff函数转换对裸字符串字面量参数生成非法sql的缺陷:两参/三参转换中的字面量参数(如datediff('2026-01-20','2026-01-15'))自动包装to_date(长度启发式同to_date单参,含时间部分补'yyyy-MM-dd hh24:mi:ss'),规避TRUNC('...')等写法在默认NLS_DATE_FORMAT(DD-MON-RR)会话下报ORA-01722/ORA-01861(oracle 21c实测);列、表达式及to_date等已有函数参数不受影响
3、修复dm/pg系数据库DDL生成丢失时间部分的缺陷(实测dm的DATE列类型只存日期,与oracle的DATE含时间不同;pg系date同样只存日期,pg 18.6实测timestamp写入date列静默截断时间,按生成DDL建表后时间被静默清零):DDL字段类型转换依据字段Java类型精化,java.util.Date/LocalDateTime等含时间类型的列在dm输出DATETIME、pg及衍生库(openGauss/MogDB/Vastbase/GaussDB/StarDB/Oscar/Kingbase)输出TIMESTAMP(覆盖@Column未指定type自动探测与quickvo生成实体显式声明type=DATE两种场景;衍生库Oracle兼容模式DATE本含时间,TIMESTAMP为无损超型),LocalDate/java.sql.Date纯日期类型保持DATE不变;字段JDBC类型探测保持原状不调整,避免影响sqlserver等对Types.TIMESTAMP有特殊语义(行版本戳rowversion,运行时从merge/insert/update中剔除)的逻辑
4、新增oracle/dm真实库函数转换冒烟测试(OracleRealDbSmokeTest/DmRealDbSmokeTest):覆盖nvl/to_char/date_format/to_date/concat/concat_ws/substr/instr/length/datediff/if/decode/group_concat转listagg/字面量安全/union-all-count在真实库上的转换与执行,连接配置走target下probe.properties不入库,环境不可用自动跳过;dm测试驱动DmJdbcDriver18以test scope引入

# v6.0.3 2026-10-01
5、convertType数字分支强化:字符串值解析为BigDecimal/Integer/Long/Double等数值类型前剥离千分位分组逗号,修复sql中number-format等带#,##格式化列(如"8,888.0000")映射VO数值属性时抛"Character , is neither a decimal digit number..."的缺陷(BeanUtil.convertBoolean统一收口,仅严格匹配分组形态才剥离,非法形态如"1,23"保持原解析报错;XMLUtil数值解析同步受益)
6、新增realDialectFirst配置(默认false):开启后sqlId方言变体查找优先按连接探测的真实方言(DBProfile.realDialect,典型如OB按mysql方言配置而真实库为oceanbase、代理库场景),真实方言变体不存在时回退原有配置方言查找链,真实方言与配置方言一致时行为不变;实现上SqlScriptLoader.getSqlConfig读取线程连接档案(SqlToyThreadDataHolder.getDBProfile)优先取真实方言变体(覆盖连接回调内嵌套解析),顶层查询变体在连接外解析拿不到线程档案,由DialectUtils查询管线统一预处理入口(getUnifyParamsNamedConfig)按profile经SqlToyContext.resolveRealDialectVariant显式重解析补齐(配置方言变体不降级为base);配置属性已经spring-starter(spring.sqltoy.realDialectFirst)和solon-plugin暴露,并随SqlToyContext.initialize写入SqlToyConstants.realDialectFirst静态常量(JVM级首个context生效),便于后续环节随时获取;同步补齐函数替换遗漏:6个sql片段getter汇聚的SqlToyConfig.getDialectSql惰性转换收口点按同一开关将转换方言换算为真实方言(线程档案由processDataSource按连接绑定,仅sql形态层——函数与保留字,分页等执行策略的dbType分派不受影响),且**仅限经realDialectFirst变体匹配选中的sql**(realDialectMatched标记,由变体匹配点置位;未命中变体回退base的sql保持配置方言行为,不被顺带转换),迁移场景下全局dialect=mysql固化的ifnull等mysql形态按kingbase真实库转换为coalesce,真实方言与配置一致/线程无档案/开关关闭时行为零变化
7、新增<sql dialect="xxx">元素属性(此前该属性不被解析而静默忽略):声明本条sql按指定方言形态解析固化——加载时按声明方言做一次函数/保留字转换并固化解析标签,执行期当前数据库方言与声明一致时复用既有"查询方言==解析标签"早退机制跳过函数替换,异方言查询走既有惰性转换+dialectSqlMap缓存反向适配;count-sql与主sql使用同一生效方言(此前按全局方言转换,属性覆盖后会形态错配);优先级为mql/eql强制mongo/es>元素dialect属性>全局方言(mql/eql不支持该属性),属性值经getDBType校验,未识别方言warn提示并回退全局方言(打错字表现为属性不存在,避免产生永不早退的标签);sqltoy.xsd的sqlToySqlType同步声明该属性(仅sql,mql/eql不加——运行时强制mongo/es覆盖;运行时DOM解析非校验模式不依赖xsd,此项为IDE校验/补全体验,远程发布副本https://sagframe.github.io/schema/sqltoy.xsd随发布流程另行同步);与realDialectFirst协同:<sql id="x_kingbase" dialect="kingbase">被变体匹配命中时加载已固化kingbase形态且标签相等,执行连惰性转换都省略

# v5.6.43 2025-04-10
1、getTables方法优化了postgresql分区表的过滤，保留基础分区表，过滤掉子分区表
2、增强@if(:param1<>A && :boolParam) 含boolean类型参数不完整表达式[完整模式:booleanParam==true]的兼容处理

# v5.6.42 2025-04-02
1、修复分组link结果为List、Set时最后一条为一组时未被处理的缺陷

```xml
<sql id="common_resourceRoles" debug="false" dataSource="portalDataSource">
<link id-columns="res_url" columns="role_code" distinct="true" result-type="SET"/>
<value>
    	<![CDATA[
            select
                t3.res_url,
                t2.role_code
            from SAG_ROLE_RESOURCES t1
            left join sag_resource t3 on t3.res_id=t1.res_id
            left join sag_role t2 on t2.role_id=t1.role_id
            where t3.res_url is not null and t3.NODE_LEVEL >= 3
            and t3.status=1
            and t1.status=1
            -- and t3.view_type='F'
            and t3.app_code=:appCode
            order by t3.res_url
    		]]>
    </value>
</sql>
```

# v5.6.41 2025-03-03
1、修复DateUtil字符串转日期，字符串值不合规未能提示的缺陷

# v5.6.40 2025-02-26
1、查询条件参数支持JSONArray和JSONObject
2、多字段in支持(id,type) in （:list.id,:typeCode）参数值存在部分数组场景
3、升级solon、spring依赖版本

# v5.6.39 2025-02-10
1、优化5.6.38 -- @fast_start 和 -- @fast_end的标记，支持只需要-- @fast_start即可标记
2、优化支持lightDao.Store().resultTypes(Class...clasess)多个结果类型即可替代.moreResult(true)的设置
3、优化loadAll,改进大批量并行执行(之前是先分批后拆分并行，导致并行实际并未生效或无法起到实质效果)
4、调整提示慢sql的时间标准，默认值从30秒改为8秒

# v5.6.38 2025-02-04
1、增加通过/* @fast_start */ 和 /* @fast_end */代替@fast标记快速分页的开始和结束位置，从而便于sql调试
2、@fast支持@fast （sql) 左括号跟@fast之间存在空格情况的匹配

# v5.6.37 2024-12-29
1、提供针对同一个字段进行多次翻译或根据具体逻辑条件进行翻译的功能

```xml
<sql id="sqltoy_complax_trans">
      <!-- where like colName==xxx | colName!=xxx | colName in (a,b) |colName out (a,b) -->
      <translate cache="dictCache" cache-type="purchase_deliver_type" columns="deliver_type" where="order_type==purchase"/>
      <translate cache="dictCache" cache-type="sales_deliver_type" columns="deliver_type" where="order_type==sales"/>
      <value><!CDATA[[
       select order_id,order_type,deliver_type
       from order_info
         ]]>
      </value>
</sql>
```

# v5.6.35 2024-12-25
1、修复数据库Blob类型字段值长度为0时场景处理缺陷
2、升级solon依赖为3.0.5版本、springboot3.4.1

# v5.6.34 2024-12-12
1、修复@value(:inField) in (:ids) ids超过1000场景下的缺陷
2、升级solon依赖为3.0.4版本、springboot3.4

# v5.6.33 2024-11-19
1、增强in条件的参数值去重功能
2、sql查询代替group_concat的link功能支持结果类型为HashSet
3、升级solon依赖为3.0.3版本

# v5.6.31 2024-11-08
1、增强sql自动分页提取count部分sql的算法，兼容最终from前面有非（select from）对称场景:

```sql
select field1,(day from(xxx)) as aliase from table 
```
2、优化DateUtil,对非规范长度的字符串(如:2024-11-07 10:52:36.12345)转LocalDateTime、LocalTime的兼容处理
3、强化opengauss系列getTables方法，表名匹配小写化

# v5.6.30 2024-10-31
1、增加opengauss、stardb数据库的支持，将vastbase、mogdb改为继承opengauss模式

# v5.6.29 2024-10-24
1、增加海量数据库(vastbase)支持，默认映射成gaussdb执行
2、分页最大单页记录pageFetchSizeLimit(对应参数:spring.sqltoy.pageFetchSizeLimit)小于0表示不做限制
3、优化个别rs.close()行为，统一放入finally块中处理
4、升级solon3.0.2版本

# v5.6.28 2024-10-17
1、规范if/elseif/else的逻辑(历史版本:if场景，sql中无动态参数也会被剔除)

```sql
select * from table where name='测试'
-- 非if逻辑场景下,内部动态参数为null，最终为and status=1 也要自动剔除
#[and status=1 #[and type=:type] #[and orderName like :orderName] ]
-- flag==1成立，因为内容存在动态参数，所以继续变成#[and status=:status]参数为null剔除，不为null则保留
#[@if(:flag==1) and status=:status]
-- 成立,因为and status=1 没有动态参数,则保留and status=1
#[@if(:flag==1) and status=1 ]
#[@elseif(:flag==2) and status in (2,4) ]
#[@else and status in (1,2)]
```

# v5.6.27 2024-10-16
1、修复StringUtil.firstToLowerCase单字符处理缺陷

# v5.6.26 2024-10-01
1、针对batchUpdate、saveAll、updateAll、saveOrUpdateAll、loadAll等批量操作，针对超大数据集场景提供并行执行机制

# v5.6.25 2024-09-29
1、save操作增加判断主键值不为空时，且是sequence主键策略则主动调整为assgin，避免sequence跳号
2、batchUpdate、executeSql操作时，增加判断sql是否是merge into 且方言是sqlserver，帮助自动补充分号结尾(sqlserver merge into 必须是;结尾)
3、完善代码注释以及变更日志，增强代码可理解性和变更原因
4、升级pom第三方组件的依赖版本

# v5.6.24 2024-09-25
1、增加 @if （:param exclude xxx）逻辑(include的取反)
2、优化deleteAll底层逻辑，改为delete from table id in （:ids）,复合主键为: (code,type) in （(:codes,:types))，提升效率
3、优化loadAll级联加载，复合主键支持select * from table （id，code） in ((x1,x2),(x11,x22)) 多字段in模式，提升效率

# v5.6.23 2024-09-19
1、修复@fast( @include("sqlId") ) 场景缺陷
2、强化where #[field1=:field1Value and] field2=:field2Value 场景sql处理缺陷

# v5.6.22 2024-08-30
1、修复同比环比计算中除数跟零的对比缺陷(BigDeciaml("0.00") 不等于BigDeciaml.ZERO)
2、修复跨数据库适配Nvl函数，排除coalesce匹配，coalesce各种数据库都支持且非单纯的ifnull/nvl逻辑

# v5.2.9  2022-8-21
1、针对一下产品化的软件(适配多种数据库:mysql\oracle\postgres\db2\mssql),要求在一个数据库场景下可以测试相同的功能是否可以适配不同数据库

# v5.2.8  2022-8-11
1、支持动态增加缓存翻译配置和缓存更新检测

# v5.2.7  2022-8-6
1、增加将缓存数据以VO/DTO 对象类集合返回方法
public <T> List<T> getTranslateCache(String cacheName, String cacheType,Class<T> reusltType);
2、优化树型表结构封装wrapTreeTableRoute事务问题，将autoCommit=true改为了null不强制提交
3、getTableColumns(String tableName)方法增加字段:是否是索引、是否unique、索引名称三个属性

# v5.2.6  2022-7-24
1、增加了spring.sqltoy.columnLabelUpperOrLower属性，设置getMetaData().getColumnLabel(index)是否转大小写，默认为default，表示不做处理
2、支持以流模式获取查询结果

# v5.2.5  2022-7-22
1、改进save、saveAll等操作的默认值处理机制，通过对象填充默认值代替insert sql中nvl(?,'1') 模式

# v5.2.3  2022-7-1
1、增加OverTimeSqlHandler 提供超时执行sql的采集
调用方法: lazyDao.getSqlToyContext().getSlowestSql(10,true)
2、针对loadAll级联加载关联字段值为null报NPE问题修复

# v5.2.2  2022-6-23
1、优化nanotimeId主键生成策略，避免在一次取10万规模时存在偶发重复

# v5.2.1  2022-6-18
1、sqltoy抽离对spring的依赖，便于其他框架对sqltoy扩展【5.2.0】
2、针对mongo、elasticsearch 查询，修复mongo().entity(entity)对象传参失效问题【5.2.1】
3、MapKit增加了map(key,value)方法，便于构建单值map【5.2.1】

# v5.1.46 2022-06-11
1、缓存翻译配置支持多个配置文件,默认配置为:classpath:sqltoy-translate.xml;classpath:translates
2、select concat(id,'/',amt) from 查询出byte[] 数组,并映射到对象字符类型属性上的场景优化
3、where #[条件] limit 条件不成立变成了where limit ，增加了limit 关键词处理，同时补全where 1=1避免特殊情况
4、NumberUtil中数字转英文金额支持万亿以上单位

# v5.1.43 2022-05-28
1、修复postgresql identity主键策略保存返回id跟建表字段顺序有关的缺陷(identity 主键不在第一列)

# v5.1.42 2022-05-25
1、支持多字段in，例如:(id,type) in ((:ids,:types)) 或 (id,type) in (:ids,:types)

# v5.1.38 2022-05-18
1、优化级联加载OneToOne 关联多条的验证,当为多条抛出异常
2、针对loadAll 场景，优化逻辑兼容ManyToMany、ManyToOne
3、summary汇总求平均计算增加skip-single-row属性(默认false)，可设置单行分组记录不进行汇总求平均计算

# v5.1.36 2022-05-12
* 1、在sql xml的filters中扩展一个自定义条件参数处理器
* 2、优化wrapTreeTable组织树结构过程中idField数据类型判断，减少手工设置
* 3、unpivot列转行支持多组列转行(参见word文档)
* 4、@if()宏条件判断增强，支持无参数判断@if(1==1) 便于跟@loop等组合使用

# v5.1.33 2022-04-24
* 1、处理in条件查询语句参数数组长度超过1000场景的处理
```
 t.order_id (not) in (:orderIds) 转变成
( t.order_id in (?,?..) or t.order_id in (?,?..)) 或 
( t.order_id not in (?,?..) and t.order_id not in (?,?..))
```
* 2、findBySql(sql,map,resultType) 支持当查询单列时、resultType为原生类型，返回一维 List<T> 模式
* 3、分页页数超出范围处理策略参数:spring.sqltoy.pageOverToFirst默认值改为false(原本跳到第一页变为返回空集合)
* 4、升级spring等pom依赖版本

# v5.1.31 2022-03-29
* 1、兼容查询结果是空字符映射到数字等类型的处理
* 2、通过链式操作中deeply实现saveOrUpdateAllDeeply想要的功能:lazyDao.save().deeply(true).saveMode(SaveMode.UPDATE).many(entities)
* 3、优化sql日志输出，可以通过debug="true" 属性来控制日志是否输出

# v5.1.30 2022-03-21
* 1、修复updateByQuery因where参数为in (?) 模式导致参数长度和参数类型长度不一致缺陷

# v5.1.29 2022-03-19
* 1、修复summary算法，修复逆向汇总时add(0,createSummaryRows)改为addAll(0,createSummaryRows)
* 2、filters中的to-date 日期加减操作单位支持months、years

# v5.1.25 2022-02-13 
* 1、sql文件filters中增加clone方法
* 2、findByQuery(QueryExecutor query) 结果QueryResult增加List getFirstColumn(boolean distinct) 方法，便于提取结果集单列值形成一维数组

# v5.1.24 2022-01-18 
* 1、修复NumberUtil 数字转英文大写金额负数问题
* 2、优化ParamFilterUtils类，支持多个primary 参数过滤处理器场景
* 3、升级部分pom依赖版本

# v5.1.23 2022-01-04 
* 1、增加deleteByIds快捷删除方法
* 2、修复deleteByQuery中的ParamsFilter使其生效
* 3、优化缓存翻译cache-indexs配置错误引起的数组越界提示，提供完整的错误提示

# v5.1.22 2021-12-26
* 1、updateByQuery支持set field=field+1 模式的自依赖更新
* 2、findByQuery或findPageByQuery等QueryExecutor中可设置hiberarchy=true并通过对象中@OneToOne和@OneToMany将结果封装成对象层次结构返回

# 5.1.21 2021-12-19
* 1、去除多余的log4j2的依赖,sqltoy本身一直都不依赖log4j2(演示功能剥离时漏删除)
* 2、优化一些校验和错误提示

# 5.1.20 2021-12-11
* 1、升级log4j2依赖版本为2.15.0(实际sqltoy并不依赖log4j2,log4j2为多余的依赖，下版去除)
* 2、sql补全优化，如from table where xxx 情况下自动补全select *
* 3、增加一些容错校验判断

# 5.1.19 2021-12-04
* 1、修复updateByQuery功能对象或Map传where条件参数值的一个bug

# 5.1.18 2021-11-29
* 1、优化POJO注解解析，支持多级父类，适度优化代码书写

# 5.1.17 2021-11-18
* 1、增加 @ Translate 缓存翻译注解

# 5.1.16 2021-11-13
* 1、优化sql在跨数据库函数适配转换时支持函数嵌套
* 2、完善sqltoy中统一字段赋值，在oracle、db2、sqlserver场景下调用saveOrUpdate/All 时因无法判断是新增还是修
改，导致统一赋值中关于新增记录的诸如：创建人、创建时间等属性无法赋值，现通过组织merge into 语句时伪造成默认值形式实现
* 3、增强findEntity中select(fields) pojo属性名转表字段名称能力
* 4、增强sql中在特殊场景下使用@value(:sqlPart)引入sql片段时，做跨数据库函数适配

# 5.1.15 2021-11-10
* 1、增加数据库表字段值加密存储、查询解密，默认采用RSA算法实现
* 2、将脱敏变成接口化，框架提供默认实现，便于开发者扩展
* 3、增加MapKit便于快速构造Map进行传参

# 5.1.14 2021-10-29
* 1、优化executeSql增加存在分库分表场景
* 2、batchUpdate增加公共属性赋值(创建人、创建日期、修改人、修改日期等)
* 3、updateByQuery、deleteByQuery、updateFetch完善分库分表场景
* 4、优化sql中以？(问号)传参且有@fast()场景下按比例取Top记录和取随机记录时参数位置处理缺陷

# 5.1.13 2021-10-26
* 1、完善查询传参数合法性验证
* 2、升级依赖包版本

# 5.1.11 2021-10-20
* 1、修复5.1.9版本引起的分页查询sql语句中以？模式传参场景下的bug(:paramName模式不受影响)
* 2、优化sqlserver分页查询order by 语句在#[@if(:xxx==xxx1) order by field] 场景下@if 不成立时去除了order by 导致判断是否存在order by 缺陷

# 5.1.10 2021-10-18
* 1、增加统一数据权限传参和越权检查，参照文档统一字段处理章节配置
* 2、兼容sql中参数名称含中文的特殊场景
* 3、@loop(:paramName,sql片段)支持类似:staffInfos[i].birthday 对象子属性模式，为应对极端场景提供策略
* 4、代替group_concat的link对应的id-column改为id-columns支持多列分组(依旧兼容id-column)

# 5.1.8 2021-10-09
* 1、batchUpdate方法支持List传参数模式，同时支持子类属性
* 2、修复sql去除回车换行导致@if()中或(||)符号被替换的bug

# 5.1.6 2021-09-28
* 1、排查修复db2 updateSaveFetch锁语句为for update with rs
* 2、优化queryResult中labelTypes类型处理(针对存在缓存翻译、格式化场景改变了结果类型)
* 3、升级依赖库版本

# 5.1.4 2021-09-17
* 1、findEntity可以用entity查询但返回结果类型是dto
* 2、优化IdUtil产生主键依赖的通过ip获取serverId场景，规避无网卡情况下无法获取ip的特殊情况
* 3、优化sql日志输出，减少过多的空白影响sql阅读
* 4、增强部分跨数据库sql函数自适应

# 5.1.3 2021-09-15
* 1、增加获取数据库表信息和表字段信息功能

# 5.1.2 2021-09-07
* 1、修复updateSaveFetch复合主键场景下的bug，遗漏了条件中的and
* 2、修复sqlite数据库identity主键策略insert的缺陷
* 3、优化saveOrUpdate，增加当主键值为null时直接走save操作

# 5.1.0 2021-09-01
* 1、针对类似库存台账、客户资金帐等强事务、高并发场景，提供updateSaveFetch新的一次数据库交互完成锁查询、更新、记录不存在则插入、返回的全过程的方法

# 5.0.13 2021-08-26
* 1、支持数据库表字段默认值为空白的场景(配合quickvo-4.18.18版本)
* 2、delete级联删除时增加了级联字段值是否为null的验证(针对级联字段非主键场景)，当为null抛出非法异常

# 5.0.12 2021-08-21
* 1、修复NumberUtil类数字转中文金额过亿元万位数全为零的一个缺陷
* 2、elastic sql http模式执行下增加@blank(:name)和@value(:name)

# 5.0.11 2021-08-17
* 1、优化重复sqlId处理，进行集中提示并做异常退出处理
* 2、依赖的jar包版本升级

# 5.0.9 2021-08-5
* 1、支持impalajdbc，主要针对kudu进行了集成，进一步增强大数据体系下的应用
* 2、findEntity的EntityQuery增加了groupBy()和having() 参数设置
* 3、优化支持elasticsearch原生sql取top记录的sql组织方式
* 4、优化sql文件加载日志提示

# 5.0.7 2021-07-14
* 1、修复oracle数据库oracle.sql.TIMESTAMP 转LocalDateTime的支持

# 5.0.6 2021-07-13
* 1、优化查询过程中首次调用缓存无法打印查询自身sql日志，因sql日志在当前线程中，调用缓存时会冲掉当前线程中的日志

# 5.0.5 2021-07-11
* 1、修复分页并行查询时，用到缓存翻译，且缓存正好是第一次被加载导致当前线程日志被置空产生的异常

# 5.0.3 2021-07-7
* 1、查询参数findBySql(String sql,Map paramsMap,Class resultType) map支持多层子对象属性
* 2、SqlToyDaoSupport中增加findPageBySql(final Page page, final String sqlOrNamedSql,
final Map<String, Object> paramsMap, Class voClass) 方法

# 5.0.1 2021-07-01
* 1、修复生成POJO含schema时生成update语句时2次拼接schema的错误

# 5.0.0 2021-06-23
* 1、规整4.x版本的代码目录，使其更加科学
* 2、去除executor目录，将QueryExecutor 对外的模型统一移入model目录下面
* 3、将非对外的内部模型移入到model.inner 包下面，将一些配置化的模型移入到config.model下面
* 4、将PaginationModel 改为Page，并将PageNo 由Long改为long，避免需要写1L，简化书写
* 5、优化support下面的LinkSupport，BaseSupport
* 6、剔除掉LinkSupport和BaseSupport，合并到SqlToyDaoSupport
* 7、去除一些根本用不到的方法，避免产生疑问和混淆，使得SqlToyDao更加清晰
* 8、去除updateFetchTop、updateFetchRandom
* 9、去除：public Long executeSql(String sqlOrNamedSql, Serializable entity, ReflectPropsHandler reflectPropertyHandler)带有reflectPropertyHandler 的开放方法
* 10、去除@ListSql @PageSql @LoadSql 这些注解，尽量让使用方法归一
* 11、去除ObtainDataSource，避免跟DataSourceSelector产生功能重叠
* 12、将ConnectonFactory移入org.sagacity.sqltoy.plugins.datasource包下
* 13、剔除findAll方法，用findEntity(Class voClass,null) 代替findAll方法属于极小众方法
* 14、并行查询设置分页模型方法：pageMode(Pagination pageModel)改为page(Page page)
* 15、增加loadEntity方法，通过EntityQuery获得单条记录：
 public <T extends Serializable> T loadEntity(Class<T> entityClass, EntityQuery entityQuery);
* 16、增加numFmt（numberFormat) 对英文金额转大写的支持
   <number-format columns="total_amt" format="capital-en"/>
* 17、删除对SybaseIQ数据库的支持
* 18、优化部分不使用的代码和注释

# v4.18.25 2021-06-08
* 1、修复oracle无主键表保存操作一个NPP错误
* 2、优化defaultDataSource获取模式，适应dynamic-datasource插件场景
* 3、在sqlToyContext中扩展了ConnectionFactory 供自定义获取当前ThreadLocal中的connection的机制，为非spring场景做铺垫

# v4.18.22 2021-05-26
* 1、在findEntity中EntityQuery可以设置fetchSize
* 2、在sqltoyContext中可以全局设置fetchSize,如: spring.sqltoy.fetchSize=200
* 3、convertType 支持空集合返回空集合
* 4、针对一些特殊原因导致表名是数据库关键词的处理支持

# v4.18.21 2021-05-21
* 1、修复elasticsearch sql查询的一个NPP错误
* 2、date-format增加locale选项，支持英文等日期格式化
* 3、针对分页和取top记录和取随机记录方法适度进行了规整，避免每个方言里面重复书写

# v4.18.18 2021-04-26
* 1、优化极端场景下sql中单行注释处理

# v4.18.13 2021-04-15
* 1、增加DataSourceSelector扩展，提供在多数据源特殊场景下可自行扩展

# v4.18.8 2021-03-24
* 1、优化clickhouse修改和删除操作，增加对dorisdb的支持

# v4.18.7 2021-03-12
* 1、兼容pojo或dto的属性名称含下划线的特殊场景，如：staff_name 和对应的getStaff_Name()
* 2、分页页号越界时可统一配置是否从第一页开始:spring.sqltoy.pageOverToFirst默认为true
* 3、列转行允许将结果映射到vo/pojo,map等类型上
* 4、优化loadAll复合主键场景下的处理逻辑以及附带的级联加载逻辑

# v4.18.3 2021-2-26
* 1、级联操作进行优化，精简级联配置，增加OneToOne类型的支持

```java
	// 可以自行定义oneToMany 和oneToOne
	// fields:表示当前表字段(当单字段关联且是主键时可不填)
	// mappedFields:表示对应关联表的字段
	// delete:表示是否执行级联删除，考虑安全默认为false(有实际外键时quickvo生成会是true)
	@OneToOne(fields = { "transDate", "transCode" }, mappedFields = { "transDate", "transId" }, delete = true)
	private ComplexpkItemVO complexpkItemVO;
```
* 2、修复xml定义sql中number-format和date-format多个参数换行没有trim的缺陷
* 3、优化cache-arg 反向通过名称匹配key，将之前字符串包含变为类似数据库like模式，可以实现：中国 苏州 带空格的模式匹配
* 4、quickvo 进行级联优化适配升级，版本4.18.3

# v4.13.11 2020-7-31
* 1、修复EntityManager中对OneToMany解析bug(4.13.10 版本已经调整)。

```java
for (int i = 0; i < idSize; i++) {
	// update 2020-7-30 修复取值错误,原:var = oneToMany.mappedFields()[i];
	var = oneToMany.fields()[i];
	for (int j = 0; j < idSize; j++) {
		idFieldName = idList.get(j);
		if (var.equalsIgnoreCase(idFieldName)) {
			// 原mappedFields[j] = var;
			mappedFields[j] = oneToMany.mappedFields()[i];
			mappedColumns[j] = oneToMany.mappedColumns()[i];
			break;
		}
	}
}
```
* 2、修复loadCascade时Class... cascadeTypes 模式传参判空处理错误，导致无法实际进行加载(4.13.7 版本修改导致)

```java
protected <T extends Serializable> T loadCascade(T entity, LockMode lockMode, Class... cascadeTypes) {
	if (entity == null) {
		return null;
	}
	Class[] cascades = cascadeTypes;
	// 当没有指定级联子类默认全部级联加载(update 2020-7-31 缺失了cascades.length == 0 判断)
	if (cascades == null || cascades.length == 0) {
		cascades = sqlToyContext.getEntityMeta(entity.getClass()).getCascadeTypes();
	}
	return dialectFactory.load(sqlToyContext, entity, cascades, lockMode, this.getDataSource(null));
}
```

* 3、全部修复update级联时，在mysql、postgresql子表采用原生sql进行saveOrUpdateAll的bug，分解为:先update后saveIgnoreExist模式
* 4、提供代码中动态查询增加filters,便于今后文本块应用sql直接写于代码中情况下可以动态调用缓存翻译、filters等功能

```java
@Test
public void findEntityByVO() {
	List<StaffInfoVO> staffVOs = sqlToyLazyDao.findEntity(StaffInfoVO.class,
			EntityQuery.create().where("#[staffId=:staffId]#[and staffName like :staffName] #[ and status=:status]")
					.values(new StaffInfoVO().setStatus(-1).setStaffName("陈").setStaffId("S0005"))
					.filters(new ParamsFilter("status").eq(-1)).filters(new ParamsFilter("staffName").rlike())
					.filters(new ParamsFilter("staffId").primary()));
	System.err.println(JSON.toJSONString(staffVOs));
}
```

# v4.8.2 2019-10-12
* 1、修复#[@if(:param==null) and t.name=:name] 其中:param为null则剔除整个#[] 之间sql的不合理规则
* 2、开放TranslateCacheManager的扩展注入，便于可以替换目前的默认实现
* 3、增加了缓存更新检测的集群节点时间差异参数[sqltoy-translate.xml配置]，保障集群环境下缓存更新检测的时效：
   <cache-update-checkers cluster-time-deviation="1">

# v4.8.1 2019-09-24
* 1、优化将查询部分PreparedStatement 原: ResultSet.TYPE_SCROLL_INSENSITIVE 改为ResultSet.TYPE_FORWARD_ONLY，提升效率。
* 2、修复根据方言提取sql部分缺陷。

# v4.8.0 2019-09-17
* 1、调整了plugin包为plugins，下面的类目录做了适当调整，便于今后扩展
* 2、大幅优化改进跨数据库函数替换处理模式，将之前执行时函数替换改进为缓存模式，相同数据库类型的存在则直接提取，不存在则进行函数替换并放入缓存中，大幅提升效率
* 3、优化了IUnifyFieldsHandler接口，增加public IgnoreCaseSet forceUpdateFields()方法，便于提供统一公共字段强制修改策略，如最后修改时间

```java     
    /* (non-Javadoc)
	 * @see org.sagacity.sqltoy.plugins.IUnifyFieldsHandler#forceUpdateFields()
	 */
	@Override
	public IgnoreCaseSet forceUpdateFields() {
		//强制updateTime 和 systemTime 进行修改
		IgnoreCaseSet forceUpdates=new IgnoreCaseSet();
		forceUpdates.add("updateTime");
		forceUpdates.add("systemTime");
		return forceUpdates;
	}
```
* 4、修复函数替换sysdate 无括号匹配处理的缺陷
* 5、其他一些优化

# v4.6.4 2019-09-10
* 1、改进缓存更新检测机制由timer定时改为线程内循环检测。
* 2、改进shardingDataSource检测机制由Timer定时改为线程内循环检测
* 3、修复DTO 中当同时有 isName 和 Name （isXXXX 和 XXXX 字段）时映射错误,强化isXXXX形式是boolean类型判断。
* 4、增强函数to_char和date_format 跨数据库时自动替换，实现mysql和oracle等情况下sql的通用性
* 5、升级fastjson依赖版本
* 6、其他一些优化

# v4.6.2 2019-08-26
* 1、优化SQL文件发生变更重新加载的机制，变成独立的进程进行文件变更监测，代替原来根据sqlId获取sql内容时检测机制。
* 2、增强了缓存翻译缓存初始化文件策略
* 3、增加多数据源连接信息debug输出，便于开发阶段识别
* 4、增加了容器销毁时自动销毁定时检测任务和缓存管理器

```xml
<bean id="sqlToyContext" class="org.sagacity.sqltoy.SqlToyContext" init-method="initialize" destroy-method="destroy">
</bean>
```
	
# v4.6.0 2019-08-12
* 1、全面支持jdk8 的LocalDate、LocalDateTime、LocalTime 日期类型；
* 2、优化EntityManager 解析对象的代码，避免子类中定义父类中属性导致被覆盖问题；
* 3、quickvo也同步进行了修改，默认类型为jdk8的日期类型
* 4、提交sqltoy-showcase 范例代码，请参见src\test 下面的用例 和 src\main\resources 下面的配置生成vo请参见sqltoy-showcase\tools\quickvo 下面的配置

# v4.5.3 2019-07-19
* 1、优化一些注释
* 2、增强泛型支持，避免：(VO)load(xxxVO) 这种前面需要强转换的操作
* 3、增强with as 语法几种模式的解析，如：with t1 (p1,p2) as () select * from t1 模式
* 4、sql文件变更解析改成同步机制，避免应用启动阶段因个别任务执行sql导致重复加载
* 5、sql模型增加是否有union语法的判断变量并优化部分机制，避免执行过程中去解析判断是否有union 和with as语法，从而提升效率。

# v4.5.2 2019-07-16
* 1、将统一接口层面的异常抛出去除，统一内部抛出RuntimeException，避免开发者强制要进行异常捕获处理（关键更新，因此版本号从4.3升级到4.5，但不影响之前的使用）
* 2、cache-arg 缓存条件转换二级过滤逻辑bug修复。
* 3、sql查询filter中to-date 过滤处理器里面增加了first_week_day，last_week_day 类型，取指定日期所在周的第一和最后一天
* 4、部分注释增强和少量代码优化
* 5、针对jdk11 进行了适应性代码改造，将已经废弃的写法重新依据新标准改写，因此sqltoy完全可以在jdk8和以后的版本中应用
* 6、增强对update xx set #[, field=？ ] 场景语句set 和 后续语句连接存在多余的逗号问题的兼容处理

# v4.3.12 2019-06-27
* 1、修复select * from table where instr(a.name,'xxx?x') and a.type=:type 形式查询问号导致的问题
* 2、放开参数条件名称不能是单个字母的限制，如 where a.name like :n ，单个字母参数命名一般不严谨，不建议如此简化，基本无法表达意思，考虑有些开发思维不够严谨，特此兼容
* 3、优化sql执行时长超过阀值的打印日志格式，便于从日志中快速找到性能慢的sql
* 4、优化部分代码，明确Exception的类型，避免部分条件检查直接抛出Exception，而是明确具体的条件非法或其他的准确的异常，避免事务控制时无法判断RunException 还是checkException

# v4.3.10 2019-05-08
* 1、支持elasticsearch6.x版本的restclient
* 2、强化sql查询固定参数中的问号处理
* 3、注释优化

# v4.3.7.2 2019-04-12
* 1、修复sql文件加载剔除非相同方言的sql文件的bug

# v4.3.7 2019-04-02
* 1、增加数字格式化精度设置(四舍五入、去位、进位等)
* 2、增加基于springsecurity安全框架下通用字段的赋值实现(可选,根据项目情况选择使用，不影响项目)

# v4.3.6.1 2019-02-25
* 1、修复查询语句中to-date('2019-01-01 12:20:30') 给定固定值情况下将:dd 作为:paramNamed 变量参数条件问题
* 2、强化分页查询对分组和统计型语句的判断，解决因为子查询中存在统计函数，将sql误判为统计型查询，count语句没有最优化
* 3、优化缓存条件过滤，未匹配情况下的赋值问题

# v4.3.6 2019-02-22
* 1、修复sql中别名参数正则表达式，避免to-date('2019-02-01 12:21:30','yyyy-MM-dd HH:mm:ss') 直接将具体数值写入sql时未能区分出:paramNamed 格式
* 2、优化缓存条件筛选过滤机制，当未匹配上时返回指定值

# v4.3.5 2019-01-28
* 1、优化redis全局业务主键生成格式，变成sqltoy_global_id:tableName:xxxxx 树形格式，之前是sqltoy_global_id_tablename_xxx 导致redis里面结构不清晰

# v4.3.4 2019-01-22
* 1、优化缓存检测更新时间，按照:yyyy-MM-dd HH:mm:ss 格式，避免检测存在毫秒级判断误差

# v4.3.3 2019-01-15
* 1、增加从缓存中模糊查询获取sql条件中的key值，直接作为条件参与查询，避免sql中关联模糊查询，从而简化sql并提升查询性能

# v4.2.22 2018-12-16
* 1、filter equals 判断当 时，当参数是日期类型对比异常，增加容错性。
* 2、将中文金额的圆统一成元，2004年后国家统一标准
* 3、修复缓存翻译定时检测参数处理问题

# v4.2.20 2018-11-27
* 1、mysql8 锁记录语法优化为 for update [of table] skip locked
* 2、对代码进行注释加强

# v4.2.17 2018-11-10
* 1、sql剔除末尾的分号和逗号，增强容错性，分号开发者会经常从客户端copy过来容易忘记剔除
* 2、在mysql中带有sum等统计函数的查询，结果集存在全是nulll的情况(一般结果就一条)，sqltoy通过sql中增加注释--#ignore_all_null_set# 方式告诉框架进行剔除，避免无效记录

# v4.2.16 2018-10-18
* 1、修复当设置缓存翻译配置文件为空白时，文件加载错误的bug
* 2、优化部分代码性能和规范一些正则表达式的统一定义

# v4.2.15 2018-9-30
* 1、修复不使用缓存翻译时，未对文件是否存在进行判断。
* 2、修复sql语句中进行注释时，先剔除-- xx -- 行注释，导致注释模式被剔除中间部分导致失效

# v4.2.14 2018-9-17
* 1、支持多个sql配置路径

```xml
<property name="sqlResourcesDir" value="classpath:com/sagframe;classpath:sqltoy" />
```

# v4.2.13 2018-9-6
* 1、修复业务主键跟数据库主键属于同一个字段时的长度控制问题
* 2、修复业务主键跟数据库主键同一个字段批量保存时的生成参数未指定正确问题

# v4.2.10 2018-8-24
* 1、修复分页查询组合count sql时：select a,from_days() days from table 取from位置bug

# v4.2.8 2018-6-5
* 1、增加日期格式化和数字格式化

```xml
<sql id="companyTrans">
    <!-- 敏感数据安全脱敏 -->
	<secure-mask columns="account_code" type="public-account"/>
	<secure-mask columns="link_tel" type="tel"/>
	<!-- 日期格式化 -->
	<date-format columns="trans_date" format="yyyy-MM-dd"/>
	<!-- 数字格式化,分:#,###.00 、capital、capital-rmb 等形式 -->
	<number-format columns="total_amt" format="capital-rmb"/>
</sql>
```

# v4.2.7 2018-5-28
* 1、业务主键策略可以根据多个字段组合形成。quickvo业务主键配置：  1),signature 增加${}引用related-columns 设置相关的列的值，@case() 进行类似oracle的decode函数处理，@df(${xxx},fmt) 对日期进行格式化，第一个参数缺省表示当天，第二个参数缺省为:yyMMdd。
    2)，related-columns可以维护多个数据库字段，用逗号分隔。

```xml
   <business-primary-key >
       <table name="OD_CONTRACT_INFO" column="CONTRACT_ID" 
          signature="${periodType}@case(${orderType},P,PO,S,SO,BN)${tradeType}@df(yyMMdd)" 
         related-columns="periodType,orderType,tradeType" length="12" generator="redis" />
   </business-primary-key>
```
* 2、缓存翻译可以一组代码进行同时翻译。如:某个字段结构是A,B,C这种格式，翻译结果为:A名称,B名称,C名称：

```xml
     <translate cache="dictKeyNameCache" columns="SEX_TYPE" split-regex="," link-sign=","/>
```

# v4.2.6 2018-5-19 
* 1、修复mysql、postgresql 执行saveOrUpdate时报：发生SQL 错误 [1048] [23000]: Column 'NAME' cannot be null

# v4.2.5 2018-5-12
* 1、修复mysql8.0 树形表设置节点路径时报sql错误的问题。

# v4.2.4 2018-5-3
* 1、修复@if（:param=='value' && :param1=='-1'）带单双引号后面紧跟+_符号的逻辑处理。
* 2、优化原生elasticsearch json语法解析错误提醒。
* 3、修复 elastic suggest 场景查询无法处理的问题
* 4、修复分页查询count语句优化处理时，在select from 之间有order by语句时处理异常问题。

# v4.2.3 2018-4-12
* 1、优化pom依赖,避免每次依赖oracle和其它一下特定需求情况下的依赖。
* 2、优化查询传参数验证提醒。
* 3、优化分页查询取count记录数时sql判断order by 并剔除的判断逻辑，确保剔除的精准。

# v4.2.2 2018-3-31
* 1、缓存翻译全部改为ehcache3.5.2版本，无需再定义cacheManager和ehcache.xml等，大幅减少配置。
* 2、缓存翻译采用了新的xml schema，支持sql、rest、service等策略。
* 3、缓存翻译增加了主动侦测数据是否发生变化，然后清空缓存的功能，且配置灵活，支持不同时间不同频率。
## bug修复：
* 1、sql语句@if(a== 'xxx' )逻辑判断，等号后面对比数据有空格时判断错误问题。
* 2、修复elasticsearch Sql查询时select count(*) count from xxxxx 没有group 时没有判断为聚合查询的bug。

# v4.1.0 2018-2-20
* 1、正式支持elasticsearch（两种模式:1、通过elasticsearch-sql模式和json原生模式）,已经经过项目应用。
* 2、正式支持redis集中式主键策略，已经正式项目应用通过。
* 3、正式支持redis缓存翻译，已经可以同时支持ehcache和redis
* 4、修复sql参数过滤的一个bug，将默认blank处理作为第一处理顺序。
* 5、对schema xsd文件进行了调整优化
* 6、quickvo 支持swagger api

# v4.0.9 2018-2-3
* 1、支持elasticsearch以及elasticsearch-sql插件

# v3.2.2 (2017-2-28)
* 1、优化取总记录数查询sql分析,排除统计性查询用分页形式查询产生的记录数量错误
* 2、更新依赖包

# v3.2.1 2016-12-1
* 1、修复pivot行转列的参照category列排序问题

# v3.2版本(2016年11月25日发布)
* 1、增加unpivot 列转行功能
* 2、修改了存储过程调用模式，剔除掉存储过程分页查询，修复oracle存储过程返回结果的执行错误
* 3、删除StoreUtils类
* 4、sql语句中增加#[@blank(:paramNamed) sql] 控制特性，便于组织sql
* 5、增加分页优化功能，避免每次都查询2次，在查询条件一致的情况下不再查询分页总记录数

```xml
<page-optimize alive-max="100" alive-seconds="900"/>
```
# v5.6.94 2026-09-02
1、优化BeanUtil.reflectBeansToList、reflectBeanToAry方法对Map类型数据的属性值提取：优先通过containsKey按key精确取值(尊重Map自身实现的get语义，如自定义归一化键的Map子类FieldKeyMap等)，未命中时保持原有大小写兼容遍历逻辑
2、精确取值为O(1)快速路径，避免批量场景下每个属性都O(n)遍历entrySet比对，同时保持行为完全向后兼容
4、lambda体系运行时输出消息(异常信息)全部改为英文,覆盖条件引擎、lambdaQuery/lambdaUpdate/lambdaDelete执行桥、join上下文的全部DataAccessException消息;javadoc与代码注释保持中文不变
5、lambda条件体系的动态化分工定版:IfPresent语法糖(eqIfPresent/likeIfPresent/inIfPresent/betweenIfPresent等)为lambda层推荐动态化惯用法,辅以条件方法的condition开关与accept/nested组合;复杂动态场景(多分支条件组合、方言差异、配置化报表sql)指引走sqltoy的SQL模式(sqlId加#[...]动态段与@if宏,SqlScriptLoader管理、支持方言变体与热更新),lambda层不复制字符串条件DSL;分工写入org.sagacity.sqltoy.lambda包说明与LambdaQuery类注释;完整能力文档落地sqltoy-docs仓库query/lambda_query.md(入口/条件全集/动态化/单表更新删除/join查询更新删除/参数约定/错误语义/sqltoy-plus迁移对照/能力边界十节)并登记侧边栏;findTop/findRandom仅保留构建器终端;双通道路由统一收进构建器终端(find/getOne/findPage/count/exists/update/delete终端内部完成assemble与通道判定),LightDao的执行类default方法全部退化为一行bind适配器(find/getOne/findPage/count/exists(LambdaQuery)与lambdaUpdate/lambdaDelete),dao层lambda面仅三个工厂(lambdaQuery/lambdaUpdate/lambdaDelete),执行全部由构建器终端完成(路由实现点唯一,终端内assemble+isJoinMode+dao公开原语);未绑定构建器经bind(dao)后执行;查询终端命名与link.Query同名同语义:find(列表)/getOne(严格单条,多条抛异常,与link.Query.getOne既有严格语义一致,故不采用findList/findOne新命名)/findPage/findTop/findRandom/count/exists
6、lambda条件体系两处执行期缺陷修正与全功能覆盖测试:inb多字段元组in此前生成(a,b) IN (:p1,:p2)并把两个List绑到两个命名参数上,内核参数绑定会把List当单值setObject导致执行必失败(sqltoy-plus时代仅测sql文本、从未执行验证),现按行展开占位符(a,b) IN ((:p1,:p2),(:p3,:p4))、每个元组值独立绑定命名参数,元组长度与字段数不一致时装配期快速失败;likeLeft/likeRight的sql模板与名称语义相反(移植自sqltoy-plus,LIKE_LEFT_SQL实为"值%"开头形态),修正为likeLeft='%值'(以值结尾)、likeRight='值%'(以值开头),与接口注释及文档语义对齐;新增LambdaFullCoverageH2Test(25例,独立数据集ff_user/ff_dept,id>=90专供变更类用例、读取类以lt(id,90)隔离)执行级覆盖:全部条件族(比较/模糊/区间/集合/inSql/inb/判空)、逻辑组合(or/and嵌套/not嵌套,含NULL三值逻辑行排除)、last/accept、IfPresent语法糖、实例级valueFilter、select与orderBy、查询终端(find/getOne严格语义/findPage/findTop整数与比例/findRandom/count/exists)、更新(set多字段与condition开关、setExpr字面量/?占位/:name三形态、无条件守卫与matchAll、无命名参数DEFAULT_VALUE兜底)、删除(条件删除与守卫)、bind执行、join缺省select/selectAll+ExcludeCols+JoinCol别名映射VO/groupBy+having+count聚合映射、多on等值关联与嵌套条件、join查询终端、join更新删除MySQL方言sql文本与守卫、错误语义(未参与join实体/缺on/setExpr含=/ExcludeCols实体错配/selectAll全排除/inb元组长度);lambda测试合计91例
7、分组聚合与去重能力定版:JoinCol提供count/sum/avg/min/max聚合列工厂(配合groupBy/having,单表分组聚合无需join——JoinCol将查询切到SQL通道并以from(主表)指定聚合对象,join场景写法相同);去重提供查询修饰符distinct()(单表通道转EntityQuery.distinct()、join通道渲染SELECT DISTINCT),并删除移植自sqltoy-plus的JoinCol.distinct列函数形态——DISTINCT(col)函数写法在H2/Oracle/PG等库不合法(DISTINCT是量词不是函数),属移植地雷;聚合与去重在LambdaFullCoverageH2Test中以H2执行级验证(sum/avg/min/max/count分组值、having过滤、null分组键参与聚合、双通道distinct去重行数)
8、lambda能力补强两项与方言边界注明:orderByAsc(String)/orderByDesc(String)支持按select别名排序(分组聚合结果按聚合列排序的刚需,仅join通道有效,单表通道装配期抛异常,别名做标识符白名单校验防注入);LambdaUpdate.setNull(column)显式置null(默认值过滤跳过set(col,null),置null诉求此前只能绕valueFilter或走lightDao.update(entity,forceProps));文档能力边界注明inb行值IN在SQLServer不支持、like的CONCAT写法在SQLite<3.44依赖函数适配层且未真实库验证;LambdaFullCoverageH2Test扩至30例(setNull双通道、别名排序执行与守卫、join setNull sql文本),lambda测试合计96例;lambda toolkit的ClassUtils类加载链能力并入core的BeanUtil.loadClass(className,classLoader)(优先classloader→线程上下文→BeanUtil自身→系统四级兜底,失败抛DataAccessException),ReflectLambdaMeta/ShadowLambdaMeta改调BeanUtil,删除lambda私有ClassUtils——类加载属通用能力不应在lambda包私藏;sqltoy-verify门禁工程新增LambdaVerifyTest多库执行级门禁(like CONCAT适配/inb行值IN的SQLServer已知限制跳过/join多表DML仅mysql系/setExpr与setNull全profile,探针表sqltoy_verify_lu/ld自动建删,h2首跑9例全过,多库扫描待容器在线)
9、PropertyNamer并入BeanUtil(methodToProperty/isGetter/isSetter/isProperty,源头MyBatis的org.apache.ibatis.reflection.PropertyNamer经MyBatis-Plus同形转载、sqltoy-plus移植,溯源注释随方法迁入BeanUtil),lambda toolkit私有PropertyNamer删除,LambdaUtils改调BeanUtil;顺带统一了plugins/EntityResultSetProxy的getter名内联截取(substring(3/2)无首字母小写处理,靠getFieldMeta内部lowercase掩盖差异,与methodToProperty语义等价故安全替换);BeanUtil:2535的getter/setter属性名列表构建(replaceFirst加firstToLowerCase)与methodToProperty在getURL这类连续大写属性上语义不同(前者得uRL后者保留URL),替换会改变既有映射行为,故意不统一并留注释说明
10、补齐sqltoy-plus最后一项能力净丢失:selectAll(Class)将某个被join实体的全部字段以"别名.列 AS 属性"并入select(对应sqltoy-plus的select(mappingClass,...)),与selectAll()/select(JoinCol...)可叠加、与主表重复的实体自动跳过避免重复列、实体未参与join装配期快速失败;至此sqltoy-plus功能面除有意不迁项(字符串wrapper全家/update(entity|Map,wrapper)/oneOpt/BatchValueOperation)外无净丢失;lambda测试合计97例
11、lambda体系防御性审计加固四处:join自关联守卫(同一实体二次注册抛异常,sqltoy-plus原有守卫在重构中丢失后补回);select别名查重双模式(用户显式别名JoinCol.as/属性select与任何别名重复抛异常避免结果映射静默错值,框架dump之间同名列按声明顺序主表优先跳过使selectAll()+selectAll(Class)组合可用);having/inSql外部参数名禁占内部p加数字命名空间(调用时校验,规避参数合并先赢导致的静默错绑);join通道下结果类型未参与join时属性形式select渲染出null.列坏SQL的守卫(快速失败);文档新增"安全边界与使用约定"章节(原始SQL逃逸口不做注入筛查禁拼终端用户输入/构建器单次使用非线程安全/主键可显式set属意图不拦截/别名与参数命名空间规则),package-info补充单次使用声明;LambdaFullCoverageH2Test扩至32例含防卫与组合场景,lambda测试合计99例
12、对照外部sqltoy-plus缺陷审计逐条核对,我方命中六处并修复:orderBy多列时merge破坏性改写导致二次读取getSqlSegment越界(加幂等标志);last()在无任何条件时被静默丢弃(join通道现以1=1承载,单表通道EntityQuery where无法承载尾部原始sql改为响亮失败并指引findTop/findPage);空not()留下悬挂NOT(尾部NOT剔除,首部NOT合法保留);两个inSql/having共用命名参数且值不同被静默覆盖(SegmentList/MergeSegments参数合并改为同名异值响亮失败、同值共享允许,并顺带修复Collectors.toMap遇null参数值NPE);IdeaProxyLambdaMeta静态块窄catch在JDK模块强封装下抛ExceptionInInitializerError(改catch Throwable降级+构造期明确指引+extract代理路径双回退失败转DataAccessException);其余审计项核对结论:链式ClassCastException/Spring重复装配/静态元数据缓存/实体并入死代码随胶水删除不适用,likeLeft语义反转/inb行值元组/空集合IN/accept CME/无条件删除/缺on校验/lambda缓存此前已修,orderBy变参忽略condition随变参重载删除不适用,having(null)我方本就允许,betweenIfPresent短数组我方有守卫;lambda测试合计105例
13、独立交叉审查复核lambda引擎,再修九处:not()紧跟普通条件缺自动AND生成非法SQL(a NOT (b)改为a AND NOT (b),空not()悬挂NOT剔除且循环清理至稳定,首部NOT合法保留);删除通道where无命名参数时deleteByQuery空值拒绝(matchAll与isNull类条件删除不可用)补DEFAULT_VALUE兜底与更新同口径;列名解析无null守卫(非@Column属性生成null=:p1静默错果)单表与join双通道装配期抛异常;selectAll/ExcludeCols在单表通道被静默忽略(ExcludeCols转EntityQuery.unselect生效,selectAll无参单表本就等价缺省);assemble非幂等(重复终端调用plainSelectColumns重复注册抛别名重复)加去重;orderByAlias调用期直接append导致别名排序恒排lambda排序之前改为延迟装配保持调用顺序;join通道表名丢schema(getTableName改getSchemaTable(null,null)与实体通道同口径,保留字加引仍为已知限制记入文档);AbstractWhere.getSql单表模式误抛join未装配(按isJoinChannel判定);更新/删除构建器可groupBy/having/orderBy生成非法SQL(装配期守卫);condition=false仍解析列名(groupBy/orderBy/inb的resolve移入maybeDo);装配后仍可变更join结构绕过缺on校验(JoinContext装配标记+内部缺省主表注册豁免);另有清理:SegmentList/MergeSegments参数合并null安全与同名异值响亮失败、LambdaUpdate装配顺序使set字段join校验生效、工厂null入参守卫、select空元素守卫、lambda解析缓存8192上限、SqlCompare.LINK_EQ与MatchSegment/SegmentMeta/SqlKeyword死代码及paramSeq死分支清除、同包冗余导入与FQN清理、javadoc失实修正;lambda测试合计115例
14、SqlKeyword关键字单一事实源:复核发现此前清理删除的FROM/LEFT_JOIN/RIGHT_JOIN/INNER_JOIN/WHERE/ON/SELECT/DELETE/UPDATE/SET十个常量并非用不到,而是JoinContext以字符串字面量绕过了枚举(清理时只查了代码引用、未查语义用途,属过度删除);现恢复常量并让JoinContext的join类型与SELECT/UPDATE/DELETE/SET/FROM/WHERE/ON/AND拼装统一走SqlKeyword枚举,三个构建器的leftJoin/rightJoin/innerJoin改传枚举;EXISTS/NOT_EXISTS经核实确无功能对应(lambda层exists是终端方法走count>0,不存在exists条件),保持删除;115例SQL文本锁定测试全绿证明重构行为中性
15、exists/notExists关联子查询(lambda形态,类型安全):exists(子查询实体, sub -> sub.eq(子表字段, OuterRef.of(外层字段)))生成EXISTS (SELECT 1 FROM 子表 sq1 WHERE ...),OuterRef仅支持eq/ne/gt/ge/lt/le六个比较方法且在子查询外使用抛异常;单表通道外层列渲染为表名.列、join通道渲染为别名.列(子查询内必须限定避免同名列歧义);子查询别名sq前缀与外层单字母别名隔离、嵌套exists多层支持;子查询参数与外层共享命名参数序号;子查询实体与条件字段实体不一致装配期抛异常;SqlKeyword恢复EXISTS/NOT_EXISTS常量;终端exists()改为findTop(1)命中即停(不再全量count聚合);LambdaFullCoverageH2Test扩至42例含单表/join关联子查询执行与三项误用守卫,lambda测试合计117例
16、join更新/删除去MySQL方言化,改为全关系库可移植的EXISTS-over-derived改写:UPDATE 主表 SET 列=:p WHERE EXISTS(SELECT 1 FROM (SELECT 主表别名.主键 AS LPK1 FROM 主表 别名 JOIN... WHERE 条件) sqd WHERE sqd.LPK1=主表.主键),DELETE同构;派生表保留join(含LEFT JOIN)完整语义,覆盖Oracle等无多表DML语法的库;要求目标实体有@Id(无主键快速失败),UPDATE的SET列不带别名(setExpr表达式列写主表实际列名);多删除目标逐表生成删除语句并经DBTransUtils置于同一事务保证原子性;inb元组IN同步改为全库可移植的OR-of-ANDs展开(单列退化为普通IN),规避行值IN在SQLServer/DB2的方言差异;sqltoy-verify门禁数据集改ASCII字面量(规避sqlserver默认排序规则对中文varchar的转码陷阱),join更新删除用例从仅mysql系放开到全部关系库profile(仅clickhouse/doris/starrocks等无事务性行级DML的OLAP库按数据库自身边界跳过)
17、lambda like族跨库缺陷修复与全库门禁收口(sqltoy-verify扩至全部在线关系库+OLAP库):like/likeLeft/likeRight/notLike原用数据库无关CONCAT模板(如likeRight=CONCAT(:p,'%')),但sqltoy中央解析链SqlConfigParseUtils.processLike对"LIKE ?"形态参数值会自动补%(值不含%时包成%值%);sqlite等库把CONCAT改写为||后likeRight变成"LIKE ?||'%'"——?紧跟LIKE触发processLike把裸值'li'包成'%li%',最终'%li%'||'%'='%li%%'使前缀匹配退化为包含匹配(sqlite实测likeRight('li')误命中lisi+zhaoliu两条);like/likeLeft因?不在LIKE紧邻位侥幸未触发但语义同样依赖运气。修复:like族弃用CONCAT改纯"字段 LIKE :param"/"NOT LIKE :param",通配符由lambda层按方向补入参数值(like/notLike=%值%、likeLeft=%值、likeRight=值%,等价sqltoy原生ParamsFilter.llike()/rlike()语义),processLike识别"值已含%"只做_和\转义不再二次包装——数据库无关、单表与join两通道统一流经中央解析链均获转义;OuterRef(外层列引用,内联非字面量)不补通配符。门禁基建可移植化:探针表删表改dropQuietly(先drop if exists、不支持IF EXISTS的库如HANA回退plain drop并忽略表不存在);建表按库族自适应(clickhouse列默认非空而数据含null且断言依赖null→显式Nullable+MergeTree引擎;doris/starrocks不支持列内联primary key→ENGINE=OLAP+DUPLICATE KEY+DISTRIBUTED BY HASH分桶);DUPLICATE KEY模型OLAP库不支持单表UPDATE→setExpr用例按NO_UPDATE(doris/starrocks)跳过(clickhouse支持轻量UPDATE+Nullable列可正常跑)。门禁矩阵:关系库opengauss/og7/gaussdb/kingbase/highgo/vastbase/dm/tidb/sqlite/h2/mysql9/sqlserver2025/pgpostgis/oracle/db2/hana+OLAP三库clickhouse/doris/starrocks全部10/0/0(OLAP按DB边界skip join DML与单表update);oceanbase因容器obd部署IP冻结(须172.17.0.2、重启落到172.17.0.3集群无法成形)连接失败属基建问题非lambda缺陷(OB配mysql方言、SQL生成与已全绿的mysql一致);db2-12(50001)无容器。core lambda单测142例全绿。教训:数据库无关写法不等于跨库安全——CONCAT本身可移植,但与内核processLike的"LIKE ?自动补%"约定在CONCAT被改写为||的库上冲突,跨库门禁必须真实执行而非仅SQL文本断言(sqlite likeRight正是文本断言永远抓不到的执行期语义偏差)
18、lambda构建器支持显式指定数据源.dataSource(DataSource)(多数据源/读写分离场景):LambdaQuery/LambdaUpdate/LambdaDelete经共用基座AbstractWhere新增流式dataSource(ds)与getDataSource(),与sqltoy既有惯例完全一致(EntityQuery/QueryExecutor/lightDao.update().dataSource(xxx)同款);单表通道由LambdaConvert把ds透传到EntityQuery/EntityUpdate——findEntityBase/updateByQuery/deleteByQuery本就以getDataSource(innerModel.dataSource)解析数据源,故单表查询/count/更新/删除零执行链改动即支持;join通道全部改走sqltoy既有的query()/execute()链式构建器(原生sql+命名参数,二者本就提供.dataSource(ds)且内部即new QueryExecutor(sql,params),与既有find(sql,map,resultType)/executeSql(sql,map)同源,故ds为null时行为逐字节不变):查询/count经query().dataSource(ds).sql(..).entity(IgnoreKeyCaseMap).resultType(..)的find/findPage/findTop/findRandom/count,更新/删除经execute().dataSource(ds).sql(..).entity(..).submit(),多删除目标事务的数据源同步取builder显式ds(缺省回退dao.getDataSource());**LightDao接口与SqlToyDaoSupport零新增/零改动**(初版曾加getCountByQuery(QueryExecutor)与executeSql(String,Map,DataSource)两方法,经复核query()/execute()构建器已完整覆盖ds感知原生sql的查询/count/DML,遂删除以守API极简——能力全部内聚在lambda层);新增LambdaDataSourceH2Test(6例,两个独立H2内存库装不同数据,验证单表与join两通道的查询/count/更新/删除全部路由到.dataSource指定的库且默认库不受影响),lambda测试合计149例全绿;全量core测试失败集合与基线一致(仅本机无真实库的RealDbSmokeTest环境性失败)
19、lambdaQuery支持分页优化.pageOptimize(PageOptimize)(仅findPage生效,复用sqltoy既有PageOptimize、不在lambda重造旋钮):parallel(true)令count与数据查询并行、aliveSeconds(n)把count结果缓存n秒(keep-alive,翻页命中缓存跳过count),另有aliveMax/skipZeroCount/parallelMaxWaitSeconds;不设置时回退全局SqlToyConfig.pageOptimize。透传:单表通道经LambdaConvert把po落到EntityQuery(findEntityBase第2247行本就把innerModel.pageOptimize透传到内部QueryExecutor);join通道findPage由条目18的query()构建器改走findPageByQuery(page,QueryExecutor.pageOptimize(po))——因link Query构建器不支持pageOptimize而QueryExecutor支持(且QueryExecutor同时携带dataSource,与条目18的join数据源透传一致)。**单表与join的findPage保持双分支不合并**(作者曾提议"底层都走findPageByQuery(QueryExecutor)不分单表多表",经核实否决):单表必须经findPageEntity→findEntityBase以继承分库分表(setEntitySharding/dbSharding/tableSharding,SqlToyDaoSupport:2256-2268)、缓存翻译、加密脱敏、租户过滤,并由EntityMeta生成select列与表名(lambda单表只交where/参数/select字段名);findPageByQuery(QueryExecutor)直进dialectFactory.findPage会跳过整个实体层,合并将丢实体级增强——这正是"单表通道继承实体级增强、join不继承"的固有设计。LightDao/SqlToyDaoSupport零改动(pageOptimize是EntityQuery/QueryExecutor既有能力、findPageByQuery既有public方法)。测试:LambdaQuerySqlTest.testPageOptimizeThreading断言po透传到EntityQuery且未设置时为null;LambdaFullCoverageH2Test.testPageOptimize以H2执行验证单表与join的findPage在parallel+aliveSeconds下分页正确(recordCount/页行数/总页数),lambda测试合计154例全绿
20、select支持原生SQL片段列JoinCol.raw(String)(补类型化select的盲区:窗口函数ROW_NUMBER() OVER(..)、数据库特有函数、CASE WHEN、算术表达式等):新增JoinCol.raw(sqlFragment)工厂(rawSql字段,column/function为null),复用既有select(JoinCol...)入口与.as(别名)——不新增selectRaw方法、不新增select重载,类型列/聚合列/原生片段列统一走一个select(JoinCol...)(作者"怎么优雅你来定夺",取最简最一致形态);渲染时JoinContext.buildSelectColumn对raw列原样输出片段(别名经as()追加或片段内联"AS rn"),装配校验跳过raw列的"所属实体须参与join"(raw无所属实体);raw列同JoinCol一样触发join/SQL通道(hasSelectColumns),结果按别名映射到含该字段的VO;空白片段快速失败。安全:与last(sql)同为原始SQL逃逸口,原样拼接不做注入筛查、禁拼终端用户输入,片段内引用真实库列名/表别名(单表可非限定、多表用自动别名如j1),跨库注意片段自身方言可移植性。测试:LambdaQueryJoinH2Test.testSelectRawSqlText锁定sql文本(SELECT j1.STAFF_NAME AS staffName, ROW_NUMBER() OVER(ORDER BY j1.ID) AS rn FROM ...)、testSelectRawExecution以H2真实执行窗口函数ROW_NUMBER映射到VO字段并与类型化列共存,lambda测试合计156例全绿
21、lambda组合查询落地(CTE/@fast分页下推/UNION,作者"一次搞定"):①**CTE**——`.with(name, 内层lambdaQuery)[,materialized]`/`.withRecursive(name,q)`注册CTE、`.fromCte(name)`外层FROM引用(旁路实体表名,JoinContext.fromRawSource覆盖tableName,列解析走外层VO的EntityMeta);多CTE逗号串联、CTE可引用在先CTE;②**@fast分页下推**——`.fromFast(inner,alias)`渲染`FROM @fast(内层sql) alias`(内层先分页再被外层join,复用sqltoy既有@fast机制:分页下推+count用内层简化SQL),可嵌进CTE体;`.countSql(sql)`提供比@fast内层更精简的手写count(仅join通道findPage,经QueryExecutor.countSql);③**UNION/UNION ALL**——`.union(q)`/`.unionAll(q)`渲染`本sql UNION [ALL] 其他sql`(分支不加括号:SQLServer T-SQL不允许set操作数带括号,mysql/PG虽容忍但为跨库统一不加;整体被sqltoy包派生表做count/分页的外层括号由各库分页机制负责),find/count/findPage/findTop/findRandom/exists全终端支持(包裹子查询分页/count由sqltoy既有机制完成),UNION ALL的count可复用union-all-count优化。**跨builder参数续号**:组合装配时先按序装配各内层(CTE/@fast/union,各自独立paramSeq)并把外层paramSeq续到内层max(同exists子查询共享取号机制),参数名全局唯一不撞号、无需重写SQL。**WITH方言适配**(按执行期dbType):WITH恒位于语句位置0——sqltoy靠SqlWithAnalysis/hasWith从文本检测WITH以在count/分页时剥离到包裹外,前导分号会破坏该检测(初版曾给SQLServer加`;WITH`前缀,sqlserver门禁CTE count报Incorrect syntax near ')'后移除;SQLServer单语句以WITH开头本无需前置分号,分号要求仅针对WITH前尚有未结束语句的批处理);Oracle/SQLServer递归不带RECURSIVE关键字、PG经materialized参数渲染AS MATERIALIZED/NOT MATERIALIZED(其他库自动忽略,作者"按数据库类型处理、不支持的不生效");不支持CTE的老库不做降级由开发者自行改写(作者"不支持的不管")。实现:JoinContext加fromRawSource+forceJoinChannel(isJoinMode统一为composed||joinContext.isJoinChannel(),修复fromCte/fromFast内层未进join通道致getSql仅where片段的缺陷);LightDao/SqlToyDaoSupport零改动。测试LambdaCompositionH2Test(7例:CTE sql文本+H2执行、多CTE串联、fromFast的@fast派生表、UNION ALL执行、UNION的count+findPage、UNION/CTE的findTop/findRandom/exists全终端),lambda测试合计163例全绿;sqltoy-verify门禁新增compositionCteUnionFastOnRealDb(CTE count/UNION ALL count/@fast findPage)在mysql/postgresql/sqlserver真实库全过(顺带修正verify的sqltoy-starter.version 6.0.7→6.0.8以解析到含组合特性的core)。补强(用户"请对新增的cte、@fast进行测试"):withRecursive增加列清单参数`.withRecursive(name,q,"id","pid","name")`→`WITH RECURSIVE t(id,pid,name) AS`(H2等库递归CTE强制列清单,PG/MySQL/SQLServer标准支持);LambdaCompositionH2Test扩至15例(增CTE findPage、CTE join实表、CTE引CTE执行、递归CTE组织树遍历、@fast findPage执行、@fast count、@fast join实表、countSql以select 99证明recordCount取自自定义count),lambda合计171例全绿;门禁增recursiveCteAndCountSqlOnRealDb(递归CTE+countSql)在mysql/postgresql/sqlserver真实库全过(验证RECURSIVE关键字按dbType适配:mysql/PG带、sqlserver省略)。递归CTE实测踩坑:递归分支须innerJoin且关联方向为"子.pid=当前节点.id"向下遍历(LEFT JOIN无子节点也产行致递归不终止OOM);CTE输出列名须与下游VO的@Column对齐(select(JoinCol.raw("*"))输出原始列名DEPT_ID,与VO属性别名DEPTID不匹配会报column not found)
22、原生byte/short空串与null赋值的装箱类型修复:setProperty等反射赋值链路中convertType的空串分支返回Integer装箱的0(byte分支为条目7③归一改造时引入、short空串分支为既有同病),Method.invoke/Field.set不允许Integer窄化为byte/short形参,导致空串赋值抛IllegalArgumentException("空串返回0"目标未达成);null分支的1~5原生数字区间判断同样命中short(primitiveShortType=3)返回Integer同样失败;修复为空串返回(byte)0/(short)0、null分支short单独返回(short)0(int/long/float/double返回Integer经反射宽化转换合法,行为不变);新增BeanUtilPrimitiveBlankAssignTest(6例:byte/short空串与纯空白经真实setter赋0、null赋0、正常值与千分位转换不受影响、越界仍响亮报错、包装类型空串仍转null、宽化类型空串回归)
23、低危竞态与隔离遗漏收口批次:①FIFODynamicFetchCacheManager过期清理与clear的删除顺序竞态——原先删数据后删过期登记,两步之间并发getDynamicCache重建缓存写入的新登记会被误删,致该缓存keepAlive静默失效永不过期;改为先条件删除登记(remove(key,value)按Long[]身份比对,并发重建的新登记不命中即跳过数据删除)再删数据,clear同口径先清登记再清数据;②TranslateCaffeineManager(spring与spring-starter两份)destroy补清cacheNameSet——原先destroy只置空cacheManager,同实例init重建后put因cacheNameSet残留跳过registerCustomCache,getCache按默认规格动态建缓存,keepAlive/heap配置静默丢失(无过期无容量上限);③SqlToyContext.initialize失败回滚首占权——认领记录新增实例级claimedStaticStateKeys,initialize中途异常(如非法注入正则Pattern.compile失败)时释放本次认领的全部key,修复失败context永久锁死JVM级静态配置、同JVM后续上下文(测试套件/热部署重试)携带正确配置也无法生效的问题(含安全攸关的sqlInjectionRegexes),成功初始化的首占语义不变;④solon显式targetPoolName=none语义对齐spring starter——属性默认值由none改为未配置(null),未配置/空仍按参数构建默认线程池(与spring未配置建池一致),显式none改为不建池、回落core的commonPool(与spring显式none不建池一致),修复此前注释声称对齐但显式none分叉的问题;新增SqlToyContextInitFailureRollbackTest(2例:失败释放后后续context注入正则生效、成功首占不释放)、TranslateCaffeineManagerScopeTest扩充rebuildKeepsCustomCacheSpecs(destroy+init重建后heap/keepAlive规格仍生效)、SqlToyContextBuilderParityTest调整(未配置建池、显式none不建池)
24、like转义链语义收口与elastic路径防重:①processLike判定"值是否已含未转义%"由replace("\\%","")改为反斜杠奇偶扫描——\\%中的%是未转义通配符(前导\\为字面反斜杠),旧实现误剥离致like过滤器right模式对尾部\的值先转义为\\再补%后被误判"无通配符",补的%被当字面量转义、前缀匹配退化为字面量C:\%的全模糊(新<like>与旧r-like同病);②lambda的wrapLike/exactLike对齐过滤器"先转义值最后补%"同序缺陷修复——wrapLike对不含%的值先转义反斜杠再补%(含%早退的调用方自带通配符契约不变,_仍留中央解析链统一转义),exactLike先转义反斜杠再转义%(修复a\%b被误处理为字面\+通配%),消除like/notLike/likeRight/likeExact*尾部\把补的%转义为字面量的同类缺陷(单表与join双通道共用AbstractWhere同获修复);③elastic的buildRequestPath防index重复拼接——url本身指向搜索端点(http://host:9200/idx/_search)时pathPrefix已含index,跳过拼接避免/idx/idx/_search(大小写不敏感,不同index与网关子路径不受影响)。核实撤销项:OCEANBASE保留在反斜杠转义集(项目实测OB为MySQL租户,ReservedWordsUtil反引号归组/SqlUtil geometry分支为证,OracleDialect继承仅为分页merge复用;Oracle租户可经SqlToyConstants.backslashEscaping全局开关覆盖);JwtTokenManager缓存key不补password/expire维度(token按用户身份生效,同tokenUrl+username的token跨密码有效,属过度处理)。测试:SqlConfigParseComboEdgeTest增奇偶判定3例、LikeFilterElementTest增right模式尾部反斜杠H2端到端1例、HttpClientUtilsUriTest增路径防重2例、新增LikeFilterChainParityTest(6例:新like/raw/legacy/l-like/r-like/escapeLike×mode×escape-percent×append-str过滤链→processLike→H2真实执行矩阵,含raw与legacy对原始\%语义分歧锁定)与LambdaLikeBackslashH2Test(10例:独立H2库验证like族尾部反斜杠方向语义与字面%/_);core全量2583例0失败(54跳过为外部库环境)
