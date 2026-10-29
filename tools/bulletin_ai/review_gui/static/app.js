const state = {
  items: [],
  filtered: [],
  selected: null,
  detail: null,
};

const queueList = document.getElementById("queueList");
const queueCount = document.getElementById("queueCount");
const searchInput = document.getElementById("searchInput");
const statusFilter = document.getElementById("statusFilter");
const minScore = document.getElementById("minScore");
const goldOnly = document.getElementById("goldOnly");
const refreshQueue = document.getElementById("refreshQueue");

const emptyState = document.getElementById("emptyState");
const detailView = document.getElementById("detailView");
const detailCatalog = document.getElementById("detailCatalog");
const detailTitle = document.getElementById("detailTitle");
const detailSlug = document.getElementById("detailSlug");
const detailSource = document.getElementById("detailSource");
const detailScore = document.getElementById("detailScore");
const detailReasons = document.getElementById("detailReasons");
const statusSelect = document.getElementById("statusSelect");
const saveStatus = document.getElementById("saveStatus");
const reviewNote = document.getElementById("reviewNote");
const acceptGold = document.getElementById("acceptGold");
const openSnippet = document.getElementById("openSnippet");
const snippetFrame = document.getElementById("snippetFrame");
const signals = document.getElementById("signals");

const parsedTree = document.getElementById("parsedTree");
const repairedTree = document.getElementById("repairedTree");
const parsedJson = document.getElementById("parsedJson");
const repairedJson = document.getElementById("repairedJson");
const goldDiffJson = document.getElementById("goldDiffJson");
const goldExpectedJson = document.getElementById("goldExpectedJson");
const lintJson = document.getElementById("lintJson");
const llmJson = document.getElementById("llmJson");

const STATUS_LABELS = {
  pending: "Pending",
  accepted: "Accepted",
  "needs-work": "Needs Work",
  rejected: "Rejected",
};

function escapeText(value) {
  if (value == null) return "";
  return String(value);
}

function setJson(pre, data) {
  if (!data) {
    pre.textContent = "No data";
    return;
  }
  pre.textContent = JSON.stringify(data, null, 2);
}

function formatNumber(value) {
  if (value == null) return null;
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

function buildBadge(text) {
  const badge = document.createElement("span");
  badge.className = "tree-badge";
  badge.textContent = text;
  return badge;
}

function renderParsedTree(container, parsed) {
  container.innerHTML = "";
  if (!parsed) {
    container.textContent = "No parsed tree.";
    return;
  }
  const root = parsed.root || parsed;
  if (!root || typeof root !== "object") {
    container.textContent = "No parsed tree.";
    return;
  }
  container.appendChild(buildTreeNode(root, 0));
}

function buildTreeNode(node, depth) {
  const details = document.createElement("details");
  details.className = "tree-node";
  details.open = depth < 2;

  const summary = document.createElement("summary");
  summary.className = "tree-summary";

  const title = document.createElement("span");
  title.className = "tree-title";
  title.textContent = node.name || "Requirement";

  const badges = document.createElement("div");
  badges.className = "tree-badges";

  badges.appendChild(buildBadge(node.kind || "UNKNOWN"));

  const nRequired = formatNumber(node.nRequired);
  if (nRequired !== null) {
    badges.appendChild(buildBadge(`n=${nRequired}`));
  }

  const creditMin = formatNumber(node.creditMin);
  const creditMax = formatNumber(node.creditMax);
  if (creditMin !== null || creditMax !== null) {
    badges.appendChild(buildBadge(`credits ${creditMin ?? 0}-${creditMax ?? "max"}`));
  }

  if (node.doubleCountAllowed === false) {
    badges.appendChild(buildBadge("no double-count"));
  }
  const doubleCountCap = formatNumber(node.doubleCountCap);
  if (doubleCountCap !== null) {
    badges.appendChild(buildBadge(`double-count cap ${doubleCountCap}`));
  }

  const courseCount = (node.courseOptions || []).length;
  if (courseCount > 0) {
    badges.appendChild(buildBadge(`${courseCount} courses`));
  }

  const attrCount = (node.attributeOptions || []).length;
  if (attrCount > 0) {
    badges.appendChild(buildBadge(`${attrCount} attrs`));
  }

  summary.appendChild(title);
  summary.appendChild(badges);
  details.appendChild(summary);

  const body = document.createElement("div");
  body.className = "tree-body";

  const noteText = [node.notes, node.footnoteText].filter(Boolean).join(" ");
  if (noteText) {
    const note = document.createElement("div");
    note.className = "tree-note";
    note.textContent = noteText;
    body.appendChild(note);
  }

  if (courseCount > 0) {
    const courseList = document.createElement("div");
    courseList.className = "tree-courses";
    node.courseOptions.forEach((course) => {
      const row = document.createElement("div");
      row.className = "tree-course";
      const code = course.code || "Course";
      const titleText = course.title ? ` · ${course.title}` : "";
      const creditsText =
        course.credits != null && course.credits !== ""
          ? ` (${course.credits} cr)`
          : "";
      row.textContent = `${code}${titleText}${creditsText}`;
      courseList.appendChild(row);
    });
    body.appendChild(courseList);
  }

  const attributes = node.attributeOptions || [];
  if (attributes.length > 0) {
    const attrList = document.createElement("div");
    attrList.className = "tree-courses";
    attributes.forEach((attr) => {
      const row = document.createElement("div");
      row.className = "tree-course";
      const label = attr.code || "Attribute";
      const name = attr.name ? ` · ${attr.name}` : "";
      row.textContent = `${label}${name}`;
      attrList.appendChild(row);
    });
    body.appendChild(attrList);
  }

  const children = node.children || [];
  if (children.length > 0) {
    const childWrap = document.createElement("div");
    childWrap.className = "tree-children";
    children.forEach((child) => {
      childWrap.appendChild(buildTreeNode(child, depth + 1));
    });
    body.appendChild(childWrap);
  }

  details.appendChild(body);
  return details;
}

function formatReasons(reasons) {
  if (!reasons || reasons.length === 0) return "No issues";
  return reasons.join(", ");
}

function renderQueueList() {
  queueList.innerHTML = "";
  if (state.filtered.length === 0) {
    const empty = document.createElement("div");
    empty.className = "muted";
    empty.textContent = "No items match the filters.";
    queueList.appendChild(empty);
    return;
  }

  state.filtered.forEach((item) => {
    const card = document.createElement("div");
    card.className = "queue-item";
    if (
      state.selected &&
      item.slug === state.selected.slug &&
      item.catalogLabel === state.selected.catalogLabel
    ) {
      card.classList.add("active");
    }

    const title = document.createElement("h3");
    title.textContent = item.programName || item.slug;
    card.appendChild(title);

    const meta = document.createElement("div");
    meta.className = "queue-meta";
    meta.innerHTML = `
      <span>${escapeText(item.catalogLabel)}</span>
      <span>Score: ${escapeText(item.score)}</span>
      <span>Status: ${STATUS_LABELS[item.status] || item.status}</span>
    `;
    card.appendChild(meta);

    const reasons = document.createElement("div");
    reasons.className = "queue-meta";
    reasons.textContent = formatReasons(item.reasons);
    card.appendChild(reasons);

    card.addEventListener("click", () => selectItem(item));
    queueList.appendChild(card);
  });
}

function applyFilters() {
  const query = searchInput.value.trim().toLowerCase();
  const statusValue = statusFilter.value;
  const minScoreValue = Number(minScore.value) || 0;
  const goldOnlyValue = goldOnly.checked;

  state.filtered = state.items.filter((item) => {
    const matchesQuery =
      !query ||
      (item.programName || "").toLowerCase().includes(query) ||
      (item.slug || "").toLowerCase().includes(query);
    const matchesStatus =
      statusValue === "all" ? true : (item.status || "pending") === statusValue;
    const matchesScore = (item.score || 0) >= minScoreValue;
    const matchesGold = goldOnlyValue ? (item.gold && item.gold.hasMismatch) : true;
    return matchesQuery && matchesStatus && matchesScore && matchesGold;
  });

  queueCount.textContent = state.filtered.length;
  renderQueueList();
}

function loadQueue() {
  fetch("/api/queue")
    .then((res) => res.json())
    .then((data) => {
      state.items = data.items || [];
      state.items.sort((a, b) => (b.score || 0) - (a.score || 0));
      state.filtered = [...state.items];
      queueCount.textContent = state.filtered.length;
      renderQueueList();
      if (state.selected) {
        const updated = state.items.find(
          (item) =>
            item.slug === state.selected.slug &&
            item.catalogLabel === state.selected.catalogLabel
        );
        if (updated) {
          selectItem(updated);
        }
      }
    })
    .catch(() => {
      queueList.innerHTML = "<div class=\"muted\">Failed to load queue.</div>";
    });
}

function selectItem(item) {
  state.selected = item;
  state.detail = null;
  renderQueueList();
  fetch(`/api/item?slug=${encodeURIComponent(item.slug)}&catalogLabel=${encodeURIComponent(item.catalogLabel)}`)
    .then((res) => res.json())
    .then((data) => {
      state.detail = data;
      renderDetail();
    })
    .catch(() => {
      state.detail = null;
      renderDetail();
    });
}

function renderSignals(detail) {
  signals.innerHTML = "";
  const items = [];
  if (detail.item.lint) {
    items.push({
      label: "Lint issues",
      value: detail.item.lint.issueCount || 0,
    });
  }
  if (detail.item.llm) {
    items.push({
      label: "LLM issues",
      value: detail.item.llm.issueCount || 0,
    });
  }
  if (detail.item.gold) {
    items.push({
      label: "Gold mismatch",
      value: detail.item.gold.hasMismatch ? "Yes" : "No",
    });
  }
  items.push({ label: "Score", value: detail.item.score });

  items.forEach((entry) => {
    const row = document.createElement("div");
    row.className = "queue-meta";
    row.innerHTML = `<span>${escapeText(entry.label)}</span><span class="pill muted">${escapeText(entry.value)}</span>`;
    signals.appendChild(row);
  });
}

function renderDetail() {
  if (!state.selected || !state.detail) {
    emptyState.classList.remove("hidden");
    detailView.classList.add("hidden");
    return;
  }

  const detail = state.detail;
  const item = detail.item;
  emptyState.classList.add("hidden");
  detailView.classList.remove("hidden");

  detailCatalog.textContent = item.catalogLabel || "";
  detailTitle.textContent = item.programName || item.slug;
  detailSlug.textContent = item.slug || "";
  detailSource.href = item.sourceUrl || "#";
  detailScore.textContent = `Score ${item.score}`;
  detailReasons.textContent = formatReasons(item.reasons);
  statusSelect.value = item.status || "pending";
  reviewNote.value = item.reviewNote || "";

  snippetFrame.srcdoc = detail.snippetHtml || "<p>No snippet available.</p>";
  renderSignals(detail);

  renderParsedTree(parsedTree, detail.parsed);
  renderParsedTree(repairedTree, detail.repaired);
  setJson(parsedJson, detail.parsed);
  setJson(repairedJson, detail.repaired);
  setJson(goldDiffJson, detail.goldDiff);
  setJson(goldExpectedJson, detail.goldExpected);
  setJson(lintJson, detail.lint);
  setJson(llmJson, detail.llm);
}

function saveStatusUpdate() {
  if (!state.selected) return;
  const payload = {
    slug: state.selected.slug,
    catalogLabel: state.selected.catalogLabel,
    status: statusSelect.value,
    reviewNote: reviewNote.value.trim(),
    updatedAt: new Date().toISOString(),
  };

  fetch("/api/queue/status", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  })
    .then((res) => res.json())
    .then(() => {
      const item = state.items.find(
        (entry) =>
          entry.slug === state.selected.slug &&
          entry.catalogLabel === state.selected.catalogLabel
      );
      if (item) {
        item.status = payload.status;
        item.reviewNote = payload.reviewNote;
      }
      renderQueueList();
    })
    .catch(() => {
      alert("Failed to update status");
    });
}

function acceptAsGold() {
  if (!state.selected) return;
  const source = state.detail && state.detail.repaired ? "repaired" : "parsed";
  const payload = {
    slug: state.selected.slug,
    catalogLabel: state.selected.catalogLabel,
    source,
  };

  fetch("/api/gold/accept", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  })
    .then((res) => res.json())
    .then((data) => {
      if (data.error) {
        alert(data.error);
        return;
      }
      statusSelect.value = "accepted";
      reviewNote.value = reviewNote.value.trim();
      saveStatusUpdate();
      alert(`Saved gold fixture: ${data.path}`);
    })
    .catch(() => {
      alert("Failed to save gold fixture");
    });
}

refreshQueue.addEventListener("click", loadQueue);
searchInput.addEventListener("input", applyFilters);
statusFilter.addEventListener("change", applyFilters);
minScore.addEventListener("input", applyFilters);
goldOnly.addEventListener("change", applyFilters);
saveStatus.addEventListener("click", saveStatusUpdate);
acceptGold.addEventListener("click", acceptAsGold);
openSnippet.addEventListener("click", () => {
  if (!state.detail) return;
  const content = state.detail.snippetHtml || "<p>No snippet available.</p>";
  const win = window.open("", "_blank");
  if (!win) return;
  win.document.write(content);
  win.document.close();
});

loadQueue();
