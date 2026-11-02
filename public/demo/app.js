/*
 * PharmaSync browser demo: screens and interaction. Business rules live in core.js (PSCore).
 * State is kept in this browser's localStorage; "Reset demo" starts again from the sample data.
 */
(function () {
  'use strict';
  const C = window.PSCore;
  const STORE_KEY = 'pharmasync-demo-v1';
  const AUTH_KEY = 'pharmasync-demo-auth';
  const THEME_KEY = 'pharmasync-demo-theme';
  const DOWNLOAD = 'https://github.com/maneesh17084/PharmaSync/releases/latest/download/pharmasync.jar';

  // ---------------------------------------------------------------- icons (same set as the desktop app)
  const ICONS = {
    dashboard: '<rect x="3" y="3" width="7" height="9" rx="1.5"/><rect x="14" y="3" width="7" height="5" rx="1.5"/><rect x="14" y="12" width="7" height="9" rx="1.5"/><rect x="3" y="16" width="7" height="5" rx="1.5"/>',
    alert: '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/>',
    cart: '<circle cx="9" cy="20" r="1.5"/><circle cx="18" cy="20" r="1.5"/><path d="M2 3h3l2.7 12.4a2 2 0 0 0 2 1.6h8.6a2 2 0 0 0 2-1.6L22 7H6"/>',
    invoice: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><polyline points="14 2 14 8 20 8"/><line x1="8" y1="13" x2="16" y2="13"/><line x1="8" y1="17" x2="13" y2="17"/>',
    users: '<circle cx="9" cy="7" r="4"/><path d="M2 21v-2a4 4 0 0 1 4-4h6a4 4 0 0 1 4 4v2"/><path d="M16 3.1a4 4 0 0 1 0 7.8"/><path d="M22 21v-2a4 4 0 0 0-3-3.9"/>',
    pill: '<g transform="rotate(-45 12 12)"><path d="M12 7.5H7a4.5 4.5 0 0 0 0 9h5z" fill="currentColor"/><rect x="2.5" y="7.5" width="19" height="9" rx="4.5"/></g>',
    layers: '<polygon points="12 2 2 7 12 12 22 7 12 2"/><polyline points="2 17 12 22 22 17"/><polyline points="2 12 12 17 22 12"/>',
    truck: '<path d="M15 16V5a1 1 0 0 0-1-1H2a1 1 0 0 0-1 1v11a1 1 0 0 0 1 1h2"/><path d="M15 9h4l3 3v4a1 1 0 0 1-1 1h-1"/><line x1="9" y1="17" x2="15" y2="17"/><circle cx="6.5" cy="17.5" r="2"/><circle cx="17.5" cy="17.5" r="2"/>',
    plus: '<line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/>',
    trash: '<polyline points="3 6 5 6 21 6"/><path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6"/><path d="M10 11v6M14 11v6"/><path d="M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"/>',
    download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/>',
    refresh: '<polyline points="23 4 23 10 17 10"/><polyline points="1 20 1 14 7 14"/><path d="M3.5 9a9 9 0 0 1 14.9-3.4L23 10M1 14l4.6 4.4A9 9 0 0 0 20.5 15"/>',
    search: '<circle cx="11" cy="11" r="7"/><line x1="21" y1="21" x2="16.65" y2="16.65"/>',
    card: '<rect x="2" y="5" width="20" height="14" rx="2"/><line x1="2" y1="10" x2="22" y2="10"/><line x1="6" y1="15" x2="10" y2="15"/>',
    clock: '<circle cx="12" cy="12" r="9"/><polyline points="12 7 12 12 15 14"/>',
    check: '<polyline points="20 6 9 17 4 12"/>',
    x: '<line x1="18" y1="6" x2="6" y2="18"/><line x1="6" y1="6" x2="18" y2="18"/>',
    logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><polyline points="16 17 21 12 16 7"/><line x1="21" y1="12" x2="9" y2="12"/>',
    moon: '<path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z"/>',
    sun: '<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"/>',
    package: '<path d="M21 8v8a2 2 0 0 1-1 1.7l-7 4a2 2 0 0 1-2 0l-7-4A2 2 0 0 1 3 16V8a2 2 0 0 1 1-1.7l7-4a2 2 0 0 1 2 0l7 4A2 2 0 0 1 21 8z"/><polyline points="3.3 7 12 12 20.7 7"/><line x1="12" y1="22" x2="12" y2="12"/>',
    eye: '<path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/>',
    printer: '<polyline points="6 9 6 2 18 2 18 9"/><path d="M6 18H4a2 2 0 0 1-2-2v-5a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2v5a2 2 0 0 1-2 2h-2"/><rect x="6" y="14" width="12" height="8"/>',
    trending: '<polyline points="23 6 13.5 15.5 8.5 10.5 1 18"/><polyline points="17 6 23 6 23 12"/>',
    chart: '<line x1="3" y1="21" x2="21" y2="21"/><rect x="5" y="11" width="3" height="7" rx="0.5"/><rect x="10.5" y="5" width="3" height="13" rx="0.5"/><rect x="16" y="14" width="3" height="4" rx="0.5"/>',
    wallet: '<path d="M19 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h15a1 1 0 0 0 1-1V8a1 1 0 0 0-1-1H5"/><circle cx="16.5" cy="14" r="1.2"/>',
    octagon: '<polygon points="7.9 2 16.1 2 22 7.9 22 16.1 16.1 22 7.9 22 2 16.1 2 7.9"/><line x1="15" y1="9" x2="9" y2="15"/><line x1="9" y1="9" x2="15" y2="15"/>'
  };

  function icon(name, size) {
    const s = size ? ' style="width:' + size + 'px;height:' + size + 'px"' : '';
    return '<svg class="i"' + s + ' viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" '
      + 'stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + ICONS[name] + '</svg>';
  }

  // ---------------------------------------------------------------- helpers

  const $ = (sel, root) => (root || document).querySelector(sel);
  const $$ = (sel, root) => Array.from((root || document).querySelectorAll(sel));

  function esc(v) {
    return String(v == null ? '' : v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[c]);
  }

  const money = C.formatMoney;
  const plain = p => money(p).replace('Rs ', '');
  const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

  function fmtDate(iso) {
    if (!iso) {
      return '-';
    }
    const [y, m, d] = iso.slice(0, 10).split('-');
    return d + ' ' + MONTHS[Number(m) - 1] + ' ' + y;
  }

  function fmtWhen(stamp) {
    return fmtDate(stamp) + (stamp.length > 10 ? ', ' + stamp.slice(11, 16) : '');
  }

  function words(code) {
    const w = String(code).replace(/_/g, ' ').toLowerCase();
    return w.charAt(0).toUpperCase() + w.slice(1);
  }

  const TONE = {
    PAID: 'ok', ACTIVE: 'ok', VALID: 'ok', OK: 'ok', RECEIVED: 'ok', INTRA_STATE: 'info',
    PARTIAL: 'warn', NEAR_EXPIRY: 'warn', LOW_STOCK: 'warn', QUARANTINED: 'warn',
    UNPAID: 'bad', EXPIRED: 'bad', INTER_STATE: 'neu'
  };

  function pill(code, label) {
    return '<span class="pill p-' + (TONE[code] || 'neu') + '">' + esc(label || words(code)) + '</span>';
  }

  const taxType = t => (t === 'INTER_STATE' ? 'IGST' : 'CGST + SGST');
  const today = () => C.todayIso(new Date());
  const medicine = id => C.byId(state.medicines, id);
  const customer = id => C.byId(state.customers, id);
  const supplier = id => C.byId(state.suppliers, id);

  // ---------------------------------------------------------------- storage

  function store(key, value) {
    try {
      if (value === undefined) {
        return localStorage.getItem(key);
      }
      localStorage.setItem(key, value);
    } catch (e) {
      return null; // private mode or blocked storage: the demo still works for this visit
    }
    return null;
  }

  function session(key, value) {
    try {
      if (value === undefined) {
        return sessionStorage.getItem(key);
      }
      if (value === null) {
        sessionStorage.removeItem(key);
      } else {
        sessionStorage.setItem(key, value);
      }
    } catch (e) {
      return null;
    }
    return null;
  }

  function loadState() {
    try {
      const saved = JSON.parse(store(STORE_KEY) || 'null');
      if (saved && saved.version === 1 && Array.isArray(saved.sales)) {
        return saved;
      }
    } catch (e) {
      // corrupt or old data: start fresh below
    }
    return C.createDemoState(new Date());
  }

  function save() {
    store(STORE_KEY, JSON.stringify(state));
  }

  let state = loadState();
  save();

  // ---------------------------------------------------------------- toasts, tooltips, modals

  function toast(message, bad) {
    const t = document.createElement('div');
    t.className = 'toast' + (bad ? ' bad' : '');
    t.setAttribute('role', 'status');
    t.textContent = message;
    $('#toasts').appendChild(t);
    setTimeout(() => t.remove(), bad ? 6000 : 3500);
  }

  function showError(e) {
    if (e instanceof C.DemoError) {
      toast(e.message, true);
    } else {
      console.error(e);
      toast('Something went wrong: ' + e.message, true);
    }
  }

  const tip = $('#tip');
  document.addEventListener('mousemove', e => {
    const el = e.target.closest && e.target.closest('[data-tip]');
    if (!el) {
      tip.hidden = true;
      return;
    }
    tip.textContent = el.getAttribute('data-tip');
    tip.hidden = false;
    const x = Math.min(e.clientX + 14, window.innerWidth - tip.offsetWidth - 8);
    tip.style.left = x + 'px';
    tip.style.top = (e.clientY - 38) + 'px';
  });

  /** Opens a modal; returns its element. onSubmit(modalEl) may throw DemoError to keep it open. */
  function modal(title, bodyHtml, opts) {
    const o = opts || {};
    const wrap = document.createElement('div');
    wrap.className = 'overlay';
    wrap.innerHTML = '<div class="modal' + (o.wide ? ' wide' : '') + '" role="dialog" aria-modal="true" aria-label="' + esc(title) + '">'
      + '<header><h2>' + esc(title) + '</h2><button class="icon-btn" data-close title="Close">' + icon('x') + '</button></header>'
      + '<div class="body">' + bodyHtml + '<div class="error" data-error hidden></div></div>'
      + '<footer>' + (o.footer || '') + (o.submit ? '<button class="btn" data-close>Cancel</button><button class="btn primary" data-submit>'
        + esc(o.submit) + '</button>' : (o.footer ? '' : '<button class="btn" data-close>Close</button>')) + '</footer></div>';
    const close = () => {
      wrap.remove();
      document.removeEventListener('keydown', onKey);
      if (o.onClose) {
        o.onClose();
      }
    };
    const onKey = e => {
      if (e.key === 'Escape') {
        close();
      }
    };
    document.addEventListener('keydown', onKey);
    wrap.addEventListener('click', e => {
      if (e.target === wrap || e.target.closest('[data-close]')) {
        close();
      }
    });
    if (o.submit) {
      const submit = () => {
        const err = $('[data-error]', wrap);
        try {
          o.onSubmit(wrap);
          close();
        } catch (e) {
          if (e instanceof C.DemoError) {
            err.textContent = e.message;
            err.hidden = false;
          } else {
            showError(e);
          }
        }
      };
      $('[data-submit]', wrap).addEventListener('click', submit);
      wrap.addEventListener('keydown', e => {
        if (e.key === 'Enter' && e.target.tagName === 'INPUT') {
          submit();
        }
      });
    }
    wrap.close = close;
    $('#modals').appendChild(wrap);
    const first = $('input, select', wrap);
    if (first) {
      first.focus();
    }
    return wrap;
  }

  function confirmBox(title, message, yes) {
    return new Promise(resolve => {
      let answered = false;
      modal(title, '<p style="margin:0">' + esc(message) + '</p>', {
        submit: yes, onSubmit: () => {
          answered = true;
          resolve(true);
        }, onClose: () => {
          if (!answered) {
            resolve(false);
          }
        }
      });
    });
  }

  // ---------------------------------------------------------------- routing & shell

  const SCREENS = [
    { key: 'dashboard', title: 'Dashboard', section: 'Overview', icon: 'dashboard' },
    { key: 'alerts', title: 'Stock Alerts', section: 'Overview', icon: 'alert' },
    { key: 'sales', title: 'New Sales Order', section: 'Sales', icon: 'cart' },
    { key: 'invoices', title: 'Invoices', section: 'Sales', icon: 'invoice' },
    { key: 'customers', title: 'Customers', section: 'Sales', icon: 'users' },
    { key: 'medicines', title: 'Medicines', section: 'Inventory', icon: 'pill' },
    { key: 'batches', title: 'Batches & Expiry', section: 'Inventory', icon: 'layers' },
    { key: 'receive', title: 'Receive Stock', section: 'Purchasing', icon: 'truck' }
  ];

  function route() {
    const key = (location.hash.replace(/^#\/?/, '') || 'dashboard').split('?')[0];
    return SCREENS.find(s => s.key === key) ? key : 'dashboard';
  }

  function buildNav() {
    const t = today();
    const alerts = C.lowStock(state, t).length + C.nearExpiry(state, t).length + C.expiredInStock(state, t).length;
    let html = '';
    let section = null;
    for (const s of SCREENS) {
      if (s.section !== section) {
        section = s.section;
        html += '<h6>' + section.toUpperCase() + '</h6>';
      }
      html += '<a href="#/' + s.key + '" data-key="' + s.key + '">' + icon(s.icon) + '<span>' + s.title + '</span>'
        + (s.key === 'alerts' && alerts ? '<span class="badge">' + alerts + '</span>' : '') + '</a>';
    }
    $('#nav').innerHTML = html;
  }

  function render() {
    tip.hidden = true;
    const key = route();
    const screen = SCREENS.find(s => s.key === key);
    buildNav();
    $$('#nav a').forEach(a => a.classList.toggle('active', a.dataset.key === key));
    $('#crumbs').innerHTML = esc(screen.section) + ' &nbsp;/&nbsp; <b>' + esc(screen.title) + '</b>';
    document.title = screen.title + ' - PharmaSync Demo';
    // A fresh container per render, so screen event handlers never pile up.
    const screenEl = document.createElement('div');
    screenEl.innerHTML = VIEWS[key]();
    $('#view').replaceChildren(screenEl);
    if (BIND[key]) {
      BIND[key](screenEl);
    }
  }

  function go(key) {
    if (route() === key) {
      render();
    } else {
      location.hash = '#/' + key;
    }
  }

  function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    $('[data-action="theme"]').innerHTML = icon(theme === 'dark' ? 'sun' : 'moon');
  }

  function shell() {
    $('[data-action="logout"]').innerHTML = icon('logout');
    $('[data-action="reset"]').innerHTML = icon('refresh') + '<span>Reset demo</span>';
    $('.top a.primary').innerHTML = icon('download') + '<span>Download app</span>';
    const saved = store(THEME_KEY);
    applyTheme(saved || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));
    document.addEventListener('click', async e => {
      const a = e.target.closest('[data-action]');
      if (!a) {
        return;
      }
      const act = a.dataset.action;
      if (act === 'theme') {
        const next = document.documentElement.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
        store(THEME_KEY, next);
        applyTheme(next);
        render();
      } else if (act === 'reset') {
        if (await confirmBox('Reset demo', 'Throw away your changes and start again with the original sample data?', 'Reset')) {
          state = C.createDemoState(new Date());
          draft = newDraft();
          receipt = newReceipt();
          save();
          render();
          toast('Demo data reset.');
        }
      } else if (act === 'logout') {
        session(AUTH_KEY, null);
        start();
      }
    });
    window.addEventListener('hashchange', render);
  }

  // ---------------------------------------------------------------- login

  function login() {
    const el = $('#login');
    el.innerHTML = '<div class="brand-side">'
      + '<img src="/assets/logo-mark.png" alt="PharmaSync">'
      + '<p>Try the app right here: place an order and watch FEFO pick the batches, print a GST invoice, take a payment '
      + 'and receive new stock. Everything uses sample data and stays in this browser.</p>'
      + '<ul>' + ['Batch-wise stock with FEFO dispatch', 'Expiry and low-stock alerts', 'GST invoices with CGST/SGST or IGST',
        'Customer payments and balances'].map(f => '<li>' + icon('check') + esc(f) + '</li>').join('') + '</ul>'
      + '<p style="margin-top:auto;font-size:13px;color:#94A3B8">The full desktop app adds purchase orders, reports, '
      + 'users and roles, audit log, PDF export and more. <a href="' + DOWNLOAD + '" style="color:#5EEAD4">Download it</a>.</p>'
      + '</div><div class="form-side"><form novalidate>'
      + '<div><h1>Welcome to the demo</h1><p class="hint" style="margin:4px 0 0">Sign in with the demo account (already filled in).</p></div>'
      + '<label class="f">Username<input type="text" name="u" value="admin" autocomplete="off"></label>'
      + '<label class="f">Password<input type="password" name="p" value="admin123" autocomplete="off"></label>'
      + '<div class="error" data-error hidden></div>'
      + '<button class="btn primary" type="submit">Sign in to demo</button>'
      + '<div class="note">Demo account: <b>admin</b> / <b>admin123</b>. No real data is used and nothing is sent to a server.</div>'
      + '<a class="hint" href="/" style="text-align:center">&larr; Back to the PharmaSync website</a>'
      + '</form></div>';
    $('form', el).addEventListener('submit', e => {
      e.preventDefault();
      const u = e.target.u.value.trim();
      const p = e.target.p.value;
      if (u === 'admin' && p === 'admin123') {
        session(AUTH_KEY, '1');
        start();
      } else {
        const err = $('[data-error]', el);
        err.textContent = 'Wrong username or password. Use admin / admin123.';
        err.hidden = false;
      }
    });
  }

  function start() {
    const signedIn = session(AUTH_KEY) === '1';
    $('#login').hidden = signedIn;
    $('#app').hidden = !signedIn;
    if (signedIn) {
      render();
    } else {
      document.title = 'Sign in - PharmaSync Demo';
      login();
    }
  }

  // ---------------------------------------------------------------- small render helpers

  function head(title, sub, actions) {
    return '<div class="page-head"><div><h1>' + esc(title) + '</h1>' + (sub ? '<p>' + sub + '</p>' : '') + '</div>'
      + (actions ? '<div class="actions">' + actions + '</div>' : '') + '</div>';
  }

  function table(cols, rows, opts) {
    const o = opts || {};
    const th = cols.map(c => '<th class="' + (c.num ? 'num' : '') + '">' + esc(c.label) + '</th>').join('');
    const body = rows.length ? rows.join('') : '<tr><td class="empty" colspan="' + cols.length + '">' + esc(o.empty || 'No records to show') + '</td></tr>';
    return '<div class="card table-wrap"' + (o.maxHeight ? ' style="max-height:' + o.maxHeight + '"' : '') + '><table><thead><tr>'
      + th + '</tr></thead><tbody>' + body + '</tbody></table></div>';
  }

  function td(v, cls) {
    return '<td' + (cls ? ' class="' + cls + '"' : '') + '>' + v + '</td>';
  }

  function searchBox(id, placeholder, value) {
    return '<label class="search">' + icon('search') + '<input type="search" id="' + id + '" placeholder="' + esc(placeholder)
      + '" value="' + esc(value || '') + '" aria-label="' + esc(placeholder) + '"></label>';
  }

  function medicineOptions(selectedId, withStock) {
    const t = today();
    return state.medicines.filter(m => m.active).sort((a, b) => a.brand.localeCompare(b.brand)).map(m => '<option value="' + m.id + '"'
      + (m.id === Number(selectedId) ? ' selected' : '') + '>' + esc(m.brand + ' - ' + m.generic)
      + (withStock ? '  [stock ' + C.sellableStock(state, m.id, t) + ']' : '') + '</option>').join('');
  }

  // ---------------------------------------------------------------- dashboard

  function barChart(points, opts) {
    const W = 560;
    const H = 300;
    const horizontal = opts.horizontal;
    if (!points.length) {
      return '<p class="empty">No sales yet</p>';
    }
    const max = Math.max(1, ...points.map(p => p.value));
    let svg = '<svg viewBox="0 0 ' + W + ' ' + H + '" role="img" aria-label="' + esc(opts.label) + '">';
    if (horizontal) {
      const left = 130;
      const right = 50;
      const rowH = (H - 16) / points.length;
      points.forEach((p, i) => {
        const y = 8 + i * rowH;
        const w = (W - left - right) * p.value / max;
        const tipText = esc(p.tip);
        svg += '<text x="' + (left - 8) + '" y="' + (y + rowH / 2 + 4) + '" text-anchor="end" data-tip="' + tipText + '">' + esc(p.label) + '</text>'
          + '<rect class="bar" x="' + left + '" y="' + (y + rowH * 0.18) + '" width="' + Math.max(w, 2) + '" height="' + rowH * 0.64
          + '" rx="4" fill="' + opts.color + '" data-tip="' + tipText + '"></rect>'
          + '<text class="val" x="' + (left + w + 6) + '" y="' + (y + rowH / 2 + 4) + '">' + esc(p.short) + '</text>';
      });
    } else {
      const left = 56;
      const bottom = 44;
      const plotH = H - bottom - 12;
      const step = (W - left - 8) / points.length;
      for (let g = 0; g <= 4; g++) {
        const y = 12 + plotH - plotH * g / 4;
        svg += '<line x1="' + left + '" x2="' + W + '" y1="' + y + '" y2="' + y + '"></line>'
          + '<text x="' + (left - 6) + '" y="' + (y + 4) + '" text-anchor="end">' + Math.round(max * g / 4 / 100).toLocaleString('en-IN') + '</text>';
      }
      points.forEach((p, i) => {
        const h = plotH * p.value / max;
        const x = left + i * step + step * 0.18;
        const tipText = esc(p.tip);
        // Invisible full-height hover area first, then the bar on top (so the bar still gets its hover style).
        svg += '<rect x="' + (left + i * step) + '" y="12" width="' + step + '" height="' + plotH + '" fill="transparent" data-tip="' + tipText + '"></rect>'
          + '<rect class="bar" x="' + x + '" y="' + (12 + plotH - h) + '" width="' + step * 0.64 + '" height="' + Math.max(h, p.value ? 2 : 0)
          + '" rx="3" fill="' + opts.color + '" data-tip="' + tipText + '"></rect>'
          // Label every other day so the dates do not run into each other.
          + (i % 2 === points.length % 2 ? '' : '<text x="' + (x + step * 0.32) + '" y="' + (H - bottom + 18) + '" text-anchor="middle" data-tip="'
            + tipText + '">' + esc(p.label) + '</text>');
      });
    }
    return svg + '</svg>';
  }

  function tile(label, value, caption, ic, tone, target) {
    const colors = { ok: ['--ok-bg', '--ok-fg'], warn: ['--warn-bg', '--warn-fg'], bad: ['--bad-bg', '--bad-fg'], info: ['--info-bg', '--info-fg'] }[tone];
    return '<a class="card tile" href="#/' + target + '"><div class="ic" style="background:var(' + colors[0] + ');color:var(' + colors[1] + ')">'
      + icon(ic) + '</div><div style="min-width:0"><small>' + esc(label) + '</small><b>' + esc(value) + '</b><span>' + esc(caption) + '</span></div></a>';
  }

  const VIEWS = {};
  const BIND = {};

  VIEWS.dashboard = () => {
    const now = new Date();
    const k = C.dashboard(state, now);
    const hour = now.getHours();
    const greet = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
    const daily = C.dailySales(state, 14, now).map(d => ({ label: fmtDate(d.day).slice(0, 6), value: d.total,
      tip: fmtDate(d.day) + ': ' + money(d.total) }));
    const top = C.topMedicines(state, 30, 7, now).map(t => ({ label: t.medicine.brand, value: t.qty, short: String(t.qty),
      tip: t.medicine.brand + ': ' + t.qty + ' units sold' }));
    return head(greet + ', Store Administrator', 'Here is how the demo store is doing today, ' + fmtDate(today()) + '.',
      '<a class="btn primary" href="#/sales">' + icon('plus') + 'New sales order</a>')
      + '<div class="tiles">'
      + tile("Today's sales", money(k.todaySales), k.todayInvoices + ' invoice' + (k.todayInvoices === 1 ? '' : 's') + ' today', 'trending', 'ok', 'invoices')
      + tile('This month', money(k.monthSales), 'Sales since the 1st', 'chart', 'ok', 'invoices')
      + tile('Receivables', money(k.receivables), 'Balance due from customers', 'wallet', k.receivables > 0 ? 'warn' : 'ok', 'invoices')
      + tile('Stock value', money(k.stockValue), 'Sellable stock at cost', 'package', 'info', 'batches')
      + tile('Active medicines', String(k.activeMedicines), 'In the catalogue', 'pill', 'info', 'medicines')
      + tile('Low stock', String(k.lowStock), k.lowStock ? 'Below reorder level' : 'All above reorder level', 'alert', k.lowStock ? 'warn' : 'ok', 'alerts')
      + tile('Near expiry', String(k.nearExpiry), k.nearExpiry ? 'Batches to sell first' : 'Nothing expiring soon', 'clock', k.nearExpiry ? 'warn' : 'ok', 'alerts')
      + tile('Expired in stock', String(k.expired), k.expired ? 'Blocked from sale' : 'No expired stock', 'octagon', k.expired ? 'bad' : 'ok', 'alerts')
      + '</div><div class="charts">'
      + '<div class="card pad chart"><h3>Sales trend <small>Invoice value per day, last 14 days (Rs)</small></h3>'
      + barChart(daily, { color: 'var(--accent)', label: 'Sales per day' }) + '</div>'
      + '<div class="card pad chart"><h3>Top sellers <small>Units sold, last 30 days</small></h3>'
      + barChart(top, { horizontal: true, color: '#6366F1', label: 'Top selling medicines' }) + '</div></div>'
      + '<p class="hint" style="margin-top:14px">Hover the bars or labels for exact figures. Tiles open the matching screen.</p>';
  };

  // ---------------------------------------------------------------- alerts

  let alertTab = 'low';

  VIEWS.alerts = () => {
    const t = today();
    const low = C.lowStock(state, t);
    const near = C.nearExpiry(state, t);
    const expired = C.expiredInStock(state, t);
    const tab = (key, label, n) => '<button class="' + (alertTab === key ? 'on' : '') + '" data-tab="' + key + '">' + label + ' (' + n + ')</button>';
    let body;
    if (alertTab === 'low') {
      body = table([{ label: 'Code' }, { label: 'Medicine' }, { label: 'Category' }, { label: 'Valid stock', num: 1 },
        { label: 'Reorder level', num: 1 }, { label: 'Shortfall', num: 1 }, { label: 'Suggested order', num: 1 }, { label: '' }],
      low.map(x => '<tr>' + td(esc(x.medicine.code)) + td(esc(x.medicine.brand) + ' <span class="sub">' + esc(x.medicine.generic) + '</span>')
        + td(esc(x.medicine.category)) + td(x.stock, 'num') + td(x.medicine.reorder, 'num') + td(x.shortfall, 'num') + td('<b>' + x.suggested + '</b>', 'num')
        + td('<button class="btn small" data-restock="' + x.medicine.id + '" data-qty="' + x.suggested + '">' + icon('truck') + 'Receive stock</button>') + '</tr>'),
      { empty: 'Every medicine is above its reorder level.' })
        + '<p class="hint">Valid stock counts only active, unexpired batches. Suggested order brings stock back to twice the reorder level.</p>';
    } else {
      const list = alertTab === 'near' ? near : expired;
      body = table([{ label: 'Batch' }, { label: 'Medicine' }, { label: 'Expiry date' }, { label: 'Days left', num: 1 },
        { label: 'Qty', num: 1 }, { label: 'Value at cost', num: 1 }, { label: 'Status' }],
      list.map(b => '<tr class="' + (alertTab === 'near' ? 't-amber' : 't-red') + '">' + td(esc(b.batchNo)) + td(esc(medicine(b.medicineId).brand))
        + td(fmtDate(b.expiry)) + td(C.daysBetween(t, b.expiry), 'num') + td(b.qty, 'num') + td(plain(b.qty * b.cost), 'num')
        + td(pill(alertTab === 'near' ? 'NEAR_EXPIRY' : 'EXPIRED')) + '</tr>'),
      { empty: alertTab === 'near' ? 'Nothing expires in the next ' + C.NEAR_EXPIRY_DAYS + ' days.' : 'No expired stock.' })
        + '<p class="hint">' + (alertTab === 'near'
          ? 'These are dispatched first automatically (FEFO). Consider returning slow movers to the supplier.'
          : 'Expired batches are blocked from sale: FEFO skips them and picking one by hand is refused.') + '</p>';
    }
    return head('Stock Alerts', 'Near-expiry threshold: ' + C.NEAR_EXPIRY_DAYS + ' days')
      + '<div class="tabs">' + tab('low', 'Low stock', low.length) + tab('near', 'Near expiry', near.length) + tab('expired', 'Expired', expired.length) + '</div>'
      + '<div class="stack">' + body + '</div>';
  };

  BIND.alerts = view => {
    view.addEventListener('click', e => {
      const t = e.target.closest('[data-tab]');
      if (t) {
        alertTab = t.dataset.tab;
        render();
      }
      const r = e.target.closest('[data-restock]');
      if (r) {
        receipt.medicineId = Number(r.dataset.restock);
        receipt.qty = r.dataset.qty;
        go('receive');
      }
    });
  };

  // ---------------------------------------------------------------- new sales order

  function newDraft() {
    return { customerId: 1, mode: 'CREDIT', status: 'UNPAID', amount: '', medicineId: null, batchId: '', qty: '', rate: '', disc: '0', lines: [] };
  }

  let draft = newDraft();

  /** Units of a medicine (or batch) already in the draft order. */
  function inDraft(medicineId, batchId) {
    return draft.lines.filter(l => l.medicineId === medicineId && (!batchId || l.batchId === batchId)).reduce((s, l) => s + l.qty, 0);
  }

  function draftAmounts(l) {
    return C.lineAmounts(l.qty, l.price, l.discBp, medicine(l.medicineId).gstBp);
  }

  VIEWS.sales = () => {
    if (!draft.medicineId) {
      draft.medicineId = state.medicines.find(m => m.code === 'MED001').id;
    }
    const t = today();
    const med = medicine(draft.medicineId);
    const batches = C.fefoOrder(C.batchesOf(state, med.id), t);
    const lines = draft.lines.map((l, i) => {
      const m = medicine(l.medicineId);
      const a = draftAmounts(l);
      const b = l.batchId ? C.byId(state.batches, l.batchId) : null;
      return '<tr>' + td(esc(m.brand) + ' <span class="sub">' + esc(m.generic) + '</span>') + td(b ? esc(b.batchNo) : '<span class="sub">Auto (FEFO)</span>')
        + td(l.qty, 'num') + td(plain(l.price), 'num') + td((l.discBp / 100).toFixed(2), 'num') + td((m.gstBp / 100).toFixed(2), 'num')
        + td(plain(a.taxable), 'num') + td(plain(a.tax), 'num') + td('<b>' + plain(a.total) + '</b>', 'num')
        + td('<button class="btn small danger" data-remove="' + i + '" title="Remove line">' + icon('trash') + '</button>') + '</tr>';
    });
    return head('New Sales Order', 'Pick a customer, add medicines and confirm. Stock is taken first-expiry-first-out.')
      + '<div class="order">'
      + '<div class="stack">'
      + '<div class="card pad"><h3>Order</h3><div class="grid g2">'
      + '<label class="f">Customer<select id="o-customer">' + state.customers.map(c => '<option value="' + c.id + '"' + (c.id === draft.customerId ? ' selected' : '')
        + '>' + esc(c.name) + ' (' + esc(C.STATES[c.state]) + ')</option>').join('') + '</select></label>'
      + '<label class="f">Payment mode<select id="o-mode">' + ['CREDIT', 'CASH', 'UPI', 'NEFT', 'CHEQUE'].map(m => '<option' + (m === draft.mode ? ' selected' : '') + '>' + m + '</option>').join('') + '</select></label>'
      + '<label class="f">Payment status<select id="o-status">' + ['UNPAID', 'PAID', 'PARTIAL'].map(s => '<option value="' + s + '"' + (s === draft.status ? ' selected' : '') + '>' + words(s) + '</option>').join('') + '</select></label>'
      + '<label class="f">Amount received now<input type="text" id="o-amount" inputmode="decimal" placeholder="Only for partial" value="' + esc(draft.amount) + '"' + (draft.status === 'PARTIAL' ? '' : ' disabled') + '></label>'
      + '</div></div>'
      + '<div class="card pad"><h3>Add medicine to order</h3><div class="grid g2">'
      + '<label class="f" style="grid-column:1/-1">Medicine<select id="o-med">' + medicineOptions(draft.medicineId, true) + '</select></label>'
      + '<label class="f">Batch<select id="o-batch"><option value="">Auto (FEFO)</option>' + batches.map(b => '<option value="' + b.id + '"'
        + (String(b.id) === String(draft.batchId) ? ' selected' : '') + '>' + esc(b.batchNo) + ' - exp ' + fmtDate(b.expiry) + ' - ' + (b.qty - inDraft(med.id, b.id)) + ' left</option>').join('') + '</select></label>'
      + '<label class="f">Quantity<input type="number" id="o-qty" min="1" step="1" placeholder="e.g. 120" value="' + esc(draft.qty) + '"></label>'
      + '<label class="f">Rate (Rs)<input type="text" id="o-rate" inputmode="decimal" value="' + esc(draft.rate || (med.price / 100).toFixed(2)) + '"></label>'
      + '<label class="f">Discount %<input type="text" id="o-disc" inputmode="decimal" value="' + esc(draft.disc) + '"></label>'
      + '</div><div class="row" style="margin-top:12px"><button class="btn primary" id="o-add">' + icon('plus') + 'Add to order</button>'
      + '<span class="hint" id="o-avail"></span></div></div>'
      + '</div>'
      + '<div class="card pad"><h3>FEFO dispatch suggestion <small>which batches this quantity will come from</small></h3><div id="o-fefo"></div></div>'
      + '</div>'
      + '<h3 style="margin:20px 0 10px">Order lines</h3>'
      + table([{ label: 'Medicine' }, { label: 'Batch' }, { label: 'Qty', num: 1 }, { label: 'Rate', num: 1 }, { label: 'Disc %', num: 1 },
        { label: 'GST %', num: 1 }, { label: 'Taxable', num: 1 }, { label: 'GST', num: 1 }, { label: 'Amount', num: 1 }, { label: '' }], lines,
      { empty: 'No medicines added yet. Add one above.' })
      + '<div class="card totals" style="margin-top:14px" id="o-totals"></div>';
  };

  function salesTotals() {
    const t = C.totals(draft.lines.map(draftAmounts));
    const c = customer(draft.customerId);
    const type = C.supplyType(state.company.state, c.state);
    const split = C.gstSplit(t.tax, type, c.state);
    const gstText = type === 'INTER_STATE'
      ? 'inter-state supply, IGST ' + money(split.igst)
      : 'intra-state supply, CGST ' + money(split.cgst) + ' + ' + split.stateTaxName + ' ' + money(split.sgst);
    $('#o-totals').innerHTML = '<div class="figs"><div>Items<b>' + draft.lines.length + '</b></div><div>Gross<b>' + money(t.subtotal) + '</b></div>'
      + '<div>Discount<b>' + money(t.discount) + '</b></div><div>GST<b>' + money(t.tax) + '</b></div><div class="grand">Total<b>' + money(t.total) + '</b></div></div>'
      + '<div style="margin-left:auto" class="row"><button class="btn" id="o-clear"' + (draft.lines.length ? '' : ' disabled') + '>' + icon('x') + 'Clear</button>'
      + '<button class="btn primary" id="o-confirm"' + (draft.lines.length ? '' : ' disabled') + '>' + icon('check') + 'Confirm order &amp; generate invoice</button></div>'
      + '<div class="gst">Place of supply: <b>' + esc(C.stateLabel(c.state)) + '</b> &middot; company in ' + esc(C.stateLabel(state.company.state)) + ' &middot; ' + gstText + '</div>';
  }

  function updateFefo() {
    const t = today();
    const med = medicine(draft.medicineId);
    const qty = parseInt(draft.qty, 10) || 0;
    const used = {};
    for (const l of draft.lines.filter(x => x.medicineId === med.id)) {
      if (l.batchId) {
        used[l.batchId] = (used[l.batchId] || 0) + l.qty;
      } else {
        // FEFO lines already in the order take from the earliest batches first.
        let need = l.qty;
        for (const b of C.fefoOrder(C.batchesOf(state, med.id), t)) {
          const take = Math.min(need, b.qty - (used[b.id] || 0));
          if (take > 0) {
            used[b.id] = (used[b.id] || 0) + take;
            need -= take;
          }
        }
      }
    }
    const order = C.fefoOrder(C.batchesOf(state, med.id), t);
    const free = order.reduce((s, b) => s + Math.max(b.qty - (used[b.id] || 0), 0), 0);
    let remaining = qty;
    const rows = order.map((b, i) => {
      const left = Math.max(b.qty - (used[b.id] || 0), 0);
      const take = draft.batchId ? (String(b.id) === String(draft.batchId) ? Math.min(qty, left) : 0) : Math.min(remaining, left);
      if (!draft.batchId) {
        remaining -= take;
      }
      const st = C.expiryStatus(b, t);
      return '<tr' + (take ? ' class="t-amber"' : '') + '>' + td(i + 1, 'num') + td(esc(b.batchNo)) + td(fmtDate(b.expiry) + ' ' + (st === 'NEAR_EXPIRY' ? pill(st) : ''))
        + td(C.daysBetween(t, b.expiry), 'num') + td(left, 'num') + td('<b>' + take + '</b>', 'num') + '</tr>';
    });
    const blocked = C.batchesOf(state, med.id).filter(b => b.qty > 0 && !C.isSellable(b, t)).length;
    $('#o-fefo').innerHTML = table([{ label: '#', num: 1 }, { label: 'Batch' }, { label: 'Expiry' }, { label: 'Days left', num: 1 },
      { label: 'Available', num: 1 }, { label: 'Dispatch', num: 1 }], rows, { empty: 'No valid stock for this medicine.' })
      + '<p class="hint" style="margin:8px 0 0">' + (blocked ? blocked + ' expired / blocked batch' + (blocked > 1 ? 'es are' : ' is') + ' excluded. ' : '')
      + (qty ? (qty > free ? '<b style="color:var(--bad-fg)">Short by ' + (qty - free) + ' units: only ' + free + ' valid units available.</b>' : 'Enough valid stock.') : 'Enter a quantity to see which batches will be dispatched.') + '</p>';
    $('#o-avail').textContent = 'Valid stock available: ' + free;
  }

  BIND.sales = view => {
    const val = id => $('#' + id, view).value;
    const sync = () => {
      draft.customerId = Number(val('o-customer'));
      draft.mode = val('o-mode');
      draft.status = val('o-status');
      draft.amount = val('o-amount');
      draft.batchId = val('o-batch');
      draft.qty = val('o-qty');
      draft.rate = val('o-rate');
      draft.disc = val('o-disc');
    };
    view.addEventListener('input', e => {
      sync();
      if (e.target.id === 'o-qty' || e.target.id === 'o-batch') {
        updateFefo();
      }
    });
    view.addEventListener('change', e => {
      sync();
      if (e.target.id === 'o-med') {
        draft.medicineId = Number(val('o-med'));
        draft.batchId = '';
        draft.rate = '';
        render();
      } else if (e.target.id === 'o-status') {
        $('#o-amount', view).disabled = draft.status !== 'PARTIAL';
        if (draft.status === 'PARTIAL') {
          $('#o-amount', view).focus();
        }
      } else if (e.target.id === 'o-customer') {
        salesTotals();
      }
    });
    view.addEventListener('click', async e => {
      const rm = e.target.closest('[data-remove]');
      if (rm) {
        draft.lines.splice(Number(rm.dataset.remove), 1);
        render();
        return;
      }
      if (e.target.closest('#o-add')) {
        await addLine();
      } else if (e.target.closest('#o-clear')) {
        draft.lines = [];
        render();
      } else if (e.target.closest('#o-confirm')) {
        confirmOrder();
      }
    });
    updateFefo();
    salesTotals();
  };

  async function addLine() {
    try {
      const t = today();
      const med = medicine(draft.medicineId);
      let qty = parseInt(draft.qty, 10);
      if (!qty || qty <= 0 || String(qty) !== String(draft.qty).trim()) {
        throw new C.DemoError('Enter a whole-number quantity greater than zero');
      }
      const price = C.parseMoney(draft.rate || (med.price / 100).toFixed(2), 'Rate');
      const discBp = C.parsePercent(draft.disc, 'Discount');
      C.lineAmounts(qty, price, discBp, med.gstBp); // validates
      const batchId = draft.batchId ? Number(draft.batchId) : null;
      const free = batchId
        ? C.byId(state.batches, batchId).qty - inDraft(med.id, batchId)
        : C.sellableStock(state, med.id, t) - inDraft(med.id);
      if (free <= 0) {
        throw new C.DemoError('No valid stock of ' + med.brand + ' is left for this order.');
      }
      if (qty > free) {
        const ok = await confirmBox('Not enough stock', 'Only ' + free + ' valid units of ' + med.brand + ' are available (requested '
          + qty + '). Add ' + free + ' units and reject the remaining ' + (qty - free) + '?', 'Add ' + free + ' units');
        if (!ok) {
          return;
        }
        qty = free;
      }
      draft.lines.push({ medicineId: med.id, qty, price, discBp, batchId });
      draft.qty = '';
      draft.batchId = '';
      render();
      toast(qty + ' x ' + med.brand + ' added to the order.');
    } catch (e) {
      showError(e);
    }
  }

  function confirmOrder() {
    try {
      const amount = draft.status === 'PARTIAL' ? C.parseMoney(draft.amount, 'Amount received') : null;
      const sale = C.createSale(state, {
        customerId: draft.customerId, mode: draft.mode, paymentStatus: draft.status, amountReceived: amount,
        lines: draft.lines.map(l => ({ medicineId: l.medicineId, qty: l.qty, batchId: l.batchId, price: l.price, discBp: l.discBp }))
      }, new Date());
      save();
      const keepCustomer = draft.customerId;
      draft = newDraft();
      draft.customerId = keepCustomer;
      render();
      toast('Invoice ' + sale.invoiceNo + ' saved. Stock updated.');
      showInvoice(sale.id, true);
    } catch (e) {
      showError(e);
    }
  }

  // ---------------------------------------------------------------- invoices

  let invoiceSearch = '';

  VIEWS.invoices = () => {
    const q = invoiceSearch.toLowerCase();
    const list = state.sales.slice().sort((a, b) => (a.when < b.when ? 1 : a.when > b.when ? -1 : b.id - a.id))
      .filter(s => !q || s.invoiceNo.toLowerCase().includes(q) || customer(s.customerId).name.toLowerCase().includes(q));
    const total = list.reduce((t, s) => t + s.total, 0);
    const paid = list.reduce((t, s) => t + s.paid, 0);
    return head('Invoices', list.length + ' invoices &middot; total ' + money(total) + ' &middot; received ' + money(paid)
      + ' &middot; <b>outstanding ' + money(total - paid) + '</b>', searchBox('inv-search', 'Customer or invoice no.', invoiceSearch))
      + table([{ label: 'Invoice' }, { label: 'Date' }, { label: 'Customer' }, { label: 'Tax type' }, { label: 'Total', num: 1 },
        { label: 'Paid', num: 1 }, { label: 'Balance', num: 1 }, { label: 'Payment' }, { label: '' }],
      list.map(s => '<tr class="click' + (s.status !== 'PAID' ? ' t-amber' : '') + '" data-open="' + s.id + '">' + td('<b>' + esc(s.invoiceNo) + '</b>')
        + td(fmtWhen(s.when)) + td(esc(customer(s.customerId).name)) + td(taxType(s.supplyType)) + td(plain(s.total), 'num')
        + td(plain(s.paid), 'num') + td(plain(s.total - s.paid), 'num') + td(pill(s.status))
        + td(s.total > s.paid ? '<button class="btn small" data-pay="' + s.id + '">' + icon('card') + 'Record payment</button>' : '') + '</tr>'),
      { maxHeight: 'calc(100vh - 220px)', empty: 'No invoices match your search.' })
      + '<p class="hint">Click an invoice to view or print it. Amber rows still have a balance due.</p>';
  };

  BIND.invoices = view => {
    const s = $('#inv-search', view);
    s.addEventListener('input', () => {
      invoiceSearch = s.value;
      const pos = s.selectionStart;
      render();
      const again = $('#inv-search');
      again.focus();
      again.setSelectionRange(pos, pos);
    });
    view.addEventListener('click', e => {
      const pay = e.target.closest('[data-pay]');
      if (pay) {
        e.stopPropagation();
        paymentDialog(Number(pay.dataset.pay));
        return;
      }
      const row = e.target.closest('[data-open]');
      if (row) {
        showInvoice(Number(row.dataset.open));
      }
    });
  };

  function paymentDialog(saleId, after) {
    const s = C.byId(state.sales, saleId);
    const c = customer(s.customerId);
    modal('Record payment - ' + s.invoiceNo,
      '<p class="hint" style="margin-top:0">' + esc(c.name) + ' &middot; total ' + money(s.total) + ' &middot; paid ' + money(s.paid)
      + ' &middot; <b>balance due ' + money(s.total - s.paid) + '</b></p>'
      + '<div class="grid g2"><label class="f">Amount received (Rs)<input type="text" name="amount" inputmode="decimal" value="' + ((s.total - s.paid) / 100).toFixed(2) + '"></label>'
      + '<label class="f">Mode<select name="mode">' + ['CASH', 'UPI', 'NEFT', 'CHEQUE', 'CARD'].map(m => '<option>' + m + '</option>').join('') + '</select></label>'
      + '<label class="f">Cheque / UTR / reference<input type="text" name="ref" placeholder="Optional"></label>'
      + '<label class="f">Payment date<input type="date" name="date" value="' + today() + '" min="' + s.when.slice(0, 10) + '" max="' + today() + '"></label></div>',
    { submit: 'Record payment', onSubmit: m => {
      const amount = C.parseMoney($('[name=amount]', m).value, 'Amount');
      C.recordPayment(state, saleId, amount, $('[name=mode]', m).value, $('[name=ref]', m).value.trim(), $('[name=date]', m).value || today());
      save();
      render();
      toast('Payment of ' + money(amount) + ' recorded for ' + s.invoiceNo + '. Status: ' + words(s.status) + '.');
      if (after) {
        after();
      }
    } });
  }

  function invoiceHtml(s) {
    const c = customer(s.customerId);
    const split = C.gstSplit(s.tax, s.supplyType, s.placeOfSupply);
    const items = s.items.map((i, n) => {
      const m = medicine(i.medicineId);
      return '<tr><td>' + (n + 1) + '</td><td class="wrap">' + esc(m.brand + ' (' + m.generic + ')') + '</td><td>' + esc(i.batchNo) + '</td><td>'
        + esc(i.expiry.slice(5, 7) + '/' + i.expiry.slice(0, 4)) + '</td><td class="num">' + i.qty + '</td><td class="num">' + plain(i.price)
        + '</td><td class="num">' + (i.discBp / 100).toFixed(2) + '</td><td class="num">' + (i.gstBp / 100).toFixed(2) + '</td><td class="num">' + plain(i.tax)
        + '</td><td class="num">' + plain(i.total) + '</td></tr>';
    }).join('');
    const taxRows = s.supplyType === 'INTER_STATE'
      ? '<div><span>IGST</span><span>' + plain(split.igst) + '</span></div>'
      : '<div><span>CGST</span><span>' + plain(split.cgst) + '</span></div><div><span>' + split.stateTaxName + '</span><span>' + plain(split.sgst) + '</span></div>';
    return '<div class="invoice">'
      + '<h4>' + esc(state.company.name) + '</h4><div class="co">' + esc(state.company.address) + ' &nbsp; State: ' + esc(C.stateLabel(state.company.state)) + '</div>'
      + '<div class="title">TAX INVOICE</div><div class="blocks"><div><b>Bill To</b><br>' + esc(c.name) + '<br>' + esc(c.city) + '<br>State: '
      + esc(C.stateLabel(c.state)) + '<br>GSTIN: ' + esc(c.gstin || '-') + '</div><div><b>Invoice</b><br>Invoice No: ' + esc(s.invoiceNo)
      + '<br>Date: ' + fmtWhen(s.when) + '<br>Place of supply: ' + esc(C.stateLabel(s.placeOfSupply)) + '<br>Supply: '
      + (s.supplyType === 'INTER_STATE' ? 'Inter-state (IGST)' : 'Intra-state (CGST + SGST)') + '<br>Payment: ' + esc(s.mode) + ' / ' + esc(s.status) + '</div></div>'
      + '<table><thead><tr><th>#</th><th>Medicine</th><th>Batch</th><th>Expiry</th><th class="num">Qty</th><th class="num">Rate</th><th class="num">Disc %</th>'
      + '<th class="num">GST %</th><th class="num">GST Amt</th><th class="num">Amount</th></tr></thead><tbody>' + items + '</tbody></table>'
      + '<div class="sum"><div><span>Gross amount</span><span>' + plain(s.subtotal) + '</span></div><div><span>Discount</span><span>- ' + plain(s.discount) + '</span></div>'
      + taxRows + '<div class="b"><span>Invoice total (INR)</span><span>' + plain(s.total) + '</span></div><div><span>Amount paid</span><span>' + plain(s.paid)
      + '</span></div><div class="b"><span>Balance due (INR)</span><span>' + plain(s.total - s.paid) + '</span></div></div>'
      + '<div class="foot">Medicines dispatched by FEFO (First Expiry, First Out). Generated by the PharmaSync browser demo.</div></div>';
  }

  function showInvoice(saleId, justCreated) {
    const s = C.byId(state.sales, saleId);
    const payments = state.payments.filter(p => p.saleId === s.id);
    const history = '<h3 style="margin:18px 0 8px;font-size:15px">Payment history</h3>' + (payments.length
      ? table([{ label: 'Date' }, { label: 'Amount', num: 1 }, { label: 'Mode' }, { label: 'Reference' }],
        payments.map(p => '<tr>' + td(fmtDate(p.date)) + td(plain(p.amount), 'num') + td(esc(p.mode)) + td(esc(p.reference || '-')) + '</tr>'))
      : '<p class="hint">No payments recorded yet.</p>');
    const m = modal((justCreated ? 'Invoice created - ' : 'Invoice ') + s.invoiceNo, invoiceHtml(s) + history, {
      wide: true,
      footer: (s.total > s.paid ? '<button class="btn" data-inv-pay>' + icon('card') + 'Record payment</button>' : '')
        + '<button class="btn" data-print>' + icon('printer') + 'Print</button><button class="btn primary" data-close>Done</button>'
    });
    const pay = $('[data-inv-pay]', m);
    if (pay) {
      pay.addEventListener('click', () => {
        m.close();
        paymentDialog(s.id, () => showInvoice(s.id));
      });
    }
    $('[data-print]', m).addEventListener('click', () => window.print());
  }

  // ---------------------------------------------------------------- customers

  VIEWS.customers = () => head('Customers', 'Pharmacies, hospitals and clinics. The state is the place of supply that decides CGST/SGST or IGST.')
    + table([{ label: 'Name' }, { label: 'Type' }, { label: 'City' }, { label: 'State' }, { label: 'GSTIN' }, { label: 'Tax on sales' },
      { label: 'Invoices', num: 1 }, { label: 'Outstanding', num: 1 }],
    state.customers.map(c => {
      const sales = state.sales.filter(s => s.customerId === c.id);
      const due = sales.reduce((t, s) => t + s.total - s.paid, 0);
      const type = C.supplyType(state.company.state, c.state);
      return '<tr>' + td('<b>' + esc(c.name) + '</b>') + td(pill(c.type)) + td(esc(c.city)) + td(esc(C.stateLabel(c.state))) + td(esc(c.gstin || '-'))
        + td(pill(type, taxType(type))) + td(sales.length, 'num') + td(plain(due), 'num') + '</tr>';
    }));

  // ---------------------------------------------------------------- medicines

  let medSearch = '';

  VIEWS.medicines = () => {
    const t = today();
    const q = medSearch.toLowerCase();
    const list = state.medicines.filter(m => !q || [m.code, m.brand, m.generic, m.category].join(' ').toLowerCase().includes(q))
      .sort((a, b) => a.brand.localeCompare(b.brand));
    return head('Medicines', 'Click a medicine to see its batches in FEFO order.', searchBox('med-search', 'Search code, brand or generic', medSearch))
      + table([{ label: 'Code' }, { label: 'Medicine' }, { label: 'Category' }, { label: 'Form' }, { label: 'Pack' }, { label: 'Price', num: 1 },
        { label: 'GST %', num: 1 }, { label: 'Valid stock', num: 1 }, { label: 'Reorder at', num: 1 }, { label: 'Stock' }],
      list.map(m => {
        const stock = C.sellableStock(state, m.id, t);
        const low = stock < m.reorder;
        return '<tr class="click' + (low ? ' t-amber' : '') + '" data-med="' + m.id + '">' + td(esc(m.code)) + td('<b>' + esc(m.brand) + '</b> <span class="sub">' + esc(m.generic) + '</span>')
          + td(esc(m.category)) + td(esc(m.form + ' ' + m.strength)) + td(esc(m.pack)) + td(plain(m.price), 'num') + td((m.gstBp / 100).toFixed(2), 'num')
          + td(stock, 'num') + td(m.reorder, 'num') + td(pill(low ? 'LOW_STOCK' : 'OK', low ? 'Low stock' : 'OK')) + '</tr>';
      }), { maxHeight: 'calc(100vh - 210px)', empty: 'No medicines match your search.' });
  };

  BIND.medicines = view => {
    const s = $('#med-search', view);
    s.addEventListener('input', () => {
      medSearch = s.value;
      const pos = s.selectionStart;
      render();
      const again = $('#med-search');
      again.focus();
      again.setSelectionRange(pos, pos);
    });
    view.addEventListener('click', e => {
      const row = e.target.closest('[data-med]');
      if (!row) {
        return;
      }
      const t = today();
      const m = medicine(row.dataset.med);
      const sellable = C.fefoOrder(C.batchesOf(state, m.id), t);
      const all = C.batchesOf(state, m.id).slice().sort((a, b) => (a.expiry < b.expiry ? -1 : 1));
      modal(m.brand + ' - batches', '<p class="hint" style="margin-top:0">' + esc(m.generic + ' · ' + m.form + ' ' + m.strength + ' · ' + m.pack)
        + '. Sales take stock from the top of this list first.</p>'
        + table([{ label: 'FEFO #', num: 1 }, { label: 'Batch' }, { label: 'Expiry' }, { label: 'Days left', num: 1 }, { label: 'Qty', num: 1 }, { label: 'Status' }],
          all.map(b => {
            const order = sellable.indexOf(b);
            const st = C.expiryStatus(b, t);
            return '<tr class="' + (st === 'EXPIRED' ? 't-red' : st === 'NEAR_EXPIRY' ? 't-amber' : '') + '">' + td(order >= 0 ? order + 1 : '-', 'num')
              + td(esc(b.batchNo)) + td(fmtDate(b.expiry)) + td(C.daysBetween(t, b.expiry), 'num') + td(b.qty, 'num')
              + td(b.qty === 0 ? pill('EMPTY', 'Empty') : pill(st)) + '</tr>';
          })), { wide: true });
    });
  };

  // ---------------------------------------------------------------- batches

  let batchSearch = '';
  let batchFilter = 'all';

  VIEWS.batches = () => {
    const t = today();
    const q = batchSearch.toLowerCase();
    const list = state.batches.filter(b => b.qty > 0)
      .filter(b => batchFilter === 'all' || C.expiryStatus(b, t) === batchFilter)
      .filter(b => !q || (b.batchNo + ' ' + medicine(b.medicineId).brand + ' ' + medicine(b.medicineId).code).toLowerCase().includes(q))
      .sort((a, b) => (a.expiry < b.expiry ? -1 : 1));
    const count = st => state.batches.filter(b => b.qty > 0 && C.expiryStatus(b, t) === st).length;
    return head('Batches & Expiry', list.length + ' batches in stock &middot; red = expired (' + count('EXPIRED') + ') &middot; amber = expiring within '
      + C.NEAR_EXPIRY_DAYS + ' days (' + count('NEAR_EXPIRY') + ')',
    searchBox('batch-search', 'Search batch, medicine or code', batchSearch)
      + '<select id="batch-filter" aria-label="Expiry filter">' + [['all', 'All statuses'], ['EXPIRED', 'Expired'], ['NEAR_EXPIRY', 'Near expiry'], ['VALID', 'Valid']]
        .map(([v, l]) => '<option value="' + v + '"' + (v === batchFilter ? ' selected' : '') + '>' + l + '</option>').join('') + '</select>')
      + table([{ label: 'Batch' }, { label: 'Medicine' }, { label: 'Mfg date' }, { label: 'Expiry date' }, { label: 'Days left', num: 1 }, { label: 'Qty', num: 1 },
        { label: 'Cost', num: 1 }, { label: 'MRP', num: 1 }, { label: 'Supplier' }, { label: 'Expiry status' }],
      list.map(b => {
        const st = C.expiryStatus(b, t);
        const m = medicine(b.medicineId);
        return '<tr class="' + (st === 'EXPIRED' ? 't-red' : st === 'NEAR_EXPIRY' ? 't-amber' : '') + '">' + td('<b>' + esc(b.batchNo) + '</b>')
          + td(esc(m.brand) + ' <span class="sub">' + esc(m.generic) + '</span>', 'wrap') + td(fmtDate(b.mfg)) + td(fmtDate(b.expiry)) + td(C.daysBetween(t, b.expiry), 'num')
          + td(b.qty, 'num') + td(plain(b.cost), 'num') + td(plain(b.mrp), 'num') + td(esc(supplier(b.supplierId).name), 'wrap') + td(pill(st)) + '</tr>';
      }), { maxHeight: 'calc(100vh - 210px)', empty: 'No batches match.' });
  };

  BIND.batches = view => {
    const s = $('#batch-search', view);
    s.addEventListener('input', () => {
      batchSearch = s.value;
      const pos = s.selectionStart;
      render();
      const again = $('#batch-search');
      again.focus();
      again.setSelectionRange(pos, pos);
    });
    $('#batch-filter', view).addEventListener('change', e => {
      batchFilter = e.target.value;
      render();
    });
  };

  // ---------------------------------------------------------------- receive stock

  function newReceipt() {
    return { supplierId: 1, ref: '', medicineId: null, batchNo: '', mfg: '', expiry: '', qty: '', cost: '', lines: [] };
  }

  let receipt = newReceipt();

  VIEWS.receive = () => {
    if (!receipt.medicineId) {
      receipt.medicineId = state.medicines[0].id;
    }
    const t = today();
    const med = medicine(receipt.medicineId);
    const sup = supplier(receipt.supplierId);
    const type = C.supplyType(sup.state, state.company.state);
    const lines = receipt.lines.map((l, i) => {
      const m = medicine(l.medicineId);
      const a = C.lineAmounts(l.qty, l.cost, 0, m.gstBp);
      return '<tr>' + td(esc(m.brand)) + td(esc(l.batchNo)) + td(fmtDate(l.mfg)) + td(fmtDate(l.expiry)) + td(l.qty, 'num') + td(plain(l.cost), 'num')
        + td(plain(a.tax), 'num') + td('<b>' + plain(a.total) + '</b>', 'num')
        + td('<button class="btn small danger" data-rline="' + i + '" title="Remove line">' + icon('trash') + '</button>') + '</tr>';
    });
    const tot = C.totals(receipt.lines.map(l => C.lineAmounts(l.qty, l.cost, 0, medicine(l.medicineId).gstBp)));
    const recent = state.purchases.slice().reverse().slice(0, 8).map(p => '<tr>' + td('<b>' + esc(p.poNo) + '</b>') + td(fmtDate(p.date))
      + td(esc(supplier(p.supplierId).name)) + td(esc(p.invoiceRef || '-')) + td(p.items.length, 'num') + td(pill(p.supplyType, taxType(p.supplyType)))
      + td(plain(p.total), 'num') + td(pill('RECEIVED')) + '</tr>');
    return head('Receive Stock', 'Record a delivery: each line becomes a batch with its own manufacturing and expiry date.')
      + '<div class="order"><div class="card pad"><h3>Shipment</h3><div class="grid g2">'
      + '<label class="f">Supplier<select id="r-sup">' + state.suppliers.map(s => '<option value="' + s.id + '"' + (s.id === receipt.supplierId ? ' selected' : '') + '>'
        + esc(s.name) + ' (' + esc(C.STATES[s.state]) + ')</option>').join('') + '</select></label>'
      + '<label class="f">Supplier invoice no.<input type="text" id="r-ref" placeholder="e.g. APX/INV/7790" value="' + esc(receipt.ref) + '"></label>'
      + '<label class="f" style="grid-column:1/-1">Medicine<select id="r-med">' + medicineOptions(receipt.medicineId, true) + '</select></label>'
      + '<label class="f">Batch no.<input type="text" id="r-batch" placeholder="e.g. B105" value="' + esc(receipt.batchNo) + '"></label>'
      + '<label class="f">Quantity<input type="number" id="r-qty" min="1" step="1" value="' + esc(receipt.qty) + '"></label>'
      + '<label class="f">Mfg date<input type="date" id="r-mfg" max="' + t + '" value="' + esc(receipt.mfg) + '"></label>'
      + '<label class="f">Expiry date<input type="date" id="r-exp" value="' + esc(receipt.expiry) + '"></label>'
      + '<label class="f">Unit cost (Rs)<input type="text" id="r-cost" inputmode="decimal" value="' + esc(receipt.cost || (med.cost / 100).toFixed(2)) + '"></label>'
      + '</div><div class="row" style="margin-top:12px"><button class="btn primary" id="r-add">' + icon('plus') + 'Add line</button>'
      + '<span class="hint">Tip: try an expiry date in the past - it is refused.</span></div></div>'
      + '<div class="card pad"><h3>GST on this bill</h3><p style="margin:0 0 8px">' + esc(sup.name) + ' is in <b>' + esc(C.stateLabel(sup.state)) + '</b>; the company is in <b>'
      + esc(C.stateLabel(state.company.state)) + '</b>.</p>' + pill(type, type === 'INTER_STATE' ? 'Inter-state: IGST' : 'Intra-state: CGST + SGST')
      + '<p class="hint">Change the supplier to see the tax treatment switch.</p></div></div>'
      + '<h3 style="margin:20px 0 10px">Lines to receive</h3>'
      + table([{ label: 'Medicine' }, { label: 'Batch' }, { label: 'Mfg' }, { label: 'Expiry' }, { label: 'Qty', num: 1 }, { label: 'Cost', num: 1 },
        { label: 'GST', num: 1 }, { label: 'Amount', num: 1 }, { label: '' }], lines, { empty: 'No lines yet. Add the items in the delivery above.' })
      + '<div class="card totals" style="margin-top:14px"><div class="figs"><div>Lines<b>' + receipt.lines.length + '</b></div><div>Subtotal<b>' + money(tot.subtotal)
      + '</b></div><div>GST<b>' + money(tot.tax) + '</b></div><div class="grand">Total<b>' + money(tot.total) + '</b></div></div>'
      + '<div style="margin-left:auto"><button class="btn primary" id="r-save"' + (receipt.lines.length ? '' : ' disabled') + '>' + icon('package') + 'Receive shipment</button></div></div>'
      + '<h3 style="margin:24px 0 10px">Recent purchases</h3>'
      + table([{ label: 'PO No' }, { label: 'Received' }, { label: 'Supplier' }, { label: 'Supplier inv.' }, { label: 'Items', num: 1 }, { label: 'Tax type' },
        { label: 'Total', num: 1 }, { label: 'Status' }], recent);
  };

  BIND.receive = view => {
    const val = id => $('#' + id, view).value;
    const sync = () => {
      receipt.supplierId = Number(val('r-sup'));
      receipt.ref = val('r-ref');
      receipt.batchNo = val('r-batch');
      receipt.qty = val('r-qty');
      receipt.mfg = val('r-mfg');
      receipt.expiry = val('r-exp');
      receipt.cost = val('r-cost');
    };
    view.addEventListener('input', sync);
    view.addEventListener('change', e => {
      sync();
      if (e.target.id === 'r-med') {
        receipt.medicineId = Number(val('r-med'));
        receipt.cost = '';
        render();
      } else if (e.target.id === 'r-sup') {
        render();
      }
    });
    view.addEventListener('click', e => {
      const rm = e.target.closest('[data-rline]');
      if (rm) {
        receipt.lines.splice(Number(rm.dataset.rline), 1);
        render();
      } else if (e.target.closest('#r-add')) {
        try {
          const m = medicine(receipt.medicineId);
          const qty = parseInt(receipt.qty, 10);
          if (!qty || qty <= 0) {
            throw new C.DemoError('Enter a quantity greater than zero');
          }
          const batchNo = receipt.batchNo.trim().toUpperCase();
          C.validateBatchInfo(batchNo, receipt.mfg, receipt.expiry, today(), m.brand);
          const cost = C.parseMoney(receipt.cost || (m.cost / 100).toFixed(2), 'Unit cost');
          receipt.lines.push({ medicineId: m.id, batchNo, mfg: receipt.mfg, expiry: receipt.expiry, qty, cost });
          Object.assign(receipt, { batchNo: '', qty: '', mfg: '', expiry: '', cost: '' });
          render();
        } catch (err) {
          showError(err);
        }
      } else if (e.target.closest('#r-save')) {
        try {
          const p = C.receiveStock(state, { supplierId: receipt.supplierId, invoiceRef: receipt.ref.trim() || null,
            lines: receipt.lines }, new Date());
          save();
          receipt = newReceipt();
          render();
          toast(p.poNo + ' received: ' + p.items.length + ' batch line' + (p.items.length > 1 ? 's' : '') + ' added to stock.');
        } catch (err) {
          showError(err);
        }
      }
    });
  };

  // ---------------------------------------------------------------- go

  shell();
  start();
})();
