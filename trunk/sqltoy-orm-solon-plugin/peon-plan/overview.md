# StringUtil 缺陷分析与修复计划

## 文件
`/sagacity-sqltoy/src/main/java/org/sagacity/sqltoy/utils/StringUtil.java`

---

## 高严重度缺陷

### 缺陷 1: `replaceAllStr` 边界判断 `>=` 应为 `>`，单字符字符串不替换

**位置**: `replaceAllStr(source, template, target, fromIndex, endIndex)` 第 ~410 行

```java
if (realFrom >= realEnd) {   // BUG: 应为 >
    return source;
}
```

**问题**: 当 `realFrom == realEnd`（单字符区间）时直接返回原串，跳过了替换。
`replaceAllStr("a", "a", "b")` → fromIndex=0, endIndex=length-1=0, realFrom=0, realEnd=0, `0 >= 0` → true → 返回 `"a"`（应为 `"b"`）。

**修复**: `realFrom >= realEnd` → `realFrom > realEnd`

---

### 缺陷 2: `matchLastIndex` / `matchCnt` / `replaceRegex` 的 offset 参数可导致死循环

**位置**:
- `matchLastIndex(source, pattern, offset)` — `start = m.end() - offset`
- `matchCnt(source, pattern, offset)` — `start = matcher.end() - offset`
- `replaceRegex(source, pattern, replaceStr, matchCnt, offset)` — `start = end - offset`

**问题**: 当 `offset >= 匹配长度` 时，`start` 回退到上次匹配位置或更早，`find(start)` 重复匹配同一位置 → 无限循环。

**示例**: `matchCnt("aaa", Pattern.compile("aa"), 2)` → 第1次匹配 [0,2], start = 2-2=0; 第2次匹配 [0,2] again, start=0... 永不退出。

**修复**: 在循环开始前确保 `start` 严格前进：
```java
int nextStart = matcher.end() - offset;
start = Math.max(nextStart, matcher.start() + 1);
```

---

### 缺陷 3: 多个方法缺少 null 检查，存在 NPE 风险

| 方法 | 触发条件 | NPE 位置 |
|------|----------|----------|
| `getSymMarkIndex(...)` | source=null | `source.indexOf(...)` |
| `getSymMarkMatchIndex(...)` | source=null | `source.substring(start)` |
| `getSymMarkReverseIndex(...)` | source=null | `source.length()` |
| `clearSymMarkContent(sql, ...)` | sql=null | `new StringBuilder(sql)` |
| `matchIndex(source, Pattern)` | source=null | `pattern.matcher(source)` |
| `matchCnt(source, regex, begin, end)` | source=null | `source.substring(begin, end)` |
| `indexOrder(source, ...)` | source=null | `source.indexOf(regex, begin)` |
| `str2ASCII(str)` | str=null | `str.toCharArray()` |
| `like(source, keywords)` | source=null 或 keywords=null | `source.indexOf(...)` |
| `matches(source, regex)` | regex=null | `Pattern.compile(regex)` |
| `humpFieldNames(labelNames)` | labelNames[i]=null | `labelNames[i].indexOf(":")` |

**修复**: 每个方法入口添加 null 守卫（返回 -1 / null / 空数组 / false 视语义而定）。

---

### 缺陷 4: `loopAppendWithSign` source 为 null 时 NPE

**位置**: 第 ~228 行

```java
return String.join(sign, Collections.nCopies(loopSize, source));
```

**问题**: `String.join` 不接受 null 元素，`loopAppendWithSign(null, ",", 3)` 抛 NPE。

**修复**: source 为 null 时将其替换为空字符串：
```java
String item = (source == null) ? "" : source;
return String.join(sign, Collections.nCopies(loopSize, item));
```

---

### 缺陷 5: `getSymMarkMatchIndex` 未转义正则元字符

**位置**: 第 ~277 行

```java
Pattern startP = Pattern.compile(beginMarkSign);
Pattern endP = Pattern.compile(endMarkSign);
```

**问题**: 当 `beginMarkSign` 含正则元字符（如 `(`、`[`、`{`），`Pattern.compile("(")` 抛 `PatternSyntaxException`。

**修复**: 使用 `Pattern.quote()`：
```java
Pattern startP = Pattern.compile(Pattern.quote(beginMarkSign));
Pattern endP = Pattern.compile(Pattern.quote(endMarkSign));
```

---

## 中严重度缺陷

### 缺陷 6: `splitRegex` 对 `.` 和 `|` 未做字面量处理

**位置**: 第 ~530 行

**问题**: 方法对 `?` `,` `;` `:` 做了字面量转义，但 `.` 和单 `|` 没有。
- `splitRegex("1.2.3", ".", false)` → 按任意字符切分，返回 `["","","","","","",""]`（应按 "." 切分）
- `splitRegex("a|b", "|", false)` → 按空交替切分，返回 `["a","|","b"]`（应返回 `["a","b"]`）

**修复**: 在 switch 链中增加 `.` 和 `|` 的字面量处理，或将 `default` 分支改为 `source.split(Pattern.quote(regex))`（如果意图是纯字面量切分）。

---

### 缺陷 7: `secureMask` 缺少负数参数校验

**位置**: 第 ~362 行

**问题**: `preLength` 或 `tailLength` 为负数时，`tmp.substring(0, preLength)` 抛 `StringIndexOutOfBoundsException`。

**修复**: 入口处将负数归零：
```java
preLength = Math.max(0, preLength);
tailLength = Math.max(0, tailLength);
```

---

### 缺陷 8: `replaceFirstStr` replacement 为 null 时产生字面量 "null"

**位置**: 第 ~440 行

```java
return source.substring(0, idx) + replacement + source.substring(idx + target.length());
```

**问题**: Java 字符串拼接 `"" + null` 产生 `"null"` 文本。调用方传 null 替换串可能预期删除而非插入 "null"。

**修复**: 显式处理：
```java
String rep = (replacement == null) ? "" : replacement;
return source.substring(0, idx) + rep + source.substring(idx + target.length());
```

---

## 低严重度 / 代码质量

### 缺陷 9: 静态 Pattern 未声明 `final`
`chinaPattern`、`quotaPattern` 等 5 个字段缺少 `final` 修饰符，虽不会被重新赋值但不符合不可变约定。

### 缺陷 10: `splitExcludeSymMark` 使用原始类型 `ArrayList`
```java
ArrayList splitResults = new ArrayList();  // 应为 ArrayList<String>
```
被类级 `@SuppressWarnings("rawtypes")` 掩盖。

### 缺陷 11: 参数名拼写错误
`trimedEquals(String soure, ...)` — `soure` 应为 `source`（公开 API，影响调用方 IDE 提示）。

### 缺陷 12: `toDBC` 效率低下
链式调用 12 次 `replaceAll`，每次重新编译正则。应改为单次字符遍历。

### 缺陷 13: `humpToSplitStr` 不处理连续大写→单词边界
`humpToSplitStr("XMLParser", "_")` → `"XMLParser"`（期望 `"XML_Parser"`）。当检测到大写→小写转换时应插入分隔符。此为已知局限，视业务需求决定是否修复。

---

## 测试策略

| 缺陷 | 测试方法 | 测试类 |
|------|----------|--------|
| 缺陷1 | `testReplaceAllStrSingleChar`: assert `replaceAllStr("a","a","b")` = `"b"` | StringUtilsTest |
| 缺陷2 | `testMatchCntInfiniteLoop`: assert `matchCnt("aaa", Pattern.compile("aa"), 2)` 不超时且返回正确值 | StringUtilsTest |
| 缺陷3 | `testNullSafety`: 对每个方法传入 null，断言返回安全值（-1/false/null/空数组） | StringUtilsTest |
| 缺陷4 | `testLoopAppendNull`: `loopAppendWithSign(null, ",", 3)` = `"//"` (两个逗号连接三个空串) | StringUtilsTest |
| 缺陷5 | `testSymMarkMatchRegexChar`: `getSymMarkMatchIndex("(", ")", "(a+b)", 0)` 不抛异常 | StringUtilsTest |
| 缺陷6 | `testSplitRegexDot`: `splitRegex("1.2.3", ".", false)` = `["1","2","3"]` | StringUtilsTest |
| 缺陷7 | `testSecureMaskNegativeParam`: `secureMask("hello", -1, 2, "*")` 不抛异常 | StringUtilsTest |

所有新增测试需设置超时（如 `@Timeout(2)`）以防止死循环缺陷在测试阶段挂起。

## Open Questions

1. **`splitRegex` 语义**: default 分支是纯字面量切分还是正则切分？如果改为全部字面量，可能影响已有调用方（`SqlConfigParseUtils` 等传入正则的场景）。建议保持 default 为正则，仅补充 `.` 和 `|` 的字面量特例。
2. **`replaceFirstStr` null 替换语义**: 传 null 时应视为删除（空串）还是保留原文？需要确认现有调用方的行为。
3. **缺陷13（驼峰转下划线）**: 是否有业务场景依赖当前行为？若无明确需求建议暂不修改，单独作为一个增强 story。
