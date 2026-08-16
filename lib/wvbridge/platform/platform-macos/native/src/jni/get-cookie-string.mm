#import "javascript-helpers.h"

// Shiromi fork addition (see lib/wvbridge/VENDOR.md):
// reads the cookie header string (`name=value; name=value`, HttpOnly included)
// for the given URL from the WKWebView cookie store.
API_EXPORT(jstring, getCookieString, jlong handle, jstring url) {
    LOGGER_I("getCookieString: handle=%lld", (long long)handle);
    auto *ctx = require_context(env, handle, "getCookieString");
    if (!ctx) return nullptr;
    if (url == nullptr) {
        LOGGER_W("getCookieString: url is null, throwing NPE");
        throw_jni_exception(env, "java/lang/NullPointerException", "url is null");
        return nullptr;
    }

    NSString *nsUrl = jstring_to_nsstring(env, url);
    if (env->ExceptionCheck()) {
        LOGGER_E("getCookieString: JNI exception after string conversion");
        return nullptr;
    }

    dispatch_semaphore_t semaphore = dispatch_semaphore_create(0);
    __block NSMutableArray<NSString *> *parts = nil;
    __block bool webViewAvailable = true;

    runOnMainAsync(^{
        if (!ctx->webView) {
            LOGGER_E("getCookieString: webView is not available in main block");
            webViewAvailable = false;
            dispatch_semaphore_signal(semaphore);
            return;
        }

        WKHTTPCookieStore *cookieStore = ctx->webView.configuration.websiteDataStore.httpCookieStore;
        [cookieStore getAllCookiesWithCompletionHandler:^(NSArray<NSHTTPCookie *> *cookies) {
            // RFC 6265 domain-match against the requested URL host: host == domain, or
            // host ends with "." + domain. Substring matching would admit lookalike
            // domains (e.g. "evil.com.cn") — Shiromi fork hardening.
            NSString *host = [NSURL URLWithString:nsUrl].host;
            NSMutableArray<NSString *> *arr = [NSMutableArray array];
            for (NSHTTPCookie *cookie in cookies) {
                NSString *domain = cookie.domain;
                if (domain.length == 0) continue;
                BOOL match = [host isEqualToString:domain];
                if (!match && host.length > domain.length) {
                    NSString *suffix = [NSString stringWithFormat:@".%@", domain];
                    match = [host hasSuffix:suffix];
                }
                if (!match) continue;
                [arr addObject:[NSString stringWithFormat:@"%@=%@", cookie.name, cookie.value]];
            }
            parts = [arr retain];
            dispatch_semaphore_signal(semaphore);
        }];
    });

    LOGGER_V("getCookieString: waiting for cookie fetch to complete");
    dispatch_semaphore_wait(semaphore, DISPATCH_TIME_FOREVER);
    dispatch_release(semaphore);

    if (!webViewAvailable) {
        LOGGER_E("getCookieString: webview is not available, throwing exception");
        throw_jni_exception(env, "java/lang/RuntimeException", "webview is not available");
        return nullptr;
    }
    if (!parts) {
        LOGGER_V("getCookieString: no cookies returned, returning null");
        return nullptr;
    }

    NSString *joined = [parts componentsJoinedByString:@"; "];
    const char *chars = [joined UTF8String];
    jstring output = env->NewStringUTF(chars ? chars : "");
    [parts release];
    LOGGER_V("getCookieString: returning cookie string length=%lu", (unsigned long) (chars ? strlen(chars) : 0));
    return output;
}
