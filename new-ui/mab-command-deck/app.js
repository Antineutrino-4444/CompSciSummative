const COLORS = {
  bg: "#04080c",
  grid: "rgba(32, 224, 213, 0.16)",
  cyan: "#20e0d5",
  green: "#66f08d",
  amber: "#ffb84a",
  red: "#ff4d58",
  magenta: "#f36ad5",
  blue: "#6fa8ff",
  white: "#e6edf1"
};

const PIECES = {
  I: { color: COLORS.cyan, cells: [[0, 1], [1, 1], [2, 1], [3, 1]] },
  O: { color: COLORS.amber, cells: [[1, 0], [2, 0], [1, 1], [2, 1]] },
  T: { color: COLORS.magenta, cells: [[1, 0], [0, 1], [1, 1], [2, 1]] },
  S: { color: COLORS.green, cells: [[1, 0], [2, 0], [0, 1], [1, 1]] },
  Z: { color: COLORS.red, cells: [[0, 0], [1, 0], [1, 1], [2, 1]] },
  J: { color: COLORS.blue, cells: [[0, 0], [0, 1], [1, 1], [2, 1]] },
  L: { color: "#ff8f45", cells: [[2, 0], [0, 1], [1, 1], [2, 1]] }
};

const state = {
  tick: 0,
  defcon: 3,
  charge: 68,
  silo: 91,
  heat: 64,
  signal: 72,
  eta: 8,
  threats: 2,
  stack: 12,
  panel: "overview",
  message: "Incoming package split across two lanes. Intercept window is open.",
  playerGrid: makeGrid(false),
  rivalGrid: makeGrid(true)
};

const panels = {
  overview: {
    kicker: "Overview",
    title: "Theater Status",
    body: "Your board owns the initiative but the rival launch is live. Keep the center lane clean, preserve spin access, and spend doctrine only after the intercept window closes.",
    stats: [["Incoming", "2"], ["Response", "Ready"], ["Charge", "68%"], ["Defcon", "3"]]
  },
  intel: {
    kicker: "Intel",
    title: "Rival Signal",
    body: "Passive radar shows a balanced AI profile with high launch confidence and a vulnerable left well. Its next pressure spike is tied to a short ETA package.",
    stats: [["Signal", "72%"], ["Stack", "12 high"], ["Scan", "Fresh"], ["Bias", "Left"]]
  },
  doctrine: {
    kicker: "Doctrine",
    title: "Loaded Commands",
    body: "Manual Override is ready. Dead Hand is dormant. EMP Screen is charging. The useful choice right now is to wait until the intercept resolves.",
    stats: [["Manual", "Ready"], ["EMP", "42%"], ["Dead Hand", "Dormant"], ["Cycle", "Live"]]
  },
  launch: {
    kicker: "Launch",
    title: "Strike Path",
    body: "The payload is not clean enough for a responsible launch. Finish one Tetris or one spin route before committing the command deck to fire.",
    stats: [["Tetris", "3 / 4"], ["Spin", "1 / 2"], ["Payload", "Unsealed"], ["Hold", "Fire"]]
  }
};

const els = {
  starfield: document.getElementById("starfield"),
  playerBoard: document.getElementById("playerBoard"),
  rivalBoard: document.getElementById("rivalBoard"),
  holdCanvas: document.getElementById("holdCanvas"),
  nextCanvas: document.getElementById("nextCanvas"),
  radarCanvas: document.getElementById("radarCanvas"),
  defconValue: document.getElementById("defconValue"),
  playerChargeText: document.getElementById("playerChargeText"),
  playerSiloText: document.getElementById("playerSiloText"),
  rivalSignalText: document.getElementById("rivalSignalText"),
  rivalEtaText: document.getElementById("rivalEtaText"),
  threatCount: document.getElementById("threatCount"),
  readinessText: document.getElementById("readinessText"),
  readinessFill: document.getElementById("readinessFill"),
  heatText: document.getElementById("heatText"),
  heatFill: document.getElementById("heatFill"),
  rivalStackText: document.getElementById("rivalStackText"),
  rivalMissileText: document.getElementById("rivalMissileText"),
  scanText: document.getElementById("scanText"),
  primaryOrder: document.getElementById("primaryOrder"),
  doctrineState: document.getElementById("doctrineState"),
  launchPath: document.getElementById("launchPath"),
  eventFeed: document.getElementById("eventFeed"),
  panel: document.getElementById("detailPanel"),
  panelKicker: document.getElementById("panelKicker"),
  panelTitle: document.getElementById("panelTitle"),
  panelBody: document.getElementById("panelBody"),
  panelGrid: document.getElementById("panelGrid"),
  collapsePanel: document.getElementById("collapsePanel")
};

function makeGrid(rival) {
  const grid = Array.from({ length: 20 }, () => Array(10).fill(null));
  const floor = rival ? 13 : 15;
  for (let y = floor; y < 20; y += 1) {
    for (let x = 0; x < 10; x += 1) {
      const gap = rival ? (x === 7 && y > 15) : (x === 4 && y > 16);
      if (!gap && Math.random() > (rival ? 0.32 : 0.38)) {
        const names = Object.keys(PIECES);
        grid[y][x] = PIECES[names[(x + y + (rival ? 2 : 0)) % names.length]].color;
      }
    }
  }
  return grid;
}

function fitCanvas(canvas) {
  const rect = canvas.getBoundingClientRect();
  const ratio = window.devicePixelRatio || 1;
  const width = Math.max(1, Math.round(rect.width * ratio));
  const height = Math.max(1, Math.round(rect.height * ratio));
  if (canvas.width !== width || canvas.height !== height) {
    canvas.width = width;
    canvas.height = height;
  }
  return ratio;
}

function drawBoard(canvas, grid, activeName, ghostOffset) {
  fitCanvas(canvas);
  const ctx = canvas.getContext("2d");
  const w = canvas.width;
  const h = canvas.height;
  const cell = Math.floor(Math.min(w / 10, h / 20));
  const bx = Math.floor((w - cell * 10) / 2);
  const by = Math.floor((h - cell * 20) / 2);

  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = COLORS.bg;
  ctx.fillRect(0, 0, w, h);

  ctx.strokeStyle = COLORS.grid;
  ctx.lineWidth = Math.max(1, Math.floor(cell * 0.04));
  for (let x = 0; x <= 10; x += 1) {
    const px = bx + x * cell;
    ctx.beginPath();
    ctx.moveTo(px, by);
    ctx.lineTo(px, by + 20 * cell);
    ctx.stroke();
  }
  for (let y = 0; y <= 20; y += 1) {
    const py = by + y * cell;
    ctx.beginPath();
    ctx.moveTo(bx, py);
    ctx.lineTo(bx + 10 * cell, py);
    ctx.stroke();
  }

  for (let y = 0; y < 20; y += 1) {
    for (let x = 0; x < 10; x += 1) {
      if (grid[y][x]) drawBlock(ctx, bx + x * cell, by + y * cell, cell, grid[y][x], 1);
    }
  }

  const piece = PIECES[activeName];
  const px = activeName === "I" ? 3 : 4;
  const py = 2 + Math.floor(Math.sin(state.tick / 28) * 1.5);
  piece.cells.forEach(([x, y]) => {
    drawBlock(ctx, bx + (px + x) * cell, by + (py + y + ghostOffset) * cell, cell, piece.color, 0.18);
  });
  piece.cells.forEach(([x, y]) => {
    drawBlock(ctx, bx + (px + x) * cell, by + (py + y) * cell, cell, piece.color, 1);
  });

  ctx.strokeStyle = "rgba(230, 237, 241, 0.72)";
  ctx.lineWidth = Math.max(1, Math.floor(cell * 0.08));
  ctx.strokeRect(bx + 0.5, by + 0.5, cell * 10 - 1, cell * 20 - 1);
}

function drawBlock(ctx, x, y, size, color, alpha) {
  ctx.save();
  ctx.globalAlpha = alpha;
  ctx.fillStyle = color;
  ctx.fillRect(x + 1, y + 1, size - 2, size - 2);
  ctx.fillStyle = "rgba(255, 255, 255, 0.22)";
  ctx.fillRect(x + 1, y + 1, size - 2, Math.max(1, size * 0.12));
  ctx.fillStyle = "rgba(0, 0, 0, 0.28)";
  ctx.fillRect(x + size - Math.max(2, size * 0.16), y + 2, Math.max(1, size * 0.12), size - 4);
  ctx.restore();
}

function drawPiecePreview(canvas, names) {
  fitCanvas(canvas);
  const ctx = canvas.getContext("2d");
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  ctx.fillStyle = "rgba(4, 8, 12, 0.75)";
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  names.forEach((name, index) => {
    const piece = PIECES[name];
    const cell = Math.floor(Math.min(canvas.width / 6, canvas.height / (names.length * 4)));
    const yOffset = index * Math.floor(canvas.height / names.length) + cell;
    const xOffset = Math.floor((canvas.width - cell * 4) / 2);
    piece.cells.forEach(([x, y]) => {
      drawBlock(ctx, xOffset + x * cell, yOffset + y * cell, cell, piece.color, 1);
    });
  });
}

function drawRadar() {
  const canvas = els.radarCanvas;
  fitCanvas(canvas);
  const ctx = canvas.getContext("2d");
  const w = canvas.width;
  const h = canvas.height;
  const r = Math.min(w, h) * 0.42;
  const cx = w / 2;
  const cy = h / 2;
  const sweep = (state.tick * 0.025) % (Math.PI * 2);

  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = "rgba(4, 8, 12, 0.9)";
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fill();

  ctx.strokeStyle = "rgba(32, 224, 213, 0.32)";
  ctx.lineWidth = 1;
  [0.34, 0.64, 1].forEach((scale) => {
    ctx.beginPath();
    ctx.arc(cx, cy, r * scale, 0, Math.PI * 2);
    ctx.stroke();
  });
  ctx.beginPath();
  ctx.moveTo(cx - r, cy);
  ctx.lineTo(cx + r, cy);
  ctx.moveTo(cx, cy - r);
  ctx.lineTo(cx, cy + r);
  ctx.stroke();

  const gradient = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
  gradient.addColorStop(0, "rgba(102, 240, 141, 0.28)");
  gradient.addColorStop(1, "rgba(102, 240, 141, 0)");
  ctx.fillStyle = gradient;
  ctx.beginPath();
  ctx.moveTo(cx, cy);
  ctx.arc(cx, cy, r, sweep - 0.38, sweep, false);
  ctx.closePath();
  ctx.fill();

  ctx.strokeStyle = COLORS.green;
  ctx.beginPath();
  ctx.moveTo(cx, cy);
  ctx.lineTo(cx + Math.cos(sweep) * r, cy + Math.sin(sweep) * r);
  ctx.stroke();

  drawRadarPip(ctx, cx - r * 0.38, cy - r * 0.26, COLORS.amber, 3 + Math.sin(state.tick / 12));
  drawRadarPip(ctx, cx + r * 0.42, cy + r * 0.18, COLORS.red, 4 + Math.cos(state.tick / 10));
  drawRadarPip(ctx, cx + r * 0.04, cy - r * 0.58, COLORS.cyan, 2);
}

function drawRadarPip(ctx, x, y, color, radius) {
  ctx.fillStyle = color;
  ctx.beginPath();
  ctx.arc(x, y, radius, 0, Math.PI * 2);
  ctx.fill();
}

function drawBackdrop() {
  const canvas = els.starfield;
  fitCanvas(canvas);
  const ctx = canvas.getContext("2d");
  const w = canvas.width;
  const h = canvas.height;
  ctx.clearRect(0, 0, w, h);
  ctx.fillStyle = "rgba(5, 7, 11, 1)";
  ctx.fillRect(0, 0, w, h);
  for (let i = 0; i < 72; i += 1) {
    const x = (i * 137 + state.tick * 0.18) % w;
    const y = (i * 73) % h;
    const alpha = 0.08 + ((i % 5) * 0.025);
    ctx.fillStyle = `rgba(32, 224, 213, ${alpha})`;
    ctx.fillRect(x, y, 1.5, 1.5);
  }
}

function syncUi() {
  els.defconValue.textContent = state.defcon;
  els.playerChargeText.textContent = `${pad(state.charge)} / 100`;
  els.playerSiloText.textContent = `${state.silo}%`;
  els.rivalSignalText.textContent = `${state.signal}%`;
  els.rivalEtaText.textContent = `${pad(state.eta)} pieces`;
  els.threatCount.textContent = `${pad(state.threats)} live`;
  els.readinessText.textContent = `${state.charge}%`;
  els.readinessFill.style.width = `${state.charge}%`;
  els.heatText.textContent = `${state.heat}%`;
  els.heatFill.style.width = `${state.heat}%`;
  els.rivalStackText.textContent = `${state.stack} high`;
  els.rivalMissileText.textContent = state.threats > 0 ? "Armed" : "Dormant";
  els.scanText.textContent = state.panel === "intel" ? "Active" : "Fresh";
  els.primaryOrder.textContent = state.threats > 0 ? "Spin Intercept" : "Build Charge";
  els.doctrineState.textContent = state.charge > 74 ? "Manual Override Ready" : "Doctrine Charging";
  els.launchPath.textContent = state.charge > 85 ? "Tetris 4 / 4 - Spin 2 / 2" : "Tetris 3 / 4 - Spin 1 / 2";
  els.eventFeed.textContent = state.message;
}

function setPanel(name, reveal = true) {
  state.panel = name;
  document.querySelectorAll(".mode-switch button").forEach((button) => {
    button.classList.toggle("is-active", button.dataset.panel === name);
  });
  const data = panels[name] || panels.overview;
  els.panelKicker.textContent = data.kicker;
  els.panelTitle.textContent = data.title;
  els.panelBody.textContent = data.body;
  els.panelGrid.innerHTML = data.stats.map(([label, value]) => {
    return `<div><span>${label}</span><b>${value}</b></div>`;
  }).join("");
  if (reveal) els.panel.classList.remove("is-collapsed");
  syncUi();
}

function runAction(action) {
  if (action === "scan") {
    state.signal = clamp(state.signal + 9, 0, 100);
    state.heat = clamp(state.heat + 4, 0, 100);
    state.message = "Radar sweep completed. Rival well is exposed on the left flank.";
    setPanel("intel");
  } else if (action === "intercept") {
    state.threats = Math.max(0, state.threats - 1);
    state.eta = clamp(state.eta + 3, 0, 30);
    state.charge = clamp(state.charge - 8, 0, 100);
    state.message = "Spin intercept committed. One incoming package is breaking apart.";
    setPanel("overview");
  } else if (action === "launch") {
    if (state.charge > 84) {
      state.charge = clamp(state.charge - 38, 0, 100);
      state.heat = clamp(state.heat + 14, 0, 100);
      state.message = "Launch command sealed. Rival impact timer has started.";
    } else {
      state.message = "Launch hold remains active. Finish the route before firing.";
    }
    setPanel("launch");
  }
  syncUi();
}

function updateSimulation() {
  if (state.tick % 120 === 0) {
    state.eta = clamp(state.eta - 1, 0, 30);
    state.heat = clamp(state.heat + (state.threats > 0 ? 1 : -1), 22, 96);
    state.charge = clamp(state.charge + (state.threats > 0 ? 0 : 1), 0, 100);
    if (state.eta === 0 && state.threats > 0) {
      state.silo = clamp(state.silo - 7, 0, 100);
      state.eta = 9;
      state.message = "Impact tremor registered. Silo plating absorbed the edge.";
    }
  }
  state.defcon = state.heat > 84 ? 1 : state.heat > 70 ? 2 : state.heat > 48 ? 3 : 4;
}

function frame() {
  state.tick += 1;
  updateSimulation();
  drawBackdrop();
  drawBoard(els.playerBoard, state.playerGrid, "T", 12);
  drawBoard(els.rivalBoard, state.rivalGrid, "L", 10);
  drawRadar();
  if (state.tick % 30 === 0) syncUi();
  requestAnimationFrame(frame);
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function pad(value) {
  return String(value).padStart(2, "0");
}

document.querySelectorAll(".mode-switch button").forEach((button) => {
  button.addEventListener("click", () => setPanel(button.dataset.panel));
});

document.querySelectorAll(".dock-action").forEach((button) => {
  button.addEventListener("click", () => runAction(button.dataset.action));
});

els.collapsePanel.addEventListener("click", () => {
  els.panel.classList.toggle("is-collapsed");
});

window.addEventListener("resize", () => {
  drawBackdrop();
  drawPiecePreview(els.holdCanvas, ["I"]);
  drawPiecePreview(els.nextCanvas, ["O", "S", "J"]);
});

drawPiecePreview(els.holdCanvas, ["I"]);
drawPiecePreview(els.nextCanvas, ["O", "S", "J"]);
setPanel("overview", false);
syncUi();
frame();
