#include "javascript-helpers.h"

#include <future>

// Shiromi fork addition (see lib/wvbridge/VENDOR.md):
// reads the cookie header string (`name=value; name=value`, HttpOnly included)
// for the given URL from the WebView2 cookie store.
API_EXPORT(jstring, getCookieString, jlong handle, jstring url) {
    LOGGER_I("getCookieString: handle=%lld url=%p", (long long)handle, url);
    auto *ctx = require_context(env, handle);
    if (!ctx) return nullptr;
    if (url == nullptr) {
        LOGGER_E("getCookieString: url is null, JNI exception will be set");
        throw_jni_exception(env, "java/lang/NullPointerException", "url is null");
        return nullptr;
    }

    const std::wstring wurl = jstring_to_wstring(env, url);
    if (env->ExceptionCheck()) return nullptr;

    struct Result {
        HRESULT hr = E_FAIL;
        std::string value;
    };

    auto completion = std::make_shared<std::promise<Result>>();
    auto future = completion->get_future();

    webview2_thread_run_sync(ctx->thread, [ctx, wurl, completion] {
        if (!ctx->webview) {
            LOGGER_V("getCookieString: webview is null in dispatch");
            completion->set_value(Result{E_FAIL, ""});
            return;
        }

        Microsoft::WRL::ComPtr<ICoreWebView2CookieManager> cookieManager;
        // get_CookieManager lives on the versioned ICoreWebView2_2 interface, not the base
        // ICoreWebView2 (SDK 1.0.3595.46 headers); QueryInterface before calling it.
        Microsoft::WRL::ComPtr<ICoreWebView2_2> webview2;
        HRESULT hr = ctx->webview.As(&webview2);
        if (FAILED(hr)) {
            LOGGER_V("getCookieString: QueryInterface ICoreWebView2_2 failed, hr=0x%lx", (unsigned long)hr);
            completion->set_value(Result{hr, ""});
            return;
        }
        hr = webview2->get_CookieManager(&cookieManager);
        if (FAILED(hr)) {
            LOGGER_V("getCookieString: get_CookieManager failed, hr=0x%lx", (unsigned long)hr);
            completion->set_value(Result{hr, ""});
            return;
        }

        auto callback = Callback<ICoreWebView2GetCookiesCompletedHandler>(
            [completion](HRESULT errorCode, ICoreWebView2CookieList *cookieList) -> HRESULT {
                if (FAILED(errorCode)) {
                    LOGGER_V("getCookieString: GetCookies callback error hr=0x%lx", (unsigned long)errorCode);
                    completion->set_value(Result{errorCode, ""});
                    return S_OK;
                }

                std::string parts;
                UINT count = 0;
                if (cookieList) cookieList->get_Count(&count);
                for (UINT i = 0; i < count; i++) {
                    Microsoft::WRL::ComPtr<ICoreWebView2Cookie> cookie;
                    if (FAILED(cookieList->GetValueAtIndex(i, &cookie)) || !cookie) continue;

                    LPWSTR name = nullptr;
                    LPWSTR value = nullptr;
                    HRESULT nameHr = cookie->get_Name(&name);
                    HRESULT valueHr = cookie->get_Value(&value);
                    if (FAILED(nameHr) || FAILED(valueHr)) {
                        if (name) CoTaskMemFree(name);
                        if (value) CoTaskMemFree(value);
                        continue;
                    }

                    if (!parts.empty()) parts += "; ";
                    parts += wstring_to_utf8_local(name ? name : L"");
                    parts += "=";
                    parts += wstring_to_utf8_local(value ? value : L"");

                    CoTaskMemFree(name);
                    CoTaskMemFree(value);
                }
                completion->set_value(Result{S_OK, parts});
                return S_OK;
            }
        );

        hr = cookieManager->GetCookies(wurl.c_str(), callback.Get());
        if (FAILED(hr)) {
            LOGGER_V("getCookieString: GetCookies failed, hr=0x%lx", (unsigned long)hr);
            completion->set_value(Result{hr, ""});
        }
    });

    Result result = future.get();
    if (FAILED(result.hr)) {
        LOGGER_E("getCookieString: GetCookies failed, hr=0x%lx", (unsigned long)result.hr);
        throw_hresult(env, "GetCookies", result.hr);
        return nullptr;
    }
    LOGGER_V("getCookieString: returning cookie string length=%zu", result.value.size());
    return env->NewStringUTF(result.value.c_str());
}
