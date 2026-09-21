// Extract solution cards with like/view/comment counts (new leetcode.cn UI:
// counts sit in spans next to fa-thumbs-up / fa-eye / fa-comment svgs).
const cards = [...document.querySelectorAll("div.group")].filter((c) =>
  c.querySelector('a[href*="/solutions/"]')
);
const num = (card, icon) => {
  const svg = [...card.querySelectorAll("svg")].find((s) =>
    s.classList.contains(icon)
  );
  return svg ? Number(svg.closest("div")?.parentElement?.querySelector("span")?.textContent ?? 0) : null;
};
const out = cards.map((c) => {
  const titleA =
    [...c.querySelectorAll('a[href*="/solutions/"]')].find((a) => (a.innerText || "").trim()) || null;
  // avatar link has empty text; the username link is the first non-empty one
  const authorA = [...c.querySelectorAll('a[href^="/u/"]')].find((a) => (a.innerText || "").trim());
  return {
    title: titleA?.innerText?.replace(/\s+/g, " ").trim().slice(0, 100) ?? null,
    href: titleA?.getAttribute("href") ?? null,
    author: authorA?.innerText?.trim() ?? null,
    official: c.innerText.includes("官方"),
    likes: num(c, "fa-triangle"),
    views: num(c, "fa-eye"),
    comments: num(c, "fa-comment"),
  };
}).filter((s) => s.href);
return { count: out.length, solutions: out };