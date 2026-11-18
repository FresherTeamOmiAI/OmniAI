/*
 * Trang kiểm tra trực quan của Shopee Simulator (:8091/ui).
 * Đọc /sim/ui/*, thao tác qua /api/simulator/shopee/* sẵn có (kèm "deliver": true) và /sim/reset.
 * Không có thư viện ngoài.
 */
(() => {
  'use strict';

  const POLL_MS = 2000;
  const MAX_CALLS = 500;
  const SAMPLES = [
    'Shop ơi kem chống nắng này giá bao nhiêu?',
    'Da dầu mụn dùng được không ạ?',
    'Cho mình xin số zalo để tư vấn nhé',
    'Gửi link web của shop cho mình với',
    'Còn hàng không shop, ship mấy ngày?',
    'Bên TikTok bán rẻ hơn đó shop',
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
    items: [],
    itemStamp: new Map(),     // item_id -> update_time lần vẽ trước, để nháy dòng vừa đổi
    orders: [],
    calls: [],
    lastCallId: 0,
    pushes: [],
    lastEmit: null,           // response phát tin/đơn gần nhất (raw body + chữ ký)
    timer: null,
    busy: false,
  };

  // ---------- tiện ích ----------

  function esc(v) {
    return String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  /** Lỗi Shopee trả HTTP 200 kèm envelope {error, message}: coi như thất bại. */
  async function api(path, options = {}) {
    const res = await fetch(path, {
      headers: options.body ? { 'content-type': 'application/json' } : {},
      ...options,
    });
    const text = await res.text();
    let data = null;
    try { data = text ? JSON.parse(text) : null; } catch { data = text; }
    const shopeeError = data && typeof data === 'object' && typeof data.error === 'string' && data.error !== '';
    if (!res.ok || shopeeError) {
      const msg = data && typeof data === 'object' ? [data.error, data.message].filter(Boolean).join(': ') : '';
      throw new Error(`HTTP ${res.status}${msg ? ' · ' + msg : ''}`);
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
  const fmtVnd = a => (a == null ? '—' : Number(a).toLocaleString('vi-VN') + ' ₫');
  const bigger = (a, b) => { try { return BigInt(a) > BigInt(b); } catch { return false; } };

  function pretty(raw) {
    if (raw == null || raw === '') return '(trống)';
    if (typeof raw !== 'string') return JSON.stringify(raw, null, 2);
    try { return JSON.stringify(JSON.parse(raw), null, 2); } catch { return raw; }
  }

  function initials(name) {
    return (name || '?').trim().slice(0, 2).toUpperCase();
  }

  function itemName(id) {
    const it = state.items.find(i => String(i.item_id) === String(id));
    return it ? (it.item_name || it.item_sku || '') : '';
  }

  function textPreview(m) {
    if (m.message_type === 'text') return m.content?.text ?? '';
    if (m.message_type === 'item') return `[item ${m.content?.item_id}]`;
    if (m.message_type === 'order') return `[order ${m.content?.order_sn}]`;
    return `[${m.message_type}]`;
  }

  function showDetail(title, html) {
    $('#detailTitle').textContent = title;
    $('#detailBody').innerHTML = html;
    $('#detail').showModal();
  }

  // ---------- tab ----------

  function showTab(name) {
    state.tab = name;
    $$('.tabs button').forEach(b => b.classList.toggle('active', b.dataset.tab === name));
    $$('.tab-panel').forEach(p => p.classList.toggle('active', p.id === 'tab-' + name));
    try { localStorage.setItem('shopee-sim-tab', name); } catch { /* bỏ qua */ }
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
      if (state.tab === 'chat' || state.pending) jobs.push(loadConversations(), loadPushes());
      if (state.tab === 'chat' || state.tab === 'items' || state.tab === 'orders') jobs.push(loadItems());
      if (state.tab === 'chat' || state.tab === 'orders') jobs.push(loadOrders());
      if (state.tab === 'orders' || state.tab === 'pushes') jobs.push(loadPushes());
      if (state.tab === 'calls') jobs.push(loadCalls());
      if (state.tab === 'control') renderControl();
      await Promise.all(jobs);
      if (state.tab === 'chat') { renderConversations(); renderThread(); }
      if (state.tab === 'orders') renderOrders();   // cột Push cần cả đơn lẫn log push
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
    $('#shopLine').textContent = `${o.shop.shop_name} · shop_id ${o.shop.shop_id} · partner_id ${o.shop.partner_id} · ${o.shop.region}/${o.shop.currency}`;
    $('#cntChat').textContent = c.conversations;
    $('#cntItems').textContent = c.items;
    $('#cntOrders').textContent = c.orders;
    $('#cntCalls').textContent = c.api_errors ? `${c.api_calls} · ${c.api_errors} lỗi` : c.api_calls;
    $('#cntCalls').classList.toggle('alert', c.api_errors > 0);
    $('#cntPushes').textContent = c.failed_pushes ? `${c.pushes} · ${c.failed_pushes} lỗi` : c.pushes;
    $('#cntPushes').classList.toggle('alert', c.failed_pushes > 0);
  }

  function renderPushPill() {
    const pill = $('#pushPill');
    const last = state.pushes[0];
    if (!last) {
      pill.textContent = 'Push: chưa gửi';
      pill.className = 'pill';
    } else if (last.delivered) {
      pill.textContent = `Push: hub nhận (${fmtMs(last.time)})`;
      pill.className = 'pill ok';
    } else {
      pill.textContent = 'Push: hub không nhận';
      pill.className = 'pill err';
    }
  }

  // ---------- chat ----------

  async function loadConversations() {
    state.conversations = await api('/sim/ui/conversations');
  }

  function renderConversations() {
    const list = $('#convList');
    $('#convEmpty').classList.toggle('hidden', state.conversations.length > 0);
    list.innerHTML = state.conversations.map(c => {
      const last = c.messages[c.messages.length - 1];
      const preview = last ? (last.from_shop ? 'Shop: ' : '') + textPreview(last) : '';
      return `<li data-id="${esc(c.conversation_id)}" class="${c.conversation_id === state.selectedConv ? 'active' : ''}">
        <span class="avatar">${esc(initials(c.buyer_name))}</span>
        <div style="min-width:0">
          <div class="conv-name">${esc(c.buyer_name || c.buyer_id)}</div>
          <div class="conv-preview">${esc(preview)}</div>
        </div>
        <div class="conv-side">${esc(last ? fmtSec(last.created_timestamp) : '')}<br>${c.unread_count ? `<span class="badge" title="unread_count">${c.unread_count}</span>` : ''}</div>
      </li>`;
    }).join('');
  }

  function selectConversation(id) {
    state.selectedConv = id;
    state.convSignature = '';
    const c = state.conversations.find(x => x.conversation_id === id);
    if (c) {
      $('#buyerId').value = c.buyer_id;
      $('#buyerName').value = c.buyer_name || '';
    }
    renderConversations();
    renderThread();
    $('#buyerText').focus();
  }

  function pushFor(messageId) {
    return state.pushes.find(p => p.code === 10 && p.ref === messageId);
  }

  function renderThread() {
    const c = state.conversations.find(x => x.conversation_id === state.selectedConv);
    const box = $('#messages');
    if (!c) {
      const fresh = state.selectedConv === null && $('#buyerId').value;
      $('#threadTitle').textContent = fresh ? `Buyer mới: ${$('#buyerName').value || $('#buyerId').value}` : 'Chọn một hội thoại';
      $('#threadMeta').textContent = fresh ? 'Tin đầu tiên sẽ tạo hội thoại và đẩy push code 10 (webchat_push) tới hub.' : '';
      if (state.convSignature !== 'none') { box.innerHTML = ''; state.convSignature = 'none'; }
      renderPending(box, null);
      return;
    }
    $('#threadTitle').textContent = c.buyer_name || String(c.buyer_id);
    $('#threadMeta').innerHTML = `from_id <code>${esc(c.buyer_id)}</code> · conversation_id <code>${esc(c.conversation_id)}</code>`
      + ` · unread_count ${esc(c.unread_count)} · ${c.messages.length} tin`;

    checkPending(c);
    const last = c.messages[c.messages.length - 1];
    const pushKey = c.messages.filter(m => !m.from_shop).map(m => { const p = pushFor(m.message_id); return p ? (p.delivered ? 1 : 0) : '-'; }).join('');
    const signature = `${c.conversation_id}:${c.messages.length}:${last ? last.message_id : ''}:${pushKey}`;
    if (signature !== state.convSignature) {
      const firstRender = state.convSignature.split(':')[0] !== c.conversation_id;
      const atBottom = firstRender || box.scrollHeight - box.scrollTop - box.clientHeight < 60;
      box.innerHTML = c.messages.map((m, i) => renderMessage(m, c.messages, i, firstRender)).join('');
      c.messages.forEach(m => state.seenMessages.add(m.message_id));
      state.convSignature = signature;
      if (atBottom) box.scrollTop = box.scrollHeight;
    }
    renderPending(box, c.conversation_id);
  }

  /** Tin buyer vừa gửi: đã có tin shop sau nó chưa, hoặc push đã thất bại chưa. */
  function checkPending(c) {
    const p = state.pending;
    if (!p || p.conversationId !== c.conversation_id) return;
    const reply = c.messages.find(m => m.from_shop && bigger(m.message_id, p.messageId));
    const push = pushFor(p.messageId);
    const waited = ((Date.now() - p.sentAt) / 1000).toFixed(1);
    if (reply) {
      $('#sendStatus').textContent = `Hub đã trả lời qua send_message — thấy trên UI sau ~${waited} s (chu kỳ tải ${POLL_MS / 1000} s).`;
      state.pending = null;
    } else if (push && !push.delivered) {
      $('#sendStatus').textContent = `Hub không nhận push sau ${push.attempts.length} lần (xem tab Push).`;
      state.pending = null;
    } else if (push && push.delivered && !push.final_text) {
      $('#sendStatus').textContent = 'Hub đã nhận push nhưng không trả lời (tin không phải chữ, hoặc trùng message_id).';
      state.pending = null;
    }
  }

  function renderContent(m) {
    if (m.message_type === 'text') return esc(m.content?.text ?? '');
    if (m.message_type === 'item') {
      const name = itemName(m.content?.item_id);
      return `<span class="ref-card"><span class="tag">item</span><strong>${esc(name || '(item chưa có trên simulator)')}</strong><span class="mono">item_id ${esc(m.content?.item_id)}</span></span>`;
    }
    if (m.message_type === 'order') {
      const o = state.orders.find(x => x.order_sn === m.content?.order_sn);
      return `<span class="ref-card"><span class="tag">order</span><strong class="mono">${esc(m.content?.order_sn)}</strong>`
        + (o ? `<span>${esc(o.order_status)} · ${esc(fmtVnd(o.total_amount))}</span>` : '') + '</span>';
    }
    return `<span class="tag">${esc(m.message_type)}</span>\n<code>${esc(JSON.stringify(m.content, null, 2))}</code>`;
  }

  function renderMessage(m, all, i, firstRender) {
    let extra = '';
    if (m.from_shop) {
      for (let j = i - 1; j >= 0; j--) {
        if (!all[j].from_shop) {
          extra = `<span>trả lời sau ${m.created_timestamp - all[j].created_timestamp} s</span>`;
          break;
        }
      }
    } else {
      const p = pushFor(m.message_id);
      if (p) {
        extra = p.delivered
          ? `<span class="tag ok" title="${esc(p.attempts.length)} lần gửi">push ${esc(p.attempts[p.attempts.length - 1].http_status)}</span>`
          : `<span class="tag err">push lỗi</span>`;
      }
    }
    const fresh = !firstRender && !state.seenMessages.has(m.message_id) ? ' fresh' : '';
    const who = m.from_shop ? 'SHOP' : 'BUYER';
    return `<div class="msg ${m.from_shop ? 'shop' : 'buyer'}${fresh}" title="message_id ${esc(m.message_id)} · status ${esc(m.status)}">
      <div class="msg-meta"><span class="tag ${who}">${who}</span>
        <span>${esc(m.from_user_name || m.from_id)}</span><span>${esc(fmtSec(m.created_timestamp))}</span>${extra}</div>
      <div class="bubble">${renderContent(m)}</div>
    </div>`;
  }

  function renderPending(box, conversationId) {
    let w = $('.waiting', box);
    const show = state.pending && (state.pending.conversationId === conversationId || conversationId === null);
    if (show && !w) {
      w = document.createElement('div');
      w.className = 'waiting';
      w.textContent = 'Đã đẩy push, chờ hub trả lời';
      box.appendChild(w);
      box.scrollTop = box.scrollHeight;
    } else if (!show && w) {
      w.remove();
    }
  }

  function syncComposer() {
    const type = $('#msgType').value;
    $('#buyerText').classList.toggle('hidden', type !== 'text');
    $('#samples').classList.toggle('hidden', type !== 'text');
    $('#itemPick').classList.toggle('hidden', type !== 'item');
    $('#orderPick').classList.toggle('hidden', type !== 'order');
    $('#buyerText').required = type === 'text';
    fillPickers();
  }

  function fillPickers() {
    const keep = (sel, html) => { const v = sel.value; sel.innerHTML = html; if ([...sel.options].some(o => o.value === v)) sel.value = v; };
    keep($('#itemPick'), state.items.length
      ? state.items.map(i => `<option value="${esc(i.item_id)}">${esc(i.item_id)} · ${esc(i.item_name || i.item_sku || '')}</option>`).join('')
      : '<option value="">(chưa có item, hub chưa đẩy giá/tồn)</option>');
    keep($('#orderPick'), state.orders.length
      ? state.orders.map(o => `<option value="${esc(o.order_sn)}">${esc(o.order_sn)} · ${esc(o.order_status)}</option>`).join('')
      : '<option value="">(chưa có đơn, phát ở tab Đơn hàng)</option>');
    keep($('#orderItemPick'), '<option value="">(nhập tay)</option>'
      + state.items.map(i => `<option value="${esc(i.item_id)}">${esc(i.item_id)} · ${esc(i.item_name || i.item_sku || '')}</option>`).join(''));
  }

  async function sendBuyerMessage(ev) {
    ev.preventDefault();
    const fromId = Number($('#buyerId').value);
    const name = $('#buyerName').value.trim();
    const type = $('#msgType').value;
    const body = { from_id: fromId, from_user_name: name, message_type: type, deliver: $('#deliverChat').checked };
    if (type === 'text') body.text = $('#buyerText').value.trim();
    if (type === 'item') body.item_id = Number($('#itemPick').value);
    if (type === 'order') body.order_sn = $('#orderPick').value;
    if (!fromId || !name || (type === 'text' && !body.text) || (type === 'item' && !body.item_id)
      || (type === 'order' && !body.order_sn)) {
      toast('Thiếu from_id, tên hoặc nội dung tin', true);
      return;
    }
    const btn = $('#sendBtn');
    btn.disabled = true;
    try {
      const r = await api('/api/simulator/shopee/webchat', { method: 'POST', body: JSON.stringify(body) });
      state.lastEmit = r;
      if (type === 'text') $('#buyerText').value = '';
      state.selectedConv = r.conversation_id;
      state.convSignature = '';
      if (r.delivered_by_simulator) {
        state.pending = { conversationId: r.conversation_id, messageId: r.message_id, sentAt: Date.now() };
        $('#sendStatus').textContent = `Đã tạo message_id ${r.message_id} và đẩy push code 10. Đang chờ hub trả lời…`;
      } else {
        state.pending = null;
        $('#sendStatus').innerHTML = `Đã tạo message_id ${esc(r.message_id)}, <strong>chưa</strong> gửi hub. `
          + '<a href="#" id="showEmit">Xem raw body + chữ ký</a>';
      }
      await Promise.all([loadConversations(), loadPushes()]);
      renderConversations();
      renderThread();
    } catch (e) {
      toast('Gửi thất bại: ' + e.message, true);
    } finally {
      btn.disabled = false;
      $('#buyerText').focus();
    }
  }

  function showEmit() {
    const r = state.lastEmit;
    if (!r) return;
    showDetail('Push chưa gửi', `
      <dl class="kv">
        <dt>POST tới</dt><dd class="mono">${esc(r.callback_url)}</dd>
        <dt>Authorization</dt><dd class="mono">${esc(r.authorization)}</dd>
      </dl>
      <div><h4 class="muted small">Raw body (gửi nguyên văn, chữ ký tính trên chuỗi này)</h4><pre class="json">${esc(r.raw_body)}</pre></div>`);
  }

  function newBuyer() {
    state.selectedConv = null;
    state.convSignature = '';
    const id = 100000000 + Math.floor(Math.random() * 900000000);
    $('#buyerId').value = id;
    $('#buyerName').value = 'buyer.' + id.toString(36).slice(-4);
    renderConversations();
    renderThread();
    $('#buyerText').focus();
  }

  // ---------- sản phẩm ----------

  async function loadItems() {
    state.items = await api('/sim/ui/items');
    if (state.tab === 'items') renderItems();
    fillPickers();
  }

  function renderItems() {
    const q = $('#itemSearch').value.trim().toLowerCase();
    const list = state.items.filter(i => !q || [i.item_id, i.item_sku, i.item_name]
      .some(v => String(v ?? '').toLowerCase().includes(q)));
    const priced = state.items.filter(i => i.original_price != null).length;
    const stocked = state.items.filter(i => i.stock != null).length;
    $('#itemSummary').textContent = `${state.items.length} item · ${priced} có giá · ${stocked} có tồn`
      + (list.length !== state.items.length ? ` · hiện ${list.length}` : '');
    $('#itemEmpty').classList.toggle('hidden', state.items.length > 0);
    const first = state.itemStamp.size === 0;
    $('#itemTable tbody').innerHTML = list.map(i => {
      const changed = !first && state.itemStamp.get(i.item_id) !== i.update_time;
      return `<tr class="${changed ? 'flash' : ''}">
        <td class="mono">${esc(i.item_id)}</td>
        <td class="mono">${esc(i.item_sku ?? '—')}</td>
        <td>${esc(i.item_name ?? '—')}</td>
        <td class="num">${esc(fmtVnd(i.original_price))}</td>
        <td class="num">${esc(i.stock ?? '—')}</td>
        <td class="num">${esc(i.models)}</td>
        <td><span class="tag ${i.item_status === 'NORMAL' ? 'ok' : 'warn'}">${esc(i.item_status)}</span></td>
        <td class="small">${esc(fmtSecFull(i.update_time))}</td>
      </tr>`;
    }).join('');
    state.itemStamp = new Map(state.items.map(i => [i.item_id, i.update_time]));
  }

  // ---------- đơn hàng ----------

  async function loadOrders() {
    state.orders = await api('/sim/ui/orders');
    if (state.tab === 'orders') renderOrders();
    fillPickers();
  }

  function renderOrders() {
    $('#orderEmpty').classList.toggle('hidden', state.orders.length > 0);
    $('#orderTable tbody').innerHTML = state.orders.map(o => {
      const p = state.pushes.find(x => x.code === 3 && x.ref === o.order_sn);
      const push = !p ? '<span class="muted small">chưa gửi</span>'
        : p.delivered ? '<span class="tag ok">hub nhận</span>' : '<span class="tag err">thất bại</span>';
      return `<tr>
        <td class="mono">${esc(o.order_sn)}</td>
        <td><span class="tag">${esc(o.order_status)}</span></td>
        <td>${esc(o.line ? `${o.line.item_name} (${o.line.item_sku})` : '—')}</td>
        <td class="num">${esc(o.line ? o.line.quantity : '')}</td>
        <td class="num">${esc(fmtVnd(o.total_amount))}</td>
        <td>${o.cod ? 'có' : ''}</td>
        <td class="small">${esc(fmtSecFull(o.create_time))}</td>
        <td>${push}</td>
      </tr>`;
    }).join('');
  }

  function pickOrderItem() {
    const it = state.items.find(i => String(i.item_id) === $('#orderItemPick').value);
    if (!it) return;
    const f = $('#orderForm');
    f.item_id.value = it.item_id;
    f.item_sku.value = it.item_sku || '';
    f.item_name.value = it.item_name || '';
    if (it.original_price != null) f.price.value = it.original_price;
  }

  async function emitOrder(ev) {
    ev.preventDefault();
    const f = $('#orderForm');
    const body = {
      item_id: Number(f.item_id.value), item_sku: f.item_sku.value.trim(), item_name: f.item_name.value.trim(),
      quantity: Number(f.quantity.value), price: Number(f.price.value), status: f.status.value,
      days_to_ship: Number(f.days_to_ship.value || 0), message_to_seller: f.message_to_seller.value.trim(),
      cod: f.cod.checked, deliver: f.deliver.checked,
    };
    try {
      const r = await api('/api/simulator/shopee/orders', { method: 'POST', body: JSON.stringify(body) });
      state.lastEmit = r;
      $('#orderStatus').innerHTML = `Đã tạo ${esc(r.order_sn)}${r.delivered_by_simulator ? ', đang đẩy push code 3.' : ', chưa gửi hub.'} `
        + '<a href="#" id="showEmit">Xem raw body + chữ ký</a>';
      await Promise.all([loadOrders(), loadPushes()]);
      renderOrders();
    } catch (e) {
      toast('Phát đơn lỗi: ' + e.message, true);
    }
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

  const isErr = c => Math.floor(c.http_status / 100) !== 2 || (c.error != null && c.error !== '');

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
        <td class="mono">${esc(c.error || '')}</td>
        <td>${esc(c.message || '')}</td>
        <td>${esc(c.latency_ms)}</td>
      </tr>`).join('');
  }

  function showCall(id) {
    const c = state.calls.find(x => x.id === id);
    if (!c) return;
    const params = new URLSearchParams(c.query || '');
    showDetail(`${c.method} ${c.path}`, `
      <dl class="kv">
        <dt>Thời điểm</dt><dd>${esc(dateTimeFmt.format(new Date(c.time)))}</dd>
        <dt>HTTP / error</dt><dd>${esc(c.http_status)} / ${esc(c.error || '(rỗng)')} ${esc(c.message || '')}</dd>
        <dt>request_id</dt><dd class="mono">${esc(c.request_id ?? '—')}</dd>
        <dt>Độ trễ</dt><dd>${esc(c.latency_ms)} ms</dd>
        <dt>Content-Type</dt><dd class="mono">${esc(c.content_type ?? '—')}</dd>
      </dl>
      <div><h4 class="muted small">Tham số query (chữ ký: HMAC-SHA256 của partner_id + path + timestamp + access_token + shop_id)</h4>
        <table class="sku-table">${[...params].map(([k, v]) => `<tr><td class="mono">${esc(k)}</td><td class="mono">${esc(v)}</td></tr>`).join('') || '<tr><td>(không có)</td></tr>'}</table>
      </div>
      <div class="split">
        <div><h4>Body hub gửi</h4><pre class="json">${esc(pretty(c.request_body))}</pre></div>
        <div><h4>Simulator trả về</h4><pre class="json">${esc(pretty(c.response_body))}</pre></div>
      </div>`);
  }

  // ---------- push ----------

  async function loadPushes() {
    state.pushes = await api('/sim/ui/pushes');
    renderPushPill();
    if (state.tab === 'pushes') renderPushes();
  }

  function renderPushes() {
    $('#pushEmpty').classList.toggle('hidden', state.pushes.length > 0);
    $('#pushTable tbody').innerHTML = state.pushes.map(p => `
      <tr class="clickable${p.delivered ? '' : ' err'}" data-id="${p.id}">
        <td class="mono">${esc(fmtMs(p.time))}</td>
        <td><strong>${esc(p.code)}</strong> <span class="muted small">${p.code === 3 ? 'order_status' : p.code === 10 ? 'webchat' : ''}</span></td>
        <td class="mono">${esc(p.ref)}</td>
        <td>${p.delivered ? '<span class="tag ok">2xx</span>' : '<span class="tag err">thất bại</span>'}</td>
        <td>${esc(p.final_text ?? '')}</td>
        <td class="small">${p.attempts.map(a => esc(`#${a.attempt}: ${a.http_status ?? a.error} · ${a.latency_ms} ms`)).join('<br>')}</td>
      </tr>`).join('');
  }

  function showPush(id) {
    const p = state.pushes.find(x => x.id === id);
    if (!p) return;
    showDetail(`Push code ${p.code} · ${p.ref}`, `
      <dl class="kv">
        <dt>Địa chỉ</dt><dd class="mono">${esc(p.address)}</dd>
        <dt>Kết quả</dt><dd>${p.delivered ? 'hub trả 2xx' : 'thất bại'}</dd>
        <dt>final_text</dt><dd>${esc(p.final_text ?? '—')}</dd>
      </dl>
      ${p.attempts.map(a => `<div><h4 class="muted small">Lần ${esc(a.attempt)}: ${esc(a.http_status ?? a.error)} · ${esc(a.latency_ms)} ms</h4>
        <pre class="json">${esc(pretty(a.response_body))}</pre></div>`).join('')}`);
  }

  // ---------- điều khiển ----------

  function renderControl() {
    const o = state.overview;
    $('#shopInfo').innerHTML = [
      ['Tên', o.shop.shop_name], ['shop_id', o.shop.shop_id], ['partner_id', o.shop.partner_id],
      ['access_token', o.access_token], ['Vùng / tiền tệ', `${o.shop.region} / ${o.shop.currency}`],
      ['Push tới', o.push_callback_url],
    ].map(([k, v]) => `<dt>${esc(k)}</dt><dd class="mono">${esc(v)}</dd>`).join('');
  }

  async function resetAll() {
    if (!confirm('Reset toàn bộ simulator? Item, giá, tồn, đơn, hội thoại và log sẽ mất.')) return;
    try {
      await api('/sim/reset', { method: 'POST' });
      Object.assign(state, { conversations: [], selectedConv: null, convSignature: '', pending: null, items: [],
        orders: [], calls: [], lastCallId: 0, pushes: [], lastEmit: null });
      state.itemStamp = new Map();
      state.seenMessages = new Set();
      $('#sendStatus').textContent = '';
      $('#orderStatus').textContent = '';
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
    $('#msgType').addEventListener('change', syncComposer);
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
    document.addEventListener('click', e => {
      if (e.target.id === 'showEmit') { e.preventDefault(); showEmit(); }
    });

    $('#itemSearch').addEventListener('input', renderItems);
    $('#orderItemPick').addEventListener('change', pickOrderItem);
    $('#orderForm').addEventListener('submit', emitOrder);

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
    $('#pushTable tbody').addEventListener('click', e => {
      const tr = e.target.closest('tr[data-id]');
      if (tr) showPush(Number(tr.dataset.id));
    });

    $('#resetBtn').addEventListener('click', resetAll);
    $('#autoRefresh').addEventListener('change', () => { if ($('#autoRefresh').checked) refresh(); });
    document.addEventListener('visibilitychange', () => { if (!document.hidden) refresh(); });
  }

  bind();
  let initial = 'chat';
  try { initial = localStorage.getItem('shopee-sim-tab') || 'chat'; } catch { /* bỏ qua */ }
  if (!$('#tab-' + initial)) initial = 'chat';
  newBuyer();
  syncComposer();
  showTab(initial);
  schedule();
})();
