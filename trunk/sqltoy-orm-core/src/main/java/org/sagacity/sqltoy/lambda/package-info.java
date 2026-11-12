/**
 * @project sagacity-sqltoy
 * @description lambda条件体系:以实体属性方法引用组织查询/更新/删除条件,单表与多表join统一入口
 *              (不调join步骤即单表,走EntityQuery/EntityUpdate实体链路;调用join即拼装完整sql)。
 *              构建器为单次使用、非线程安全对象(一次组装对应一次执行,并发场景每请求新建);
 *              条件动态化以IfPresent语法糖(eqIfPresent/likeIfPresent/inIfPresent/betweenIfPresent等,
 *              null/空白/空集合自动跳过)为推荐惯用法,辅以各条件方法的condition开关与accept/nested组合;
 *              复杂动态场景(多分支条件组合、方言差异、大段可选条件、报表类配置化sql)应使用sqltoy的SQL模式——
 *              sqlId加sql文本或xml中的#[...]动态段与@if等宏,由SqlScriptLoader统一管理,支持方言变体与热更新;
 *              lambda层不复制字符串条件DSL(单表字符串条件由EntityQuery/EntityUpdate承担)。
 *              <p>
 *              代码溯源(Apache-2.0兼容,均为派生而非复制粘贴):
 *              </p>
 *              <li>直接派生自MyBatis-Plus(保留其版权头):LambdaUtils、SqlKeyword、MatchSegment,
 *              以及lambda元信息四件套SerializedLambda/ReflectLambdaMeta/ShadowLambdaMeta/IdeaProxyLambdaMeta
 *              (sqltoy-plus移植时剥离了版权头,本次恢复);PropertyNamer(源头MyBatis)已于6.0.8并入BeanUtil.methodToProperty;</li>
 *              <li>形状派生自MyBatis-Plus、经sqltoy-plus移植、本次重度改写(仅留来源说明不留版权头):
 *              segments合并引擎族(SegmentList/NormalSegmentList/GroupBySegmentList/HavingSegmentList/
 *              OrderBySegmentList/MergeSegments)、Compare方法族、Aggregate(源自Func)、Logic(源自Join+Nested)、
 *              AbstractWhere条件骨架(源自AbstractWrapper);</li>
 *              <li>源自sqltoy-plus(高总辉,Apache-2.0):CompareEnum的sql模板思路(现为SqlCompare)、IfPresent语法糖、
 *              MultiContext的别名与SQL总装思路(现为JoinContext)、SqlToyPlusDaoImpl的EntityQuery/EntityUpdate桥接路径(现为LambdaConvert);</li>
 *              <li>本次重构原创:按方法泛型推断字段所属实体、单表/join双通道自动路由、JoinCol/ExcludeCols、
 *              setExpr/setNull/distinct/orderByAlias、严格getOne、matchAll无条件守卫、:pN参数命名。
 * @author zhongxuchen
 */
package org.sagacity.sqltoy.lambda;
