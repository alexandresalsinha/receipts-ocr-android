package com.example.receiptsocr.data.remote

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.example.receiptsocr.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Result of asking the vision model to read a receipt photo.
 * [merchantName] and [totalAmount] are the primary fields requested from the model;
 * [date] and [category] are extra hints used to pre-fill the review screen.
 */
data class ReceiptExtraction(
    val merchantName: String?,
    val totalAmount: Double?,
    val date: String?,
    val category: String?,
    val rawResponse: String
)

/**
 * Sends a receipt photo to Google's Gemini vision model and asks it to return the
 * merchant name and total price. Uses the Generative Language API (generateContent).
 *
 * NOTE: The API key is injected from local.properties via BuildConfig (kept out of git). It is
 * still compiled into the APK, and anything shipped in an APK can be extracted, so for a
 * production release this key should be proxied through a backend rather than bundled with the
 * app, and ideally restricted to this app's package + signing key in Google Cloud Console.
 */
object GeminiClient {

    private val API_KEY = BuildConfig.GEMINI_API_KEY
    private const val MODEL = "gemini-2.5-flash"
    private const val ENDPOINT =
        "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent"

    // Keep the uploaded image small enough for a fast, cheap request while staying legible.
    private const val MAX_IMAGE_DIMENSION = 1536
    private const val JPEG_QUALITY = 85
    private const val TIMEOUT_MS = 60_000

    private val VALID_CATEGORIES = listOf(
        "Groceries", "Food & Dining", "Travel", "Shopping", "Utilities",
        "Fuel", "Health", "Entertainment", "Electronics", "Clothing", "Home", "Miscellaneous"
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Uploads [imageBytes] (a JPEG/PNG receipt photo) and returns the extracted fields.
     * @throws IOException on network/HTTP failure or an unparseable response.
     */
    suspend fun extractReceipt(imageBytes: ByteArray): ReceiptExtraction = withContext(Dispatchers.IO) {
        if (API_KEY.isBlank()) {
            throw IOException("Missing Gemini API key. Add GEMINI_API_KEY to local.properties and rebuild.")
        }
        val base64Image = encodeScaledJpeg(imageBytes)
        val requestBody = buildRequestBody(base64Image)

        val connection = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", API_KEY)
        }

        try {
            connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) {
                throw IOException("Gemini request failed (HTTP $status): ${responseText.take(300)}")
            }

            parseResponse(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun buildRequestBody(base64Image: String): String {
        val prompt = buildString {
            append("You are reading a photo of a shopping receipt. Extract the following and ")
            append("respond with ONLY a single JSON object (no markdown, no explanation) with exactly these keys:\n")
            append("- \"merchantName\": the store or business name (string, or null if unreadable)\n")
            append("- \"totalAmount\": the final total amount paid as a plain number without a currency symbol, or null\n")
            append("- \"date\": the purchase date formatted as DD/MM/YYYY, or null if not visible\n")
            append("- \"category\": one of ${VALID_CATEGORIES.joinToString(", ")}")
        }

        val body: JsonObject = buildJsonObject {
            putJsonArray("contents") {
                addJsonObject {
                    put("role", "user")
                    putJsonArray("parts") {
                        addJsonObject { put("text", prompt) }
                        addJsonObject {
                            putJsonObject("inline_data") {
                                put("mime_type", "image/jpeg")
                                put("data", base64Image)
                            }
                        }
                    }
                }
            }
            putJsonObject("generationConfig") {
                put("responseMimeType", "application/json")
                put("temperature", 0)
            }
        }
        return body.toString()
    }

    private fun parseResponse(responseText: String): ReceiptExtraction {
        val root = json.parseToJsonElement(responseText).jsonObject

        val content = root["candidates"]?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.firstOrNull()?.jsonObject
            ?.get("text")?.jsonPrimitive?.contentOrNullSafe()
            ?: throw IOException("Gemini response contained no readable text. ${responseText.take(200)}")

        // Strip any accidental markdown fences before parsing the JSON payload.
        val cleaned = content.trim()
            .removePrefix("```json").removePrefix("```").removeSuffix("```")
            .trim()

        val extracted = try {
            json.parseToJsonElement(cleaned).jsonObject
        } catch (e: Exception) {
            throw IOException("Could not parse receipt data from model output: ${content.take(200)}")
        }

        val category = extracted["category"]?.jsonPrimitive?.contentOrNullSafe()
            ?.let { value -> VALID_CATEGORIES.firstOrNull { it.equals(value, ignoreCase = true) } }

        return ReceiptExtraction(
            merchantName = extracted["merchantName"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() },
            totalAmount = extracted["totalAmount"]?.let { readNumber(it) },
            date = extracted["date"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() },
            category = category,
            rawResponse = cleaned
        )
    }

    private fun readNumber(element: JsonElement): Double? {
        val primitive = element.jsonPrimitive
        primitive.doubleOrNull?.let { return it }
        // Fall back to parsing a stringified number like "31.17" or "31,17".
        return primitive.contentOrNullSafe()
            ?.replace(",", ".")
            ?.filter { it.isDigit() || it == '.' || it == '-' }
            ?.toDoubleOrNull()
    }

    private fun JsonPrimitive.contentOrNullSafe(): String? {
        return if (this is JsonNull) null else content.takeIf { it != "null" }
    }

    private fun encodeScaledJpeg(imageBytes: ByteArray): String {
        val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: throw IOException("Could not decode the captured image.")

        val scaled = scaleDown(original)
        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        if (scaled != original) scaled.recycle()
        original.recycle()

        return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
    }

    private fun scaleDown(bitmap: Bitmap): Bitmap {
        val largest = maxOf(bitmap.width, bitmap.height)
        if (largest <= MAX_IMAGE_DIMENSION) return bitmap
        val ratio = MAX_IMAGE_DIMENSION.toFloat() / largest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}
