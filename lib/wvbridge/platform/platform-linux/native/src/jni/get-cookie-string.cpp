#include "javascript-helpers.h"

#include <future>

// Shiromi fork addition (see lib/wvbridge/VENDOR.md):
// reads the cookie header string (`name=value; name=value`, HttpOnly included)
// for the given URI from the WebKitGTK cookie manager.
API_EXPORT(jstring, getCookieString, jlong handle, jstring url) {
    LOGGER_I("getCookieString: handle=%lld", (long long)handle);

    auto *ctx = require_context(env, handle);
    if (!ctx) return nullptr;
    if (url == nullptr) {
        LOGGER_E("getCookieString: null url, throwing NPE");
        throw_jni_exception(env, "java/lang/NullPointerException", "url is null");
        return nullptr;
    }

    const std::string uri = jstring_to_string(env, url);
    if (env->ExceptionCheck()) {
        LOGGER_W("getCookieString: JVM exception after jstring_to_string, aborting");
        return nullptr;
    }

    struct Result {
        bool ok = false;
        std::string value;
        std::string error;
    };

    auto completion = std::make_shared<std::promise<Result>>();
    auto future = completion->get_future();

    LOGGER_V("getCookieString: dispatching to GTK thread");
    bool dispatched = wvbridge::gtk_run_on_thread_sync([ctx, uri, completion] {
        if (!ctx->webview) {
            LOGGER_V("getCookieString: ctx->webview is null in GTK thread");
            completion->set_value(Result{false, "", "webview is not available"});
            return;
        }

        WebKitWebsiteDataManager *dataManager = webkit_web_context_get_website_data_manager(
            webkit_web_view_get_context(ctx->webview));
        WebKitCookieManager *cookieManager = webkit_website_data_manager_get_cookie_manager(dataManager);

        auto *completionPtr = new std::shared_ptr<std::promise<Result>>(completion);
        webkit_cookie_manager_get_cookies(
            cookieManager,
            uri.c_str(),
            nullptr,
            [](GObject *object, GAsyncResult *asyncResult, gpointer userData) {
                auto completionHolder = static_cast<std::shared_ptr<std::promise<Result>> *>(userData);
                std::shared_ptr<std::promise<Result>> completion = *completionHolder;
                delete completionHolder;

                GError *error = nullptr;
                // WebKitGTK 2.52：finish 返回 GList*（元素为 SoupCookie*）
                GList *cookies = webkit_cookie_manager_get_cookies_finish(
                    WEBKIT_COOKIE_MANAGER(object),
                    asyncResult,
                    &error
                );

                if (error) {
                    std::string message = error->message ? error->message : "WebKitGTK cookie fetch failed";
                    LOGGER_V("getCookieString: async callback error=%s", message.c_str());
                    g_error_free(error);
                    completion->set_value(Result{false, "", message});
                    return;
                }

                std::string parts;
                guint count = 0;
                for (GList *l = cookies; l; l = l->next) {
                    count++;
                    SoupCookie *cookie = static_cast<SoupCookie *>(l->data);
                    const gchar *name = soup_cookie_get_name(cookie);
                    const gchar *value = soup_cookie_get_value(cookie);
                    if (!parts.empty()) parts += "; ";
                    parts += name ? name : "";
                    parts += "=";
                    parts += value ? value : "";
                }
                g_list_free_full(cookies, reinterpret_cast<GDestroyNotify>(soup_cookie_free));

                LOGGER_V("getCookieString: async callback done, %u cookies", count);
                completion->set_value(Result{true, parts, ""});
            },
            completionPtr
        );
    });

    if (!dispatched) {
        // GTK runtime not running/stopped: the lambda never executed, so the
        // promise would otherwise never complete and the caller would hang.
        LOGGER_E("getCookieString: GTK thread unavailable, aborting");
        completion->set_value(Result{false, "", "GTK runtime unavailable"});
    }

    Result result = future.get();
    if (!result.ok) {
        LOGGER_E("getCookieString: failed, error=%s", result.error.c_str());
        throw_jni_exception(env, "java/lang/RuntimeException", result.error.c_str());
        return nullptr;
    }
    LOGGER_V("getCookieString: returning cookie string length=%zu", result.value.size());
    return env->NewStringUTF(result.value.c_str());
}
