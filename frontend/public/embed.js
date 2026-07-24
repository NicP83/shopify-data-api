/*
 * Hearns Hobbies embeddable chat widget.
 *
 * Drop onto any page with:
 *   <script src="https://<host>/embed.js"
 *           data-embed-key="hh_xxx"
 *           data-api-base="https://<host>"
 *           data-title="Ask us anything"
 *           data-accent="#c0392b"></script>
 *
 * The embed key must be an "embed"-scoped API key bound to a single agent or
 * workflow (create one in Settings -> API Keys). It can only reach its bound
 * target and is rate-limited server-side, so it is safe to place in page HTML.
 */
(function () {
  var script = document.currentScript;
  if (!script) return;

  var embedKey = script.getAttribute('data-embed-key');
  if (!embedKey) {
    console.error('[hh-embed] missing data-embed-key');
    return;
  }
  var apiBase = script.getAttribute('data-api-base') ||
    (new URL(script.src)).origin;
  var title = script.getAttribute('data-title') || 'Chat with us';
  var accent = script.getAttribute('data-accent') || '#c0392b';

  var history = [];
  var open = false;

  // ---- styles ----
  var css = document.createElement('style');
  css.textContent = [
    '.hhw-bubble{position:fixed;bottom:20px;right:20px;width:56px;height:56px;border-radius:50%;',
    'background:' + accent + ';color:#fff;border:none;cursor:pointer;font-size:24px;box-shadow:0 4px 14px rgba(0,0,0,.25);z-index:2147483000}',
    '.hhw-panel{position:fixed;bottom:88px;right:20px;width:360px;max-width:calc(100vw - 40px);height:520px;max-height:calc(100vh - 120px);',
    'background:#fff;border-radius:12px;box-shadow:0 8px 30px rgba(0,0,0,.3);display:none;flex-direction:column;overflow:hidden;z-index:2147483000;font-family:system-ui,sans-serif}',
    '.hhw-panel.open{display:flex}',
    '.hhw-head{background:' + accent + ';color:#fff;padding:12px 16px;font-weight:600}',
    '.hhw-msgs{flex:1;overflow-y:auto;padding:12px;background:#f7f7f8}',
    '.hhw-msg{margin:8px 0;padding:10px 12px;border-radius:10px;max-width:85%;white-space:pre-wrap;line-height:1.4;font-size:14px}',
    '.hhw-user{background:' + accent + ';color:#fff;margin-left:auto}',
    '.hhw-bot{background:#fff;border:1px solid #e3e3e6;color:#222}',
    '.hhw-form{display:flex;border-top:1px solid #e3e3e6}',
    '.hhw-input{flex:1;border:none;padding:12px;font-size:14px;outline:none}',
    '.hhw-send{border:none;background:' + accent + ';color:#fff;padding:0 16px;cursor:pointer;font-weight:600}'
  ].join('');
  document.head.appendChild(css);

  // ---- markup ----
  var bubble = document.createElement('button');
  bubble.className = 'hhw-bubble';
  bubble.setAttribute('aria-label', title);
  bubble.textContent = '💬';

  var panel = document.createElement('div');
  panel.className = 'hhw-panel';
  panel.innerHTML =
    '<div class="hhw-head">' + escapeHtml(title) + '</div>' +
    '<div class="hhw-msgs"></div>' +
    '<form class="hhw-form"><input class="hhw-input" placeholder="Type a message..." autocomplete="off"/>' +
    '<button class="hhw-send" type="submit">Send</button></form>';

  document.body.appendChild(bubble);
  document.body.appendChild(panel);

  var msgs = panel.querySelector('.hhw-msgs');
  var form = panel.querySelector('.hhw-form');
  var input = panel.querySelector('.hhw-input');

  bubble.addEventListener('click', function () {
    open = !open;
    panel.classList.toggle('open', open);
    if (open) input.focus();
  });

  form.addEventListener('submit', function (e) {
    e.preventDefault();
    var text = input.value.trim();
    if (!text) return;
    addMsg(text, 'user');
    history.push({ role: 'user', content: text });
    input.value = '';
    send(text);
  });

  function send(text) {
    var typing = addMsg('...', 'bot');
    fetch(apiBase + '/api/embed/chat', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-API-Key': embedKey },
      body: JSON.stringify({ message: text, conversationHistory: history })
    })
      .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, j: j }; }); })
      .then(function (res) {
        var reply = res.ok ? (res.j.response || '(no response)')
          : ('Sorry, something went wrong: ' + (res.j.error || res.j.status || 'error'));
        typing.textContent = reply;
        if (res.ok) history.push({ role: 'assistant', content: reply });
      })
      .catch(function () {
        typing.textContent = 'Sorry, I could not reach the server.';
      });
  }

  function addMsg(text, who) {
    var el = document.createElement('div');
    el.className = 'hhw-msg ' + (who === 'user' ? 'hhw-user' : 'hhw-bot');
    el.textContent = text;
    msgs.appendChild(el);
    msgs.scrollTop = msgs.scrollHeight;
    return el;
  }

  function escapeHtml(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }
})();
