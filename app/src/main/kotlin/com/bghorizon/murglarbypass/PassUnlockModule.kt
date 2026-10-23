package com.bghorizon.murglarbypass

import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.lang.reflect.Method

/**
 * Murglar Pass Bypass — LSPosed module (libxposed API 102).
 *
 * Target: com.badmanners.murglar2 (Murglar 6.9.325).
 *
 * How the gate works (verified from decoded APK, smali level):
 * - The app has NO Google Play Billing integration (no BILLING permission, no
 *   BillingClient). Subscription state comes from the app's own backend:
 *   `POST /api/user/me` returns `SignedUser{user, sign, passLimitExceeded}`.
 * - Single source of entitlement truth is
 *   `com.badmanners.murglar.profile.model.User`:
 *   `hasActiveSubscription()` (Pass) and `hasNoAdsPremium()` (Premium),
 *   plus `SignedUser.isPassLimitExceeded()` (daily Pass download quota).
 * - Every gate in the app funnels through these methods: the profile repository
 *   combinators, the paywall/profile screen, PlayerService ("Activate Murglar Pass"
 * on playback), NodesProvider, the settings/theme summary rows,
 * and per-source availability badges.
 *
 * CRITICAL DETAIL — the server signs the profile: the app builds a `":"`-joined
 * payload of `[userId, username, email, hasNoAdsPremium, subscriptionEpoch, ...]`
 * and verifies it with `SHA1withRSA` against an embedded public key. Flipping the
 * `User` getters invalidates that signature, and the app reacts with logout +
 * `SuppressedException` (surfaced as "Ошибка: null"). `java.security.Signature` is
 * used NOWHERE else in the whole app (verified across all dex), so the module
 * additionally neutralizes `Signature#verify` — the genuine server data then
 * "passes" verification while the gates below read the unlocked values.
 *
 * Hooking the methods below therefore unlocks the whole subscription gate.
 * A free account (logged-in state) is still
 * required, because the combinators also require `ProfileStatus != LOGGED_OUT`.
 */
class PassUnlockModule : XposedModule() {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != TARGET_PACKAGE) return
        val cl = param.defaultClassLoader
        mlog("armed in ${param.packageName}")

        // RSA profile-signature check -> always pass (see class kdoc; the only
        // Signature usage in the entire app is the profile check above).
        hookSignatureVerify()
        // Pass subscription gate -> always active.
        hookBoolean(cl, USER_CLASS, "hasActiveSubscription", true)
        // Premium (no-ads) gate -> always active.
        hookBoolean(cl, USER_CLASS, "hasNoAdsPremium", true)
        // Daily Pass download quota flag -> never exceeded.
        hookBoolean(cl, SIGNED_USER_CLASS, "isPassLimitExceeded", false)
        // Expiry timestamp shown in UI / used for display formatting -> far future.
        hookFarFutureInstant(cl)
    }

    private fun hookBoolean(cl: ClassLoader, className: String, methodName: String, value: Boolean) {
        try {
            val method: Method = Class.forName(className, false, cl).getMethod(methodName)
            hook(method).intercept(ConstHooker(if (value) java.lang.Boolean.TRUE else java.lang.Boolean.FALSE))
            mlog("hooked $className#$methodName -> $value")
        } catch (t: Throwable) {
            mlog("FAILED to hook $className#$methodName", t)
        }
    }

    private fun hookSignatureVerify() {
        try {
            val verify1: Method = java.security.Signature::class.java.getMethod("verify", ByteArray::class.java)
            hook(verify1).intercept(ConstHooker(java.lang.Boolean.TRUE))
            mlog("hooked Signature#verify([B) -> true")
        } catch (t: Throwable) {
            mlog("FAILED to hook Signature#verify([B)", t)
        }
        try {
            val verify3: Method = java.security.Signature::class.java.getMethod(
                "verify", ByteArray::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
            hook(verify3).intercept(ConstHooker(java.lang.Boolean.TRUE))
            mlog("hooked Signature#verify([BII) -> true")
        } catch (t: Throwable) {
            mlog("FAILED to hook Signature#verify([BII)", t)
        }
    }

    private fun hookFarFutureInstant(cl: ClassLoader) {
        try {
            val userClass = Class.forName(USER_CLASS, false, cl)
            val getter: Method = userClass.getMethod("getSubscriptionActiveTill")
            val instantClass = Class.forName(INSTANT_CLASS, false, cl)
            val farFuture = instantClass
                .getMethod("ofEpochSecond", Long::class.javaPrimitiveType)
                .invoke(null, FAR_FUTURE_EPOCH_SECOND)
            hook(getter).intercept(ConstHooker(farFuture!!))
            mlog("hooked User#getSubscriptionActiveTill -> 2100-01-01")
        } catch (t: Throwable) {
            mlog("FAILED to hook getSubscriptionActiveTill", t)
        }
    }

    private fun mlog(message: String) {
        log(Log.INFO, LOG_TAG, message)
    }

    private fun mlog(message: String, throwable: Throwable) {
        log(Log.WARN, LOG_TAG, message, throwable)
    }

    /** Hooker that ignores the original implementation and returns a constant. */
    private class ConstHooker(private val value: Any) : XposedInterface.Hooker {
        override fun intercept(chain: XposedInterface.Chain): Any = value
    }

    companion object {
        private const val TARGET_PACKAGE = "com.badmanners.murglar2"
        private const val USER_CLASS = "com.badmanners.murglar.profile.model.User"
        private const val SIGNED_USER_CLASS = "com.badmanners.murglar.profile.model.SignedUser"
        private const val INSTANT_CLASS = "org.threeten.bp.Instant"

        /** 2100-01-01T00:00:00Z — displayed as "Pass valid till" in profile UI. */
        private const val FAR_FUTURE_EPOCH_SECOND = 4102444800L

        private const val LOG_TAG = "MurglarPass"
    }
}
