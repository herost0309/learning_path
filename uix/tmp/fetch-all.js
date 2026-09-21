// Probe-and-retry: start from the historically-known field set, drop fields
// the schema rejects, then page through the full problem list.
const base = `query probList($cat: String, $limit: Int, $skip: Int, $filters: QuestionListFilterInput) {
  problemsetQuestionList(categorySlug: $cat, limit: $limit, skip: $skip, filters: $filters) {
    hasMore total
    questions { FIELDS }
  }
}`;
let fields = ["titleSlug", "titleCn", "title", "acRate", "difficulty", "paidOnly", "frontendQuestionId", "status"];
async function probe() {
  for (let i = 0; i < 6; i++) {
    const q = base.replace("FIELDS", fields.join(" "));
    const r = await fetch("https://leetcode.cn/graphql/", {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({ query: q, variables: { cat: "", limit: 1, skip: 0, filters: {} } }),
    });
    const j = await r.json();
    if (!j.errors) return j.data.problemsetQuestionList;
    const bad = new Set();
    for (const e of j.errors) {
      const m = /Cannot query field "(\w+)"/.exec(e.message);
      if (m) bad.add(m[1]);
    }
    if (!bad.size) throw new Error(JSON.stringify(j.errors).slice(0, 300));
    fields = fields.filter((f) => !bad.has(f));
  }
  throw new Error("probe failed");
}
const first = await probe();
if (first && first.questions) {
  // probe only fetched 1 item — refetch page 0 fully with accepted fields
}
const q = base.replace("FIELDS", fields.join(" "));
async function one(skip) {
  const r = await fetch("https://leetcode.cn/graphql/", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ query: q, variables: { cat: "", limit: 100, skip, filters: {} } }),
  });
  const j = await r.json();
  if (j.errors) throw new Error(JSON.stringify(j.errors).slice(0, 200));
  return j.data.problemsetQuestionList;
}
const p0 = await one(0);
const total = p0.total;
const all = [...p0.questions];
for (let skip = 100; skip < total && all.length < 5000; skip += 100) {
  const batch = await one(skip);
  if (!batch?.questions?.length) break;
  all.push(...batch.questions);
  await new Promise((r) => setTimeout(r, 100));
}
return { fields, total, fetched: all.length, questions: all };