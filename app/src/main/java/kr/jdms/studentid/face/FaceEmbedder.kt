package kr.jdms.studentid.face

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.nio.FloatBuffer

/** 정렬된 112x112 얼굴 → 임베딩 벡터. 같은 사람이면 코사인 유사도가 높다. */
interface FaceEmbedder {
    /** 모델이 준비되어 있는지 (없으면 얼굴 '비교'를 할 수 없다) */
    val available: Boolean

    fun embed(aligned112: Bitmap): FloatArray?
    fun close()
}

/**
 * OpenCV Zoo SFace(ONNX) 실행기. 모델 파일(assets/models/face_recognition_sface.onnx)이 없으면 available=false.
 *
 * 전처리는 OpenCV FaceRecognizerSF 와 동일: RGB, 0..255 그대로, NCHW.
 */
class SFaceEmbedder(context: Context) : FaceEmbedder {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null
    private var inputName: String = "data"

    override val available: Boolean get() = session != null

    init {
        try {
            val exists = context.assets.list(MODEL_DIR)?.contains(MODEL_FILE) == true
            if (exists) {
                val bytes = context.assets.open("$MODEL_DIR/$MODEL_FILE").use { it.readBytes() }
                val e = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply { setIntraOpNumThreads(2) }
                val s = e.createSession(bytes, opts)
                inputName = s.inputNames.first()
                env = e
                session = s
            } else {
                Log.i(TAG, "얼굴 비교 모델 없음: assets/$MODEL_DIR/$MODEL_FILE")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "모델 로드 실패", t)
            session = null
        }
    }

    override fun embed(aligned112: Bitmap): FloatArray? {
        val s = session ?: return null
        val e = env ?: return null
        return try {
            val size = 112
            require(aligned112.width == size && aligned112.height == size)
            val px = IntArray(size * size)
            aligned112.getPixels(px, 0, size, 0, 0, size, size)
            val data = FloatArray(3 * size * size)
            val plane = size * size
            for (i in px.indices) {
                val p = px[i]
                data[i] = ((p shr 16) and 0xFF).toFloat()            // R
                data[plane + i] = ((p shr 8) and 0xFF).toFloat()     // G
                data[2 * plane + i] = (p and 0xFF).toFloat()         // B
            }
            OnnxTensor.createTensor(e, FloatBuffer.wrap(data), longArrayOf(1, 3, size.toLong(), size.toLong())).use { input ->
                s.run(mapOf(inputName to input)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    val out = (result[0].value as Array<FloatArray>)[0]
                    FaceMath.normalize(out)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "embed", t)
            null
        }
    }

    override fun close() {
        runCatching { session?.close() }
        session = null
    }

    private companion object {
        const val TAG = "SFaceEmbedder"
        const val MODEL_DIR = "models"
        const val MODEL_FILE = "face_recognition_sface.onnx"
    }
}
