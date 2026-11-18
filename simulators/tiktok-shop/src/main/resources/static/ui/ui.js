/*
 * Trang kiểm tra trực quan của TikTok Shop Simulator (:8090/ui).
 * Chỉ đọc /sim/ui/* và gọi lại /sim/* sẵn có; không có thư viện ngoài.
 */
(() => {
  'use strict';

  const POLL_MS = 2000;
  const MAX_CALLS = 500;
  const KNOWN_SCOPES = ['seller.authorization.info', 'seller.customer_service', 'seller.shop.info',
    'seller.product.basic', 'seller.product.write'];
  const SAMPLES = [
    'Shop ơi serum này giá bao nhiêu?',
    'Da dầu mụn dùng được không ạ?',
    'Cho mình xin số zalo để tư vấn nhé',
    'Kem này trị dứt điểm mụn không?',
    'Còn hàng không shop, ship mấy ngày?',
    'Bên Shopee bán rẻ hơn đó shop',
  ];

  const $ = (sel, root = document) => root.querySelector(sel);
  const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

  const state = {
    tab: 'chat',
    overview: null,
    conversations: [],
    selectedConv: null,
    convSignature: '',
    pending: null,            // {conversationId, messageId, sentAt}: tin buyer đang chờ hub trả lời
    seenMessages: new Set(),
    products: [],
    productStamp: new Map(),  // id -> update_time lần vẽ trước, để nháy thẻ vừa đổi
    calls: [],
    lastCallId: 0,
    deliveries: [],
    faultsLoaded: false,
    timer: null,
    busy: false,
  };

  // ---------- tiện ích ----------

  function esc(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  async function api(path, options = {}) {
    const res = await fetch(path, {
      headers: options.body ? { 'content-type': 'application/json' } : {},
      ...options,
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    if (!res.ok) {
      const msg = data && (data.message || (data.fields && data.fields.join(', ')) || data.error);
      throw new Error(`HTTP ${res.status}${msg ? ': ' + msg : ''}`);
    }
    return data;
  }

  function toast(message, isError = false) {
    const t = $('#toast');
    t.textContent = message;
    t.classList.toggle('err', isError);
    t.classList.add('show');
    clearTimeout(toast.timer);
    toast.timer = setTimeout(() => t.classList.remove('show'), isError ? 5000 : 2500);
  }

  const timeFmt = new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  const dateTimeFmt = new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'medium' });
  const fmtSec = s => (s ? timeFmt.format(new Date(s * 1000)) : '');
  const fmtSecFull = s => (s ? dateTimeFmt.format(new Date(s * 1000)) : '');
  const fmtMs = ms => timeFmt.format(new Date(ms));
  const fmtVnd = a => (a == null ? '' : Number(a).toLocaleString('vi-VN') + ' ₫');

  function pretty(raw) {
    if (raw == null || raw === '') return '(trống)';
    if (typeof raw !== 'string') return JSON.stringify(raw, null, 2);
    try { return JSON.stringify(JSON.parse(raw), null, 2); } catch { return raw; }
  }

  function messageText(m) {
    try {
      const c = JSON.parse(m.content);
      if (m.type === 'TEXT') return c.content ?? m.content;
      return c;
    } catch {
      return m.content;
    }
  }

  function initials(name) {
    return (name || '?').replace(/^buyer-/, '').trim().slice(0, 2).toUpperCase();
  }

  function imageUrl(uri) {
    return '/sim/ui/images?uri=' + encodeURIComponent(uri);
  }

  // ---------- tab ----------

  function showTab(name) {
    state.tab = name;
    $$('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
    $$('.tab-panel').forEach(p => p.classList.toggle('active', p.id === 'tab-' + name));
    try { localStorage.setItem('tts-sim-tab', name); } catch { /* bỏ qua */ }
    if (name === 'control') state.faultsLoaded = false;
    refresh();
  }

  // ---------- vòng cập nhật ----------

  async function refresh() {
    if (state.busy) return;
    state.busy = true;
    try {
      state.overview = await api('/sim/ui/overview');
      renderOverview();
      const jobs = [];
      if (state.tab === 'chat' || state.pending) jobs.push(loadConversations());
      if (state.tab === 'products') jobs.push(loadProducts());
      if (state.tab === 'calls') jobs.push(loadCalls());
      if (state.tab === 'webhooks') jobs.push(loadDeliveries());
      if (state.tab === 'control') jobs.push(loadControl());
      await Promise.all(jobs);
      $('#liveDot').className = 'dot ok';
    } catch (e) {
      $('#liveDot').className = 'dot err';
      $('#shopLine').textContent = 'Không kết nối được simulator: ' + e.message;
    } finally {
      state.busy = false;
    }
  }

  function schedule() {
    clearInterval(state.timer);
    state.timer = setInterval(() => {
      if ($('#autoRefresh').checked && !document.hidden) refresh();
    }, POLL_MS);
  }

  function renderOverview() {
    const o = state.overview;
    const c = o.counts;
    $('#shopLine').textContent = `${o.shop.name} · shop_id ${o.shop.id} · cipher ${o.shop.cipher} · app_key ${o.app_key}`;
    $('#cntChat').textContent = c.conversations;
    $('#cntProducts').textContent = c.products;
    $('#cntCalls').textContent = c.api_errors ? `${c.api_calls} · ${c.api_errors} lỗi` : c.api_calls;
    $('#cntCalls').classList.toggle('alert', c.api_errors > 0);
    $('#cntWebhooks').textContent = c.failed_deliveries ? `${c.deliveries} · ${c.failed_deliveries} lỗi` : c.deliveries;
    $('#cntWebhooks').classList.toggle('alert', c.failed_deliveries > 0);

    const pill = $('#authPill');
    const live = o.tokens.filter(t => !t.access_expired);
    if (!o.tokens.length) {
      pill.textContent = 'OAuth: hub chưa ủy quyền';
      pill.className = 'pill err';
    } else if (live.length) {
      pill.textContent = `OAuth: ${live.length} token hợp lệ`;
      pill.className = 'pill ok';
    } else {
      pill.textContent = 'OAuth: access token đã hết hạn';
      pill.className = 'pill err';
    }
  }

  // ---------- chat ----------

  async function loadConversations() {
    state.conversations = await api('/sim/ui/conversations');
    renderConversations();
    renderThread();
  }

  function buyerOf(dump) {
    return dump.conversation.participants.find(p => p.role === 'BUYER') || {};
  }

  function renderConversations() {
    const list = $('#convList');
    $('#convEmpty').classList.toggle('hidden', state.conversations.length > 0);
    list.innerHTML = state.conversations.map(d => {
      const b = buyerOf(d);
      const last = d.messages[d.messages.length - 1];
      const preview = last ? (last.sender.role === 'BUYER' ? '' : 'Shop: ') + textPreview(last) : '';
      const unread = d.conversation.unread_count;
      return `<li data-id="${esc(d.conversation.id)}" class="${d.conversation.id === state.selectedConv ? 'active' : ''}">
        <span class="avatar">${esc(initials(b.nickname))}</span>
        <div style="min-width:0">
          <div class="conv-name">${esc(b.nickname)}</div>
          <div class="conv-preview">${esc(preview)}</div>
        </div>
        <div class="conv-side">${esc(last ? fmtSec(last.create_time) : '')}<br>${unread ? `<span class="badge">${unread}</span>` : ''}</div>
      </li>`;
    }).join('');
  }

  function textPreview(m) {
    const t = messageText(m);
    return typeof t === 'string' ? t : `[${m.type}]`;
  }

  function selectConversation(id) {
    state.selectedConv = id;
    state.convSignature = '';
    const d = state.conversations.find(c => c.conversation.id === id);
    if (d) {
      const b = buyerOf(d);
      $('#buyerId').value = b.user_id || '';
      $('#buyerNick').value = b.nickname || '';
    }
    renderConversations();
    renderThread();
    $('#buyerText').focus();
  }

  function renderThread() {
    const d = state.conversations.find(c => c.conversation.id === state.selectedConv);
    const box = $('#messages');
    if (!d) {
      $('#threadTitle').textContent = state.selectedConv === null && $('#buyerId').value
        ? `Buyer mới: ${$('#buyerId').value}` : 'Chọn một hội thoại';
      $('#threadMeta').textContent = state.selectedConv === null && $('#buyerId').value
        ? 'Tin đầu tiên sẽ tạo hội thoại và bắn webhook NEW_CONVERSATION + NEW_MESSAGE.' : '';
      if (state.convSignature !== 'none') { box.innerHTML = ''; state.convSignature = 'none'; }
      renderPending(box, null);
      return;
    }
    const b = buyerOf(d);
    const conv = d.conversation;
    $('#threadTitle').textContent = b.nickname;
    $('#threadMeta').innerHTML = `buyer_user_id <code>${esc(b.user_id)}</code> · conversation_id <code>${esc(conv.id)}</code>`
      + ` · ${conv.can_send_message ? '<span class="tag ok">can_send_message</span>' : '<span class="tag err">không gửi được</span>'}`
      + ` · ${d.messages.length} tin`;

    const last = d.messages[d.messages.length - 1];
    const signature = `${conv.id}:${d.messages.length}:${last ? last.index : ''}`;
    const mine = state.pending && state.pending.conversationId === conv.id
      ? d.messages.find(m => m.id === state.pending.messageId) : null;
    const reply = mine && d.messages.find(m => m.sender.role !== 'BUYER' && BigInt(m.index) > BigInt(mine.index));
    if (reply) {
      const waited = ((Date.now() - state.pending.sentAt) / 1000).toFixed(1);
      $('#sendStatus').textContent = `Hub đã trả lời (${reply.sender.role}) — thấy trên UI sau ~${waited} s (chu kỳ tải ${POLL_MS / 1000} s).`;
      state.pending = null;
    }
    if (signature !== state.convSignature) {
      const atBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 60 || state.convSignature.split(':')[0] !== conv.id;
      const firstRender = state.convSignature.split(':')[0] !== conv.id;
      box.innerHTML = d.messages.map((m, i) => renderMessage(m, d.messages, i, firstRender)).join('');
      d.messages.forEach(m => state.seenMessages.add(m.id));
      state.convSignature = signature;
      if (atBottom) box.scrollTop = box.scrollHeight;
    }
    renderPending(box, conv.id);
  }

  function renderMessage(m, all, i, firstRender) {
    const buyer = m.sender.role === 'BUYER';
    const body = messageText(m);
    let reply = '';
    if (!buyer) {
      for (let j = i - 1; j >= 0; j--) {
        if (all[j].sender.role === 'BUYER') {
          reply = `<span>trả lời sau ${m.create_time - all[j].create_time} s</span>`;
          break;
        }
        if (all[j].sender.role !== 'BUYER') break;
      }
    }
    const fresh = !firstRender && !state.seenMessages.has(m.id) ? ' fresh' : '';
    const content = typeof body === 'string'
      ? esc(body)
      : `<span class="tag">${esc(m.type)}</span>\n<code>${esc(JSON.stringify(body, null, 2))}</code>`;
    return `<div class="msg ${buyer ? 'buyer' : 'shop'} role-${esc(m.sender.role)}${fresh}" title="message_id ${esc(m.id)} · index ${esc(m.index)}">
      <div class="msg-meta"><span class="tag ${esc(m.sender.role)}">${esc(m.sender.role)}</span>
        <span>${esc(m.sender.nickname)}</span><span>${esc(fmtSec(m.create_time))}</span>${reply}
        ${m.is_visible ? '' : '<span class="tag warn">ẩn</span>'}</div>
      <div class="bubble">${content}</div>
    </div>`;
  }

  function renderPending(box, conversationId) {
    let w = $('.waiting', box);
    const show = state.pending && (state.pending.conversationId === conversationId || conversationId === null);
    if (show && !w) {
      w = document.createElement('div');
      w.className = 'waiting';
      w.textContent = 'Đã bắn webhook, chờ hub trả lời';
      box.appendChild(w);
      box.scrollTop = box.scrollHeight;
    } else if (!show && w) {
      w.remove();
    }
  }

  async function sendBuyerMessage(ev) {
    ev.preventDefault();
    const buyerId = $('#buyerId').value.trim();
    const text = $('#buyerText').value.trim();
    if (!buyerId || !text) return;
    const btn = $('#sendBtn');
    btn.disabled = true;
    try {
      const r = await api('/sim/buyer/messages', {
        method: 'POST',
        body: JSON.stringify({ buyer_user_id: buyerId, nickname: $('#buyerNick').value.trim() || null, text }),
      });
      $('#buyerText').value = '';
      state.selectedConv = r.conversation_id;
      state.convSignature = '';
      state.pending = { conversationId: r.conversation_id, messageId: r.message_id, sentAt: Date.now() };
      $('#sendStatus').textContent = `Đã gửi message_id ${r.message_id}, tts_notification_id ${r.tts_notification_id}`
        + (r.new_conversation ? ' (hội thoại mới)' : '') + '. Đang chờ hub trả lời…';
      await loadConversations();
    } catch (e) {
      toast('Gửi thất bại: ' + e.message, true);
    } finally {
      btn.disabled = false;
      $('#buyerText').focus();
    }
  }

  function newBuyer() {
    state.selectedConv = null;
    state.convSignature = '';
    const id = 'buyer-' + Math.random().toString(36).slice(2, 7);
    $('#buyerId').value = id;
    $('#buyerNick').value = '';
    renderConversations();
    renderThread();
    $('#buyerText').focus();
  }

  // ---------- sản phẩm ----------

  async function loadProducts() {
    state.products = await api('/sim/products');
    renderProducts();
  }

  function renderProducts() {
    const q = $('#productSearch').value.trim().toLowerCase();
    const status = $('#productStatus').value;
    const list = state.products
      .filter(p => !status || p.status === status)
      .filter(p => !q || [p.title, p.id, ...p.skus.map(s => s.seller_sku)].some(v => String(v ?? '').toLowerCase().includes(q)))
      .slice()
      .sort((a, b) => b.update_time - a.update_time || String(b.id).localeCompare(String(a.id)));
    const active = state.products.filter(p => p.status === 'ACTIVATE').length;
    $('#productSummary').textContent = `${state.products.length} sản phẩm · ${active} đang bán · ${state.products.length - active} đã ẩn`
      + (list.length !== state.products.length ? ` · hiện ${list.length}` : '');
    $('#productEmpty').classList.toggle('hidden', state.products.length > 0);

    const first = state.productStamp.size === 0;
    $('#productGrid').innerHTML = list.map(p => {
      const prev = state.productStamp.get(p.id);
      const changed = !first && prev !== p.update_time;
      const prices = p.skus.map(s => Number(s.price_amount));
      const priceText = prices.length ? (Math.min(...prices) === Math.max(...prices)
        ? fmtVnd(prices[0]) : `${fmtVnd(Math.min(...prices))} – ${fmtVnd(Math.max(...prices))}`) : '';
      const img = p.main_images[0];
      return `<article class="product${changed ? ' flash' : ''}${p.status === 'ACTIVATE' ? '' : ' off'}" data-id="${esc(p.id)}">
        <div class="product-img">
          ${img ? `<img loading="lazy" alt="" src="${esc(imageUrl(img))}" onerror="this.replaceWith(Object.assign(document.createElement('span'),{className:'muted small',textContent:'không có byte ảnh'}))">` : '<span class="muted small">không có ảnh</span>'}
          ${p.main_images.length > 1 ? `<span class="count">${p.main_images.length} ảnh</span>` : ''}
        </div>
        <div class="product-body">
          <div class="product-title" title="${esc(p.title)}">${esc(p.title)}</div>
          <div class="price">${esc(priceText)}</div>
          <table class="sku-table"><thead><tr><th>seller_sku</th><th>Giá</th><th>Tồn</th></tr></thead><tbody>
            ${p.skus.map(s => `<tr><td class="mono">${esc(s.seller_sku || s.id)}</td><td>${esc(fmtVnd(s.price_amount))}</td><td>${esc(s.quantity)}</td></tr>`).join('')}
          </tbody></table>
          <div class="product-foot">
            <span class="tag ${p.status === 'ACTIVATE' ? 'ok' : 'warn'}">${esc(p.status)}</span>
            <span class="muted small" title="update_time">${esc(fmtSecFull(p.update_time))}</span>
          </div>
        </div>
      </article>`;
    }).join('');
    state.productStamp = new Map(state.products.map(p => [p.id, p.update_time]));
  }

  function showProduct(id) {
    const p = state.products.find(x => x.id === id);
    if (!p) return;
    $('#detailTitle').textContent = p.title;
    const body = $('#detailBody');
    body.innerHTML = `
      <div class="gallery">${p.main_images.map(u => `<img alt="" src="${esc(imageUrl(u))}" title="${esc(u)}">`).join('')}</div>
      <dl class="kv">
        <dt>product_id</dt><dd class="mono">${esc(p.id)}</dd>
        <dt>Trạng thái</dt><dd>${esc(p.status)}</dd>
        <dt>category_id</dt><dd class="mono">${esc(p.category_id)}</dd>
        <dt>brand_id</dt><dd class="mono">${esc(p.brand_id ?? '(không)')}</dd>
        <dt>package_weight</dt><dd>${esc(p.package_weight ? p.package_weight.value + ' ' + p.package_weight.unit : '')}</dd>
        <dt>Tạo / cập nhật</dt><dd>${esc(fmtSecFull(p.create_time))} / ${esc(fmtSecFull(p.update_time))}</dd>
      </dl>
      <div><h4 class="muted small">Mô tả (HTML hub gửi, hiển thị trong khung cách ly)</h4><iframe sandbox="" title="Mô tả"></iframe></div>
      <div><h4 class="muted small">JSON simulator đang giữ</h4><pre class="json">${esc(pretty(p))}</pre></div>`;
    $('iframe', body).srcdoc = '<meta charset="utf-8"><style>body{font:14px system-ui;margin:10px;color:#161823}</style>' + (p.description || '<i>(trống)</i>');
    $('#detail').showModal();
  }

  // ---------- API hub gọi ----------

  async function loadCalls() {
    const total = state.overview ? state.overview.counts.api_calls : 0;
    if (total < state.calls.length) {       // reset hoặc simulator khởi động lại
      state.calls = [];
      state.lastCallId = 0;
    }
    const fresh = await api('/sim/ui/api-calls?after=' + state.lastCallId);
    if (fresh.length) {
      fresh.forEach(c => { c._fresh = state.lastCallId > 0; });
      state.calls = fresh.concat(state.calls.map(c => ({ ...c, _fresh: false }))).slice(0, MAX_CALLS);
      state.lastCallId = Math.max(state.lastCallId, ...fresh.map(c => c.id));
    } else {
      state.calls.forEach(c => { c._fresh = false; });
    }
    renderCalls();
  }

  const isErr = c => Math.floor(c.http_status / 100) !== 2 || (c.code != null && c.code !== 0);

  function renderCalls() {
    const group = $('#callGroup').value;
    const onlyErr = $('#callErrors').checked;
    const q = $('#callSearch').value.trim().toLowerCase();
    const rows = state.calls
      .filter(c => !group || c.path.startsWith(group))
      .filter(c => !onlyErr || isErr(c))
      .filter(c => !q || (c.path + ' ' + (c.request_body || '') + ' ' + (c.response_body || '')).toLowerCase().includes(q));
    $('#callEmpty').classList.toggle('hidden', rows.length > 0);
    $('#callTable tbody').innerHTML = rows.map(c => `
      <tr class="clickable${isErr(c) ? ' err' : ''}${c._fresh ? ' fresh' : ''}" data-id="${c.id}">
        <td class="mono">${esc(fmtMs(c.time))}</td>
        <td><span class="method ${esc(c.method)}">${esc(c.method)}</span></td>
        <td class="path mono">${esc(c.path)}</td>
        <td>${esc(c.http_status)}</td>
        <td class="mono">${esc(c.code ?? '')}</td>
        <td>${esc(c.message ?? '')}</td>
        <td>${esc(c.latency_ms)}</td>
      </tr>`).join('');
  }

  function showCall(id) {
    const c = state.calls.find(x => x.id === id);
    if (!c) return;
    const params = new URLSearchParams(c.query || '');
    $('#detailTitle').textContent = `${c.method} ${c.path}`;
    $('#detailBody').innerHTML = `
      <dl class="kv">
        <dt>Thời điểm</dt><dd>${esc(dateTimeFmt.format(new Date(c.time)))}</dd>
        <dt>HTTP / code</dt><dd>${esc(c.http_status)} / ${esc(c.code ?? '—')} ${esc(c.message ?? '')}</dd>
        <dt>request_id</dt><dd class="mono">${esc(c.request_id ?? '—')}</dd>
        <dt>Độ trễ</dt><dd>${esc(c.latency_ms)} ms</dd>
        <dt>Content-Type</dt><dd class="mono">${esc(c.content_type ?? '—')}</dd>
      </dl>
      <div><h4 class="muted small">Tham số query</h4>
        <table class="sku-table">${[...params].map(([k, v]) => `<tr><td class="mono">${esc(k)}</td><td class="mono">${esc(v)}</td></tr>`).join('') || '<tr><td>(không có)</td></tr>'}</table>
      </div>
      <div class="split">
        <div><h4>Body hub gửi</h4><pre class="json">${esc(prettyRequest(c))}</pre></div>
        <div><h4>Simulator trả về</h4><pre class="json">${esc(pretty(c.response_body))}</pre></div>
      </div>`;
    $('#detail').showModal();
  }

  /** Send Message có content là chuỗi JSON lồng: mở ra cho dễ đọc. */
  function prettyRequest(c) {
    if (!c.request_body) return '(trống)';
    try {
      const body = JSON.parse(c.request_body);
      if (body && typeof body.content === 'string') {
        try { body.content = JSON.parse(body.content); } catch { /* giữ nguyên */ }
      }
      return JSON.stringify(body, null, 2);
    } catch {
      return c.request_body;
    }
  }

  // ---------- webhook ----------

  async function loadDeliveries() {
    state.deliveries = await api('/sim/deliveries');
    $('#hookEmpty').classList.toggle('hidden', state.deliveries.length > 0);
    $('#hookTable tbody').innerHTML = state.deliveries.map(d => `
      <tr>
        <td><strong>${esc(d.event_type)}</strong>${d.duplicate ? ' <span class="tag warn">gửi trùng</span>' : ''}</td>
        <td class="mono">${esc(d.tts_notification_id)}</td>
        <td class="path mono">${esc(d.address)}</td>
        <td>${d.delivered ? '<span class="tag ok">2xx</span>' : '<span class="tag err">thất bại</span>'}</td>
        <td class="small">${d.attempts.map(a => esc(`#${a.attempt}: ${a.http_status ?? a.error} · ${a.latency_ms} ms`)).join('<br>')}</td>
      </tr>`).join('');
  }

  // ---------- điều khiển ----------

  async function loadControl() {
    const o = state.overview;
    $('#shopInfo').innerHTML = [
      ['Tên', o.shop.name], ['shop_id', o.shop.id], ['shop_cipher', o.shop.cipher], ['Mã shop', o.shop.code],
      ['Vùng', o.shop.region], ['seller_type', o.shop.seller_type], ['app_key', o.app_key],
      ['Webhook mặc định', o.default_webhook_address],
    ].map(([k, v]) => `<dt>${esc(k)}</dt><dd class="mono">${esc(v)}</dd>`).join('');

    $('#tokenInfo').innerHTML = o.tokens.length ? `<table class="sku-table">
        <thead><tr><th>access token</th><th>Hết hạn access</th><th>Hết hạn refresh</th></tr></thead><tbody>
        ${o.tokens.map(t => `<tr><td class="mono">${esc(t.access_token)}</td>
          <td>${t.access_expired ? '<span class="tag err">đã hết</span>' : esc(fmtSecFull(t.access_expires_at))}</td>
          <td>${t.refresh_expired ? '<span class="tag err">đã hết</span>' : esc(fmtSecFull(t.refresh_expires_at))}</td></tr>`).join('')}
        </tbody></table>`
      : '<p class="muted">Chưa có token. Hub chưa đi luồng ủy quyền (hoặc vừa reset).</p>';

    $('#hookRegs').innerHTML = o.webhooks.length ? `<table class="sku-table">
        <thead><tr><th>Sự kiện</th><th>Địa chỉ</th></tr></thead><tbody>
        ${o.webhooks.map(w => `<tr><td>${esc(w.event_type)}</td><td class="mono">${esc(w.address)}</td></tr>`).join('')}
        </tbody></table>`
      : `<p class="muted small">Hub chưa gọi Update Shop Webhook: mọi sự kiện gửi tới địa chỉ mặc định <code>${esc(o.default_webhook_address)}</code>.</p>`;

    if (!state.faultsLoaded) {
      fillFaults(await api('/sim/faults'));
      state.faultsLoaded = true;
    }
  }

  function fillFaults(f) {
    const form = $('#faultForm');
    form.api_latency_ms.value = f.api_latency_ms;
    form.webhook_delay_ms.value = f.webhook_delay_ms;
    form.webhook_duplicate.checked = f.webhook_duplicate;
    form.block_sending.checked = f.block_sending;
    form.sensitive_words.value = f.sensitive_words.join(', ');
    const scopes = [...new Set([...KNOWN_SCOPES, ...f.revoked_scopes])];
    $('#scopeBoxes').innerHTML = scopes.map(s => `<label class="check"><input type="checkbox" value="${esc(s)}"
      ${f.revoked_scopes.includes(s) ? 'checked' : ''}> <code>${esc(s)}</code></label>`).join('');
    const on = [];
    if (f.api_latency_ms) on.push(`trễ API ${f.api_latency_ms} ms`);
    if (f.webhook_delay_ms) on.push(`trễ webhook ${f.webhook_delay_ms} ms`);
    if (f.webhook_duplicate) on.push('webhook trùng');
    if (f.block_sending) on.push('chặn gửi');
    if (f.sensitive_words.length) on.push(`${f.sensitive_words.length} từ nhạy cảm`);
    if (f.revoked_scopes.length) on.push(`thu hồi ${f.revoked_scopes.length} scope`);
    $('#faultStatus').textContent = on.length ? 'Đang bật: ' + on.join(', ') : 'Không bật fault nào.';
  }

  async function saveFaults(ev) {
    ev.preventDefault();
    const form = $('#faultForm');
    const body = {
      api_latency_ms: Number(form.api_latency_ms.value || 0),
      webhook_delay_ms: Number(form.webhook_delay_ms.value || 0),
      webhook_duplicate: form.webhook_duplicate.checked,
      block_sending: form.block_sending.checked,
      sensitive_words: form.sensitive_words.value.split(',').map(s => s.trim()).filter(Boolean),
      revoked_scopes: $$('#scopeBoxes input:checked').map(i => i.value),
    };
    try {
      fillFaults(await api('/sim/faults', { method: 'POST', body: JSON.stringify(body) }));
      toast('Đã lưu fault');
    } catch (e) {
      toast('Lưu fault lỗi: ' + e.message, true);
    }
  }

  async function faultsOff() {
    try {
      fillFaults(await api('/sim/faults', {
        method: 'POST',
        body: JSON.stringify({ api_latency_ms: 0, webhook_delay_ms: 0, webhook_duplicate: false, block_sending: false,
          sensitive_words: [], revoked_scopes: [] }),
      }));
      toast('Đã tắt hết fault');
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function expireTokens(kind) {
    try {
      const r = await api('/sim/tokens/expire?kind=' + kind, { method: 'POST' });
      toast(`Đã cho ${r.expired} token hết hạn (${kind})`);
      refresh();
    } catch (e) {
      toast(e.message, true);
    }
  }

  async function resetAll() {
    if (!confirm('Reset toàn bộ simulator? Hội thoại, sản phẩm, token và log sẽ mất; hub phải ủy quyền lại.')) return;
    try {
      await api('/sim/reset', { method: 'POST', body: '{}' });
      Object.assign(state, { conversations: [], selectedConv: null, convSignature: '', pending: null, products: [],
        calls: [], lastCallId: 0, deliveries: [], faultsLoaded: false });
      state.productStamp = new Map();
      state.seenMessages = new Set();
      $('#sendStatus').textContent = '';
      toast('Đã reset simulator');
      refresh();
    } catch (e) {
      toast(e.message, true);
    }
  }

  // ---------- gắn sự kiện ----------

  function bind() {
    $$('.tabs button').forEach(b => b.addEventListener('click', () => showTab(b.dataset.tab)));
    $('#convList').addEventListener('click', e => {
      const li = e.target.closest('li[data-id]');
      if (li) selectConversation(li.dataset.id);
    });
    $('#newBuyerBtn').addEventListener('click', newBuyer);
    $('#composer').addEventListener('submit', sendBuyerMessage);
    $('#buyerText').addEventListener('keydown', e => {
      if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
        e.preventDefault();
        $('#composer').requestSubmit();
      }
    });
    $('#samples').innerHTML = SAMPLES.map(s => `<button type="button">${esc(s)}</button>`).join('');
    $('#samples').addEventListener('click', e => {
      if (e.target.tagName === 'BUTTON') {
        $('#buyerText').value = e.target.textContent;
        $('#buyerText').focus();
      }
    });

    $('#productSearch').addEventListener('input', renderProducts);
    $('#productStatus').addEventListener('change', renderProducts);
    $('#productGrid').addEventListener('click', e => {
      const card = e.target.closest('.product[data-id]');
      if (card) showProduct(card.dataset.id);
    });

    ['#callGroup', '#callErrors'].forEach(s => $(s).addEventListener('change', renderCalls));
    $('#callSearch').addEventListener('input', renderCalls);
    $('#callTable tbody').addEventListener('click', e => {
      const tr = e.target.closest('tr[data-id]');
      if (tr) showCall(Number(tr.dataset.id));
    });
    $('#clearCalls').addEventListener('click', async () => {
      await api('/sim/ui/api-calls/clear', { method: 'POST' });
      state.calls = [];
      renderCalls();
    });

    $('#faultForm').addEventListener('submit', saveFaults);
    $('#faultsOff').addEventListener('click', faultsOff);
    $$('[data-expire]').forEach(b => b.addEventListener('click', () => expireTokens(b.dataset.expire)));
    $('#resetBtn').addEventListener('click', resetAll);
    $('#autoRefresh').addEventListener('change', () => { if ($('#autoRefresh').checked) refresh(); });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  }

  bind();
  let initial = 'chat';
  try { initial = localStorage.getItem('tts-sim-tab') || 'chat'; } catch { /* bỏ qua */ }
  if (!$('#tab-' + initial)) initial = 'chat';
  if (!$('#buyerId').value) $('#buyerId').value = 'buyer-' + Math.random().toString(36).slice(2, 7);
  showTab(initial);
  schedule();
})();
