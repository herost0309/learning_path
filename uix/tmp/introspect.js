// Find problemsetQuestionList's return type, then list its question fields.
const q1 = `query { __schema { queryType { fields { name type { name kind ofType { name } } } } } }`;
const r1 = await fetch("https://leetcode.cn/graphql/", {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ query: q1 }),
});
const j1 = await r1.json();
const f = j1.data.__schema.queryType.fields.find((x) => x.name === "problemsetQuestionList");
if (!f) return "field not found. sample: " + j1.data.__schema.queryType.fields.slice(0, 20).map((x) => x.name).join(",");
const tname = f.type.name || (f.type.ofType && f.type.ofType.name);
const q2 = `query { __type(name: "${tname}") { name fields { name type { name kind ofType { name } } } } }`;
const r2 = await fetch("https://leetcode.cn/graphql/", {
  method: "POST",
  headers: { "content-type": "application/json" },
  body: JSON.stringify({ query: q2 }),
});
const j2 = await r2.json();
const node = j2.data.__type;
return { listType: tname, listFields: node.fields.map((x) => x.name + ":" + (x.type.name || x.type.ofType?.name || x.type.kind)) };