package ru.stasoft.mynotes.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** Сертификат, который сервер Сбера предъявил приложению: пользователь подтверждает доверие к нему. */
class CertOffer(val subject: String, val issuer: String, val sha256: String, val der: ByteArray)

/** Клиент GigaChat API (Сбер). Нужен ключ авторизации из личного кабинета developers.sber.ru. */
object GigaChat {
    private const val HOST = "ngw.devices.sberbank.ru"
    private const val OAUTH = "https://ngw.devices.sberbank.ru:9443/api/v2/oauth"
    private const val CHAT = "https://gigachat.devices.sberbank.ru/api/v1/chat/completions"
    private var token: String? = null
    private var tokenExp = 0L

    private fun certFile(ctx: Context) = File(ctx.filesDir, "gigachat_ca.der")
    fun hasTrusted(ctx: Context) = certFile(ctx).exists()

    fun saveTrusted(ctx: Context, der: ByteArray) {
        certFile(ctx).writeBytes(der)
        token = null
    }

    private fun defaultTm(ks: KeyStore?): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(ks)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /** Системные сертификаты плюс сохранённый пользователем корневой сертификат Минцифры (только для вызовов GigaChat). */
    private fun sslFactory(ctx: Context): SSLSocketFactory {
        val sys = defaultTm(null)
        val extra: X509TrustManager? = certFile(ctx).takeIf { it.exists() }?.let { f ->
            val cert = CertificateFactory.getInstance("X.509").generateCertificate(f.inputStream())
            val ks = KeyStore.getInstance(KeyStore.getDefaultType())
            ks.load(null, null)
            ks.setCertificateEntry("ru", cert)
            defaultTm(ks)
        }
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = sys.checkClientTrusted(chain, authType)
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                try {
                    sys.checkServerTrusted(chain, authType)
                } catch (e: CertificateException) {
                    if (extra == null) throw e
                    extra.checkServerTrusted(chain, authType)
                }
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = sys.acceptedIssuers + (extra?.acceptedIssuers ?: emptyArray())
        }
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, arrayOf(tm), null)
        return sc.socketFactory
    }

    /** Читает цепочку сертификатов сервера, ничего не проверяя: только чтобы показать пользователю, чему он доверяет. */
    suspend fun fetchChain(): CertOffer = withContext(Dispatchers.IO) {
        val all = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sc = SSLContext.getInstance("TLS")
        sc.init(null, arrayOf(all), null)
        val s = sc.socketFactory.createSocket(HOST, 9443) as SSLSocket
        try {
            s.soTimeout = 10000
            s.startHandshake()
            val last = s.session.peerCertificates.map { it as X509Certificate }.last()
            val sha = MessageDigest.getInstance("SHA-256").digest(last.encoded).joinToString(":") { "%02X".format(it) }
            CertOffer(last.subjectX500Principal.name, last.issuerX500Principal.name, sha, last.encoded)
        } finally {
            s.close()
        }
    }

    private fun post(ctx: Context, url: String, headers: Map<String, String>, body: String): String {
        val c = URL(url).openConnection() as HttpsURLConnection
        c.sslSocketFactory = sslFactory(ctx)
        c.connectTimeout = 15000
        c.readTimeout = 45000
        c.requestMethod = "POST"
        c.doOutput = true
        headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
        c.outputStream.use { it.write(body.toByteArray()) }
        val code = c.responseCode
        val text = (if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.readText().orEmpty()
        if (code !in 200..299) throw IOException("HTTP $code ${text.take(200)}")
        return text
    }

    private fun accessToken(ctx: Context, authKey: String): String {
        val t = token
        if (t != null && System.currentTimeMillis() < tokenExp - 60_000) return t
        val r = post(
            ctx, OAUTH,
            mapOf(
                "Authorization" to "Basic ${authKey.trim()}",
                "RqUID" to java.util.UUID.randomUUID().toString(),
                "Content-Type" to "application/x-www-form-urlencoded",
                "Accept" to "application/json",
            ),
            "scope=GIGACHAT_API_PERS",
        )
        val j = JSONObject(r)
        token = j.getString("access_token")
        tokenExp = j.optLong("expires_at", System.currentTimeMillis() + 25 * 60_000)
        return token!!
    }

    /** Один запрос к модели; возвращает текст ответа. */
    suspend fun chat(ctx: Context, authKey: String, system: String, user: String): String = withContext(Dispatchers.IO) {
        val tok = accessToken(ctx, authKey)
        val body = JSONObject()
            .put("model", "GigaChat")
            .put("temperature", 0.1)
            .put("max_tokens", 600)
            .put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", system))
                    .put(JSONObject().put("role", "user").put("content", user)),
            ).toString()
        val r = post(
            ctx, CHAT,
            mapOf("Authorization" to "Bearer $tok", "Content-Type" to "application/json", "Accept" to "application/json"),
            body,
        )
        JSONObject(r).getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content")
    }
}
