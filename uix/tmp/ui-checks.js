// UI consistency checks — runs on a problem description page.
const body = document.body.innerText;
const slug = location.pathname.split("/")[2];
const titleLink = document.querySelector(`a[href*="/problems/${slug}"]`);
const tabs = ["描述", "题解", "讨论", "解决方案", "提交记录", "子任务", "提示"].filter((t) => body.includes(t));
return {
  url: location.href,
  docTitle: document.title,
  titleShown: titleLink?.innerText?.replace(/\s+/g, " ").trim().slice(0, 80) ?? null,
  difficulty: (body.match(/(简单|中等|困难)/) || [])[1] ?? null,
  hasEditor: !!document.querySelector(".monaco-editor, .cm-editor, #editor, textarea"),
  descLen: body.length,
  hasAcRate: /通过率/.test(body),
  hasHints: body.includes("提示"),
  tabs,
  langTabs: [...new Set(["C++", "Java", "Python", "Python3", "JavaScript", "Go", "Rust"].filter((l) => body.includes(l)))],
};