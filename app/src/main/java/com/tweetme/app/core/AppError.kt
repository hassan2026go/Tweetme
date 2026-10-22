package com.tweetme.app.core

import androidx.annotation.StringRes
import com.tweetme.app.R

/** أنواع الأخطاء التي يفهمها التطبيق، مستقلة عن أي خدمة خارجية. */
enum class ErrorKind {
    INVALID_EMAIL, EMAIL_IN_USE, WEAK_PASSWORD, BAD_CREDENTIALS, NETWORK,
    USERNAME_TAKEN, PERMISSION, TOO_MANY, TOO_BIG, UNKNOWN
}

class AppError(val kind: ErrorKind, cause: Throwable? = null) : Exception(kind.name, cause)

fun Throwable.errorKind(): ErrorKind = (this as? AppError)?.kind ?: ErrorKind.UNKNOWN

/** نص الخطأ الظاهر للمستخدم؛ عدّل الصياغة من strings.xml. */
@StringRes
fun errorRes(kind: ErrorKind): Int = when (kind) {
    ErrorKind.INVALID_EMAIL -> R.string.err_invalid_email
    ErrorKind.EMAIL_IN_USE -> R.string.err_email_in_use
    ErrorKind.WEAK_PASSWORD -> R.string.err_weak_password
    ErrorKind.BAD_CREDENTIALS -> R.string.err_bad_credentials
    ErrorKind.NETWORK -> R.string.err_network
    ErrorKind.USERNAME_TAKEN -> R.string.err_username_taken
    ErrorKind.PERMISSION -> R.string.err_permission
    ErrorKind.TOO_MANY -> R.string.err_too_many
    ErrorKind.TOO_BIG -> R.string.err_too_big
    ErrorKind.UNKNOWN -> R.string.err_unknown
}
