const {test} = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const source = fs.readFileSync('app/src/main/assets/comment_session.js', 'utf8');
function page(options = {}) {
    const events = []; let clicks = 0, writes = 0, editorHtml = '', now = 1;
    class XHR {
        open() {} send() { writes++; } abort() { this.aborted = true; }
        addEventListener(name, listener) { this[name] = listener; }
    }
    const button = {getAttribute: n => n === 'data-form' ? 'commentEditor' : 'forum_reply', click() { clicks++; }};
    const form = {querySelector: selector => selector === '.btn-send' ? button : {}};
    const document = {documentElement: {}, readyState: 'complete',
        querySelectorAll: selector => selector === '.forum_reply'
            ? (options.replyButton ? [options.replyButton] : [])
            : selector.startsWith('.comment[') && options.commentMarkup
                ? [{cloneNode: () => ({outerHTML: options.commentMarkup, querySelectorAll: () => []})}]
                : [],
        querySelector: () => form,
        createElement: () => ({set textContent(t) { this.innerHTML = t.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;'); }})};
    const context = {document, XMLHttpRequest: XHR, URL, Date: {now: () => now},
        location: {href: 'https://example.test/forum/1/2.html', origin: 'https://example.test'},
        MutationObserver: class {observe() {}}, setTimeout() {},
        EsjCommentBridge: {postMessage: v => events.push(JSON.parse(v))},
        jQuery: {}, getAuthToken() {}, nickname: 'Fixture author',
        editor: {commentEditor: {html: {set(v) { editorHtml = v; }}}}};
    context.window = context; context.top = context;
    vm.createContext(context); vm.runInContext(source, context);
    return {context, events, stats: () => ({clicks, writes, editorHtml}), xhr: () => new XHR(),
        advanceTime: ms => { now += ms; }};
}
test('fills plain text safely and only clicks once', () => {
    const p = page();
    assert.equal(p.context.__esjComment.submit('<script>\nhello', ''), true);
    assert.equal(p.context.__esjComment.submit('duplicate', ''), false);
    assert.deepEqual(p.stats(), {clicks: 1, writes: 0, editorHtml: '&lt;script&gt;<br>hello'});
});
test('unarmed/reloaded page cannot dispatch a comment', () => {
    const p = page(), xhr = p.xhr(); xhr.open('POST', '/inc/forum_reply.php'); xhr.send();
    assert.equal(p.stats().writes, 0); assert.equal(xhr.aborted, true);
});
test('one write, business response forwarded without arbitrary response fields', () => {
    const p = page(); p.context.__esjComment.submit('hello', '');
    const xhr = p.xhr(); xhr.open('POST', '/inc/forum_reply.php'); xhr.send();
    xhr.status = 200; xhr.responseText = JSON.stringify({status: 214, msg: 'Daily limit', privateField: 'must not cross bridge'}); xhr.load();
    const second = p.xhr(); second.open('POST', '/inc/forum_reply.php'); second.send();
    assert.equal(p.stats().writes, 1);
    assert.deepEqual(p.events.find(e => e.type === 'response'), {type: 'response', status: 214, msg: 'Daily limit', anchor: ''});
});
test('HTTP failure cannot masquerade as accepted and malformed JSON stays unknown', () => {
    for (const [status, responseText] of [[500, '{"status":200}'], [200, '<html>error</html>']]) {
        const p = page(); p.context.__esjComment.submit('hello', '');
        const xhr = p.xhr(); xhr.open('POST', '/inc/forum_reply.php'); xhr.send();
        xhr.status = status; xhr.responseText = responseText; xhr.load();
        assert.equal(p.events.some(e => e.type === 'response'), false);
        assert.equal(p.events.some(e => e.type === 'unknown'), true);
    }
});
test('accepted response retains only the comment anchor', () => {
    const p = page(); p.context.__esjComment.submit('hello', '');
    const xhr = p.xhr(); xhr.open('POST', '/inc/gb_reply.php'); xhr.send();
    xhr.status = 200; xhr.responseText = '{"status":200,"anchor":"#comment-42"}'; xhr.load();
    assert.equal(p.events.find(e => e.type === 'response').anchor, '#comment-42');
});
test('a reply absent from the loaded website DOM fails before any write', () => {
    const p = page();
    assert.equal(p.context.__esjComment.prepare('42-7'), false);
    p.advanceTime(2000);
    assert.equal(p.context.__esjComment.prepare('42-7'), 'missing_reply');
    assert.equal(p.context.__esjComment.submit('hello', '42-7'), false);
    assert.equal(p.stats().clicks, 0);
    assert.equal(p.stats().writes, 0);
});
test('large comment pages report their size instead of sending truncated markup', () => {
    const p = page({commentMarkup: 'x'.repeat(1500001)});
    assert.equal(p.context.__esjComment.snapshot(), '__esj_snapshot_too_large__');
    assert.equal(p.events.some(e => e.type === 'snapshot_too_large'), true);
    assert.equal(p.events.some(e => e.type === 'dom'), false);
});
