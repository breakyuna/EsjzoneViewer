package com.breakyuna.esjzone.network.wenku8

import com.google.gson.Gson
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup

/** Form fields verified from the user's login.php MHT, 2026-10-10. */
internal object Wenku8LoginForm {
    private fun helpers(attemptId: String): String = """
        const origin = ${Gson().toJson(Wenku8Urls.BASE)};
        const attemptId = ${Gson().toJson(attemptId)};
        const durations = ['0','86400','2592000','315360000'];
        let formReason = 'form-count';
        function incompatible(reason) { formReason = reason; return null; }
        function allowed(raw) {
            try { const u = new URL(raw); return u.origin === origin && !u.username && !u.password; }
            catch (_) { return false; }
        }
        function attemptDuration() {
            try {
                const value = sessionStorage.getItem('__esjWenkuLoginAttempt') || '';
                const prefix = attemptId + ':';
                const duration = value.startsWith(prefix) ? value.slice(prefix.length) : '';
                return durations.includes(duration) ? duration : null;
            } catch (_) { return null; }
        }
        function form() {
            if (!allowed(location.href)) return incompatible('origin');
            const forms = document.querySelectorAll('form[name="frmlogin"]');
            if (forms.length !== 1) return null;
            const f = forms[0];
            let action;
            // Read the actual attribute: named controls can shadow HTMLFormElement.action.
            try { action = new URL(f.getAttribute('action') || location.href, document.baseURI); }
            catch (_) { return incompatible('action'); }
            // The browser submits the site's unchanged action/query/hidden fields.
            if (!allowed(action.href) || action.pathname !== '/login.php') return incompatible('action');
            if (f.method.toLowerCase() !== 'post' ||
                f.enctype.toLowerCase() !== 'application/x-www-form-urlencoded') return incompatible('encoding');
            const users = f.querySelectorAll('input[type="text"][name="username"]');
            const passwords = f.querySelectorAll('input[type="password"][name="password"]');
            const choices = f.querySelectorAll('select[name="usecookie"]');
            const buttons = f.querySelectorAll('input[type="submit"][name="submit"]');
            if ([users,passwords,choices,buttons].some(items => items.length !== 1)) return incompatible('fields');
            const u = users[0], p = passwords[0], c = choices[0], b = buttons[0];
            if ([u,p,c,b].some(element => element.disabled || element.form !== f)) return incompatible('controls');
            if (!durations.every(value => Array.from(c.options).some(option => option.value === value)))
                return incompatible('durations');
            // An added CAPTCHA or other required interactive field belongs to the user.
            // Hidden inputs remain untouched and continue participating in the real form.
            if (Array.from(f.elements).some(element => ['INPUT','SELECT','TEXTAREA'].includes(element.tagName) &&
                !['hidden','reset','button'].includes(element.type) && ![u,p,c,b].includes(element) && !element.disabled))
                return incompatible('extra-input');
            if (f.__esjWenkuAttemptId !== attemptId) {
                f.__esjWenkuAttemptId = attemptId;
                f.addEventListener('submit', () => {
                    if (durations.includes(c.value)) {
                        try { sessionStorage.setItem('__esjWenkuLoginAttempt', attemptId + ':' + c.value); }
                        catch (_) {}
                    }
                }, true);
            }
            return {f,u,p,c,b};
        }
        function signedIn() {
            return /欢迎|歡迎/.test(document.body?.innerText || '') &&
                Array.from(document.querySelectorAll('a[href]')).some(a =>
                    /^(退出|退出登录|退出登錄|登出)$/.test(a.textContent.replace(/\s/g,'')) && allowed(a.href));
        }
    """.trimIndent()

    fun inspect(attemptId: String): String = """
        (function(){
            ${helpers(attemptId)}
            if (location.href === 'about:blank') return {state:'preparing',reason:'empty-document'};
            if (!allowed(location.href)) return {state:'incompatible',reason:'origin'};
            if (document.readyState !== 'complete') return {state:'preparing'};
            const duration = attemptDuration();
            if (document.querySelector('#challenge-stage, #challenge-form, .cf-turnstile') ||
                /^just a moment|^attention required/i.test(document.title.trim())) return {state:'manual'};
            // A completed callback can still expose the initial empty document.
            // Wait for real content instead of classifying a blank WebView as a changed form.
            const body = document.body;
            if (!body || (!(body.innerText || '').trim() &&
                !body.querySelector('form,input,select,textarea,button,img,iframe,canvas,svg,video,object,embed')))
                return {state:'preparing',reason:'empty-document'};
            if (signedIn()) return {state:'signedIn',duration};
            // Only an explicit denial after an observed submission is classified as rejection.
            // Other response wording remains unknown and is shown in the original document.
            if (duration !== null && /用户名或密码(?:输入)?错误|密码不正确|登录失败/.test(
                (document.body?.innerText || '').replace(/\s/g,''))) return {state:'rejected',duration};
            const fields = form();
            if (fields) return {state:'form',duration};
            // No rejection-page fixture is available; no error container selector is assumed.
            return {state:duration === null ? 'incompatible' : 'unknown',duration,reason:formReason};
        })()
    """.trimIndent()

    fun submit(attemptId: String, username: String, password: String, duration: String): String {
        val payload = Gson().toJson(mapOf("username" to username, "password" to password, "duration" to duration))
        return """
            (function(){
                ${helpers(attemptId)}
                const fields = form();
                if (!fields || document.readyState !== 'complete') return {state:'incompatible'};
                const data = $payload;
                if (!durations.includes(data.duration)) return {state:'incompatible'};
                const {f,u,p,c,b} = fields;
                if ((u.maxLength >= 0 && data.username.length > u.maxLength) ||
                    (p.maxLength >= 0 && data.password.length > p.maxLength)) return {state:'length'};
                u.value = data.username;
                p.value = data.password;
                c.value = data.duration;
                for (const element of [u,p,c]) {
                    element.dispatchEvent(new Event('input',{bubbles:true}));
                    element.dispatchEvent(new Event('change',{bubbles:true}));
                }
                if (!f.checkValidity()) {
                    p.value = '';
                    return {state:'invalid'};
                }
                // The real submitter contributes its original name/value and runs site validation.
                b.click();
                return {state:'submitted'};
            })()
        """.trimIndent()
    }

    val clearPassword: String = """
        (function(){
            if (location.origin !== ${Gson().toJson(Wenku8Urls.BASE)}) return;
            document.querySelectorAll('form[name="frmlogin"] input[name="password"]')
                .forEach(input => {input.value = '';});
            try {sessionStorage.removeItem('__esjWenkuLoginAttempt');} catch (_) {}
        })()
    """.trimIndent()
}

/** Welcome + an actual same-origin sign-out link, followed by a protected native request. */
internal fun wenku8SignedInDocument(html: String, url: String): Boolean {
    val document = Jsoup.parse(html, url)
    if (!Regex("欢迎|歡迎").containsMatchIn(document.text())) return false
    return document.select("a[href]").any { link ->
        // Jsoup absUrl rebuilds URLs without user info; validate the original href instead.
        val target = link.baseUri().toHttpUrlOrNull()?.resolve(link.attr("href"))
        link.text().filterNot(Char::isWhitespace) in setOf("退出", "退出登录", "退出登錄", "登出") &&
            target?.let { it.isHttps && it.host == "www.wenku8.net" && it.port == 443 &&
                it.username.isEmpty() && it.password.isEmpty() } == true
    }
}
