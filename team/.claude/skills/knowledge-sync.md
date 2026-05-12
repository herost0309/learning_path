# Skill: 知识库长期同步

> 用途：确保技术文档与代码保持同步，建立"代码变更 → 文档更新"的长期维护机制。
> 解决的核心问题：文档写完就过时，没人维护，最终变成"历史文物"。

## 触发条件

- 代码变更后需要检查文档是否同步
- 定期巡检文档与代码的一致性
- 新文档发布前需要校验准确性
- 代码 Review 时检查是否遗漏了文档更新

---

## 一、代码 → 文档同步

### 1.1 变更检测

当代码发生变更时，AI 自动检测受影响的文档：

```
Prompt：
分析以下代码变更，判断哪些文档需要更新。

变更内容：
[粘贴 git diff 或变更的文件列表]

检查清单：
1. 新增/修改了 API 端点 → docs/api/ 下对应的文档
2. 修改了数据模型（新增/删除/修改字段）→ docs/architecture/data-model.md
3. 修改了业务逻辑（if/else 条件、计算公式）→ 对应的业务规则文档
4. 新增/修改了配置项 → docs/ops/ 下的运维手册
5. 修改了模块间依赖关系 → docs/architecture/ 下的架构文档
6. 修改了外部服务调用 → 相关集成文档

输出：
- 受影响的文档列表
- 需要更新的具体章节
- 更新建议内容
```

### 1.2 增量更新

```
Prompt：
代码发生了以下变更，请更新对应的文档内容。

变更内容：
[描述变更]

当前文档：
[粘贴当前文档内容]

要求：
1. 只更新受影响的部分，保留未变更的内容
2. 更新时保持文档风格一致
3. 在更新处标注变更日期
4. 如果变更引入了新的概念或接口，补充完整说明

输出：完整的更新后文档
```

### 1.3 CI/CD 集成模板

在 CI 流程中添加文档同步检查步骤：

```yaml
# .gitlab-ci.yml 示例
doc-sync-check:
  stage: verify
  script:
    - |
      # 检查变更文件是否涉及文档
      CHANGED_FILES=$(git diff --name-only origin/main...HEAD)
      CODE_FILES=$(echo "$CHANGED_FILES" | grep -E '\.(java|py|js|ts|go)$' || true)
      DOC_FILES=$(echo "$CHANGED_FILES" | grep -E '^docs/' || true)

      if [ -n "$CODE_FILES" ] && [ -z "$DOC_FILES" ]; then
        echo "Warning: Code changed but no docs updated"
        echo "Changed code files:"
        echo "$CODE_FILES"
        echo ""
        echo "Consider updating related documentation."
      fi
  allow_failure: true
```

---

## 二、文档 → 代码反向校验

### 2.1 文档准确性校验

```
Prompt：
以下是一份技术文档，请对照当前代码验证其准确性。

文档内容：
[粘贴文档]

校验维度：
1. 文档中描述的接口是否在代码中真实存在？
2. 文档中的参数列表是否与代码一致？
3. 文档中描述的业务逻辑是否与代码实现一致？
4. 文档中的数据模型是否与当前代码匹配？
5. 文档中提到的配置项是否仍然有效？

对每个校验点标注结果：
- [通过] 文档描述与代码一致
- [差异] 文档与代码有差异，列出具体差异
- [缺失] 文档描述的内容在代码中找不到
- [新增] 代码中存在但文档未提及的内容

输出：校验报告
```

### 2.2 过时文档检测

```
Prompt：
扫描 docs/ 目录下所有文档，检测过时内容。

检测方法：
1. 读取文档中引用的文件路径，检查文件是否存在
2. 读取文档中引用的 API 端点，检查路由定义中是否存在
3. 读取文档中引用的配置项，检查配置文件中是否存在
4. 读取文档中的代码示例，检查是否与当前代码一致
5. 检查文档最后更新时间，超过 3 个月的标注为"需审核"

输出：过时内容清单，按严重程度排序
```

---

## 三、置信度标注体系

对每条文档内容标注置信度，让读者知道信息的可靠程度。

### 3.1 置信度定义

| 标记 | 含义 | 说明 |
|------|------|------|
| 🟢 高置信 | AI 从代码中明确确认 | 代码中有清晰的命名、注释或直接对应 |
| 🟡 中置信 | AI 推断，需人工确认 | AI 根据代码逻辑推断，但缺少直接证据 |
| 🔴 低置信 | AI 无法确定 | 可能来自过时文档，或代码逻辑不清晰 |
| 🔵 人工确认 | 已有人工验证 | 经过团队中有经验的人确认 |

### 3.2 Markdown 标注格式

在 Markdown 文档中，使用以下格式标注置信度：

```markdown
## 订单状态流转

| 当前状态 | 触发条件 | 目标状态 | 置信度 |
|----------|---------|---------|--------|
| 待支付 | 支付回调 | 已支付 | 🟢 高置信 |
| 已支付 | 超时未发货 | 已取消 | 🟡 中置信 — 推断自超时逻辑，需确认业务规则 |
| 已发货 | 买家确认收货 | 已完成 | 🔵 人工确认 — 张三确认于 2026-05-10 |

> ⚠️ **低置信项需要确认**：
> - 🔴 订单退款是否会触发状态回退？代码中有 RefundService 但状态流转不明确
```

### 3.3 Confluence 标注格式

在 Confluence 中使用信息面板（Info Panel）标注：

```html
<!-- 高置信内容，直接展示 -->
<p>订单创建后会写入 <code>orders</code> 表。</p>

<!-- 中置信内容，加提示框 -->
<div style="background: #fff3cd; padding: 8px; border-left: 4px solid #ffc107; margin: 8px 0;">
  <strong>AI 推断（需确认）</strong><br>
  VIP 用户享受 95 折优惠。推断依据：PricingEngine 中的 discountRate 计算逻辑。
  <br><em>如确认无误，请删除此提示框。</em>
</div>

<!-- 低置信内容，加警告框 -->
<div style="background: #f8d7da; padding: 8px; border-left: 4px solid #dc3545; margin: 8px 0;">
  <strong>不确定项</strong><br>
  退款流程的状态流转路径不明确。代码中存在 RefundService 但与订单状态机的交互逻辑需要人工确认。
  <br><em>请联系原模块负责人确认。</em>
</div>
```

---

## 四、定期巡检

### 4.1 月度巡检流程

```
Prompt：
执行月度文档巡检。

步骤：
1. 列出 docs/ 下所有文档
2. 对每份文档：
   a. 获取最后更新时间（从 git log）
   b. 读取文档内容
   c. 对照代码检查关键信息的准确性
   d. 标注需要更新的部分
3. 生成巡检报告

输出格式：
# 文档巡检报告 - [年月]

## 概要
| 指标 | 数值 |
|------|------|
| 文档总数 | ... |
| 过时文档数 | ... |
| 需要更新的文档 | ... |
| 高置信条目占比 | ... |

## 详细结果
[按文档列出巡检结果]

## 建议操作
[按优先级列出需要执行的更新操作]
```

### 4.2 巡检报告模板

```markdown
# 文档巡检报告 - 2026年5月

## 概要

| 指标 | 数值 | 上月 | 趋势 |
|------|------|------|------|
| 文档总数 | 23 | 21 | +2 |
| 过时文档 | 3 | 5 | -2 |
| 高置信条目占比 | 78% | 72% | +6% |
| 人工确认条目占比 | 35% | 28% | +7% |

## 需要更新的文档

| 文档 | 问题 | 严重程度 | 建议 |
|------|------|---------|------|
| docs/api/orders.md | 缺少新增的退款接口 | 高 | 补充接口文档 |
| docs/architecture/data-model.md | orders 表新增了 3 个字段 | 中 | 更新字段说明 |
| docs/ops/deploy.md | 部署脚本路径已变更 | 低 | 更新路径 |

## 低置信项清单

| 文档 | 低置信项 | 建议确认人 |
|------|---------|-----------|
| docs/architecture/payment.md | 退款对账流程 | 财务系统负责人 |
```

---

## 五、Git 钩子集成

### 5.1 Pre-commit 检查

```bash
#!/bin/bash
# .git/hooks/pre-commit
# 检查代码变更是否需要更新文档

STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM)

# 检查是否有代码文件变更
CODE_CHANGED=$(echo "$STAGED_FILES" | grep -cE '\.(java|py|js|ts|go|rb|php)$' || true)

# 检查是否有文档变更
DOC_CHANGED=$(echo "$STAGED_FILES" | grep -cE '^docs/' || true)

# 检查是否有 API 路由变更
ROUTE_CHANGED=$(echo "$STAGED_FILES" | grep -cE '(routes|controller|api)' || true)

if [ "$CODE_CHANGED" -gt 0 ] && [ "$DOC_CHANGED" -eq 0 ]; then
    echo ""
    echo "⚠️  代码已变更但文档未更新"
    echo "   变更的代码文件："
    echo "$STAGED_FILES" | grep -E '\.(java|py|js|ts|go|rb|php)$'
    echo ""
    echo "   建议：如果变更影响了接口、数据模型或业务逻辑，请同步更新文档。"
    echo ""
fi

if [ "$ROUTE_CHANGED" -gt 0 ] && [ "$DOC_CHANGED" -eq 0 ]; then
    echo ""
    echo "❗ API 路由已变更但 API 文档未更新"
    echo "   请更新 docs/api/ 下对应的文档。"
    echo ""
fi
```

### 5.2 MR/PR 检查清单

在 Merge Request 模板中添加文档检查项：

```markdown
## 文档同步检查

- [ ] 本次变更是否影响了 API 接口？
  - [ ] 已更新 `docs/api/` 下的对应文档
- [ ] 本次变更是否修改了数据模型？
  - [ ] 已更新 `docs/architecture/data-model.md`
- [ ] 本次变更是否修改了业务逻辑？
  - [ ] 已更新对应的业务规则文档
- [ ] 本次变更是否修改了配置项？
  - [ ] 已更新 `docs/ops/` 下的运维文档
- [ ] 新增的文档内容已标注置信度
```

---

## 使用建议

### 日常使用

| 场景 | 使用方式 |
|------|---------|
| 写代码时 | 提交前运行 pre-commit 检查 |
| Review 代码时 | 检查文档是否同步更新 |
| 每月初 | 运行月度巡检，生成报告 |
| 发布版本时 | 发布前检查所有 API 文档的准确性 |

### 团队协作

- 巡检报告发送给团队，让大家知道哪些文档需要关注
- 低置信项指派给对应的模块负责人确认
- 每月在团队会议中花 10 分钟过一遍巡检报告

### 与其他 Skill 配合

- **code-archaeology**: 代码考古产出文档后，用 knowledge-sync 维护
- **generate-docs**: 生成文档时自动标注置信度
- **code-review**: Review 时检查文档同步状态
