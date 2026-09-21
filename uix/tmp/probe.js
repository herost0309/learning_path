// Calibrate UI-check selectors on a live problem page + probe solutions API.
const sig = {};
sig.url = location.href;
sig.docTitle = document.title;
// title candidates
sig.aTitle = document.querySelector('a[href*="/problems/' + location.pathname.split("/")[2] + '"]')?.innerText?.slice(0, 60) ?? null;
// difficulty: text 简单/中等/困难 anywhere in header area
sig.difficulty = (document.body.innerText.match(/(简单|中等|困难)/) || [])[1] ?? null;
// tabs
sig.tabDesc = !!document.querySelector('a[href*="/description"], [data-cy*="tab"] a[href*="description"]');
sig.tabSolution = !!document.querySelector('a[href*="/solution"]');
sig.tabSolutionNew = !!document.querySelector('a[href*="/solutions"]');
// editor
sig.monaco = !!document.querySelector(".monaco-editor, .cm-editor, #editor, textarea");
// description content
sig.descLen = (document.querySelector('[class*="description"]')?.innerText ?? document.body.innerText).length;
// tags
sig.tags = [...document.querySelectorAll('a[href*="/tag/"]')].length;
// solutions API probe
let sol = null;
try {
  const q = `query s($q: String!, $f: Int!, $s: Int!, $o: SolutionOrderBy!) {
    solutionListArticle(questionSlug: $q, first: $f, skip: $s, orderBy: $o) {
      solutions { id title slug voteCount author { username } }
    }
  }`;
  const r = await fetch("https://leetcode.cn/graphql/", {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ query: q, variables: { q: "return-length-of-arguments-passed", f: 3, s: 0, o: "HOT" } }),
  });
  const j = await r.json();
  sol = j.errors ? { err: JSON.stringify(j.errors).slice(0, 300) } : j.data.solutionListArticle.solutions.map((s) => ({ title: s.title.slice(0, 40), votes: s.voteCount, slug: s.slug, by: s.author.username }));
} catch (e) {
  sol = { err: String(e) };
}
return { sig, sol };