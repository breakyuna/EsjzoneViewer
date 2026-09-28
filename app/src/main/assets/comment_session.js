(function () {
    'use strict';
    if (window !== window.top || window.__esjComment) return;
    let armed = false, dispatched = false, replyOpened = false, queued = false, missingReplySince = 0;
    const emit = value => EsjCommentBridge.postMessage(JSON.stringify(value));
    const snapshot = () => {
        queued = false;
        // Only comment markup crosses the bridge, never the document or form/token fields.
        const nodes = Array.from(document.querySelectorAll('.comment[id^="comment-"]'));
        const html = nodes.map(n => {
            const copy = n.cloneNode(true);
            copy.querySelectorAll('form, input, script, iframe').forEach(x => x.remove());
            return copy.outerHTML;
        }).join('');
        if (html.length > 1500000) {
            emit({type: 'snapshot_too_large'});
            return '__esj_snapshot_too_large__';
        }
        emit({type: 'dom', html: html});
        return html;
    };
    const changed = () => {
        if (!queued) { queued = true; setTimeout(snapshot, 150); }
    };
    const watch = () => {
        new MutationObserver(changed).observe(document.documentElement, {childList: true, subtree: true});
        snapshot();
    };
    if (document.documentElement) watch();
    else document.addEventListener('DOMContentLoaded', watch, {once: true});
    const open = XMLHttpRequest.prototype.open;
    const send = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.open = function (method, url) {
        const target = new URL(url, location.href);
        this.__esjReply = String(method).toUpperCase() === 'POST' && target.origin === location.origin &&
            ['/inc/forum_reply.php', '/inc/gb_reply.php'].includes(target.pathname);
        return open.apply(this, arguments);
    };
    XMLHttpRequest.prototype.send = function () {
        if (this.__esjReply) {
            // One write per native operation, including redirects/reloaded pages.
            if (!armed || dispatched) { this.abort(); return; }
            dispatched = true;
            emit({type: 'dispatched'});
            this.addEventListener('load', () => {
                try {
                    if (this.status < 200 || this.status >= 300) { emit({type: 'unknown'}); return; }
                    const r = JSON.parse(this.responseText);
                    emit({type: 'response', status: Number(r.status),
                        msg: typeof r.msg === 'string' ? r.msg.slice(0, 1000) : '',
                        anchor: typeof r.anchor === 'string' ? r.anchor : ''});
                } catch (_) { emit({type: 'unknown'}); }
            });
            this.addEventListener('error', () => emit({type: 'unknown'}));
            this.addEventListener('timeout', () => emit({type: 'unknown'}));
        }
        return send.apply(this, arguments);
    };
    window.__esjComment = {
        prepare: function (reply) {
            if (!window.jQuery || typeof getAuthToken !== 'function' || typeof editor === 'undefined') return false;
            if (!document.querySelector('nav a[href*="/my/profile"]') || typeof nickname !== 'string' || !nickname) return false;
            if (reply && !replyOpened) {
                const button = Array.from(document.querySelectorAll('.forum_reply'))
                    .find(n => n.getAttribute('data-comment') === reply);
                if (!button) {
                    if (document.readyState !== 'complete') return false;
                    if (!missingReplySince) missingReplySince = Date.now();
                    return Date.now() - missingReplySince >= 2000 ? 'missing_reply' : false;
                }
                button.click(); replyOpened = true;
            }
            const form = document.querySelector(reply ? 'form.replyEditor' : 'form.commentEditor, form.gbEditor');
            if (!form) return false;
            const button = form.querySelector('.btn-send');
            const key = button && button.getAttribute('data-form');
            if (typeof nickname === 'string' && nickname) emit({type: 'author', name: nickname});
            return !!(button && ['forum_reply', 'gb_reply'].includes(button.getAttribute('data-send')) &&
                editor[key] && editor[key].html && form.querySelector('[name="content"]'));
        },
        submit: function (text, reply) {
            if (armed || this.prepare(reply) !== true) return false;
            const form = document.querySelector(reply ? 'form.replyEditor' : 'form.commentEditor, form.gbEditor');
            const button = form.querySelector('.btn-send');
            const escaped = document.createElement('div'); escaped.textContent = text;
            editor[button.getAttribute('data-form')].html.set(escaped.innerHTML.replace(/\n/g, '<br>'));
            armed = true;
            snapshot();
            button.click();
            return true;
        },
        snapshot: snapshot
    };
}());
