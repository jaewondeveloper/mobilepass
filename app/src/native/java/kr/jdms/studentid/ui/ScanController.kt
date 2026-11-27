package kr.jdms.studentid.ui

import android.graphics.BitmapFactory
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kr.jdms.studentid.face.FaceEmbedder
import kr.jdms.studentid.face.FaceScanner
import kr.jdms.studentid.face.FaceSession
import kr.jdms.studentid.scan.CardScanner

/**
 * 스캔 흐름(글자/바코드 → 앞면 얼굴 → 전면카메라 본인 확인 → 프로필 사진 → 등록)의 상태와 전환.
 * 웹 버전의 JS 와 같은 순서·같은 문구이고, 카메라/인식은 같은 네이티브 클래스(CardScanner, FaceScanner)를 쓴다.
 */
class ScanController(
    private val activity: NativeActivity,
    private val state: AppState,
    private val toast: ToastState,
    private val embedder: Lazy<FaceEmbedder>,
) {
    val previewView = PreviewView(activity).apply {
        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        scaleType = PreviewView.ScaleType.FILL_CENTER
    }

    var step by mutableStateOf(Step.ID)
    var hint by mutableStateOf(HINT_ID)
    var detected by mutableStateOf(false)
    var previewVisible by mutableStateOf(false)
    var dialog by mutableStateOf<DialogSpec?>(null)
    /** 값이 바뀌면 화면이 카메라를 (다시) 켠다 */
    var startTick by mutableIntStateOf(0)

    /** 스캔 페이지를 닫는 동작 (액티비티가 지정) */
    var close: () -> Unit = {}

    private var pending: Pair<String, String>? = null
    private var fails = 0
    private var scanner: CardScanner? = null
    private var faceScanner: FaceScanner? = null

    val title: String get() = when (step) { Step.ID -> "학생증 스캔"; Step.CARD_FACE -> "학생증 앞면"; Step.SELFIE -> "얼굴 확인" }

    fun begin() {
        step = Step.ID; hint = HINT_ID; detected = false; previewVisible = false; dialog = null
        pending = null; fails = 0; startTick++
    }

    private fun setStep(s: Step) {
        step = s
        hint = when (s) { Step.ID -> HINT_ID; Step.CARD_FACE -> HINT_CARD_FACE; Step.SELFIE -> HINT_SELFIE }
        detected = false
        stopCamera()
        startTick++
    }

    /** 화면에서 틀이 자리를 잡은 뒤 호출 */
    fun startCamera() {
        if (dialog != null) return
        activity.ensureCamera { ok ->
            if (!ok) { toast.show("카메라 권한을 허용해주세요"); close(); return@ensureCamera }
            previewVisible = true
            when (step) {
                Step.ID -> { faceScanner?.stop(); scanner().start() }
                Step.CARD_FACE -> { scanner?.stop(); faceScanner().start(FaceScanner.Mode.CARD) }
                Step.SELFIE -> { scanner?.stop(); faceScanner().start(FaceScanner.Mode.SELFIE) }
            }
        }
    }

    fun stopCamera() {
        scanner?.stop()
        faceScanner?.stop()
        previewVisible = false
    }

    /** 스캔 페이지를 벗어날 때 */
    fun end() {
        stopCamera()
        dialog = null
        FaceSession.clear()          // 임시로 들고 있던 증명사진 특징값/프로필 후보 삭제
        pending = null
    }

    fun release() {
        scanner?.release()
        faceScanner?.release()
    }

    fun prewarm() { scanner().prewarm() }

    private fun scanner(): CardScanner = scanner ?: CardScanner(activity, previewView, idListener).also { scanner = it }
    private fun faceScanner(): FaceScanner = faceScanner ?: FaceScanner(activity, previewView, embedder.value, faceListener).also { faceScanner = it }

    // ───────────── 글자/바코드 ─────────────

    private val idListener = object : CardScanner.Listener {
        override fun onState(hint: String, detected: Boolean) { this@ScanController.hint = hint; this@ScanController.detected = detected }

        override fun onResult(name: String, code: String, thumbJpeg: ByteArray) {
            scanner?.stop()
            previewVisible = false
            val thumb = BitmapFactory.decodeByteArray(thumbJpeg, 0, thumbJpeg.size)?.asImageBitmap()
            dialog = DialogSpec(
                title = "정보를 확인해주세요",
                content = {
                    val c = LocalColors.current
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (thumb != null) Image(
                            thumb, "학생증 스캔 이미지",
                            Modifier.size(110.dp, 165.dp).clip(RoundedCornerShape(14.dp)).border(2.dp, c.divider, RoundedCornerShape(14.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        InfoRow("인식된 이름", name, false)
                        InfoRow("바코드 식별 코드", code, true)
                    }
                },
                actions = listOf(
                    DialogAction("재스캔") { retryId() },
                    DialogAction("확인") {
                        dialog = null
                        pending = name to code
                        fails = 0
                        setStep(if (embedder.value.available) Step.CARD_FACE else Step.SELFIE)     // 본인 확인 전에는 등록하지 않는다
                    },
                ),
                onCancel = { retryId() },
            )
        }

        override fun onError(message: String) { toast.show(message); close() }
    }

    private fun retryId() {
        dialog = null
        hint = HINT_ID; detected = false
        startTick++
    }

    // ───────────── 얼굴 ─────────────

    private val faceListener = object : FaceScanner.Listener {
        override fun onState(hint: String, detected: Boolean) { this@ScanController.hint = hint; this@ScanController.detected = detected }

        override fun onCardFace() { faceScanner?.stop(); previewVisible = false; setStep(Step.SELFIE) }

        override fun onSelfie(result: FaceScanner.SelfieResult) {
            faceScanner?.stop()
            previewVisible = false
            when {
                result.matched == false -> showFail()
                result.png == null -> { toast.show("프로필 사진을 만들지 못했어요. 다시 시도해 주세요"); startTick++ }
                else -> showPortrait(result)
            }
        }

        override fun onError(message: String) { toast.show(message); close() }
    }

    private fun showFail() {
        fails++
        val last = fails >= 3
        dialog = DialogSpec(
            title = "본인 확인에 실패했어요",
            message = if (last) "여러 번 확인되지 않았어요. 학생증을 확인한 뒤 처음부터 다시 시도해 주세요."
            else "학생증 사진과 얼굴이 일치하지 않아요. 밝은 곳에서 정면을 바라보고 다시 시도해 주세요.",
            cancelable = false,
            actions = if (last) listOf(DialogAction("닫기") { dialog = null; FaceSession.clear(); close() })
            else listOf(
                DialogAction("앞면 다시 찍기") { dialog = null; setStep(Step.CARD_FACE) },
                DialogAction("다시 시도") { dialog = null; setStep(Step.SELFIE) },
            ),
        )
    }

    private fun showPortrait(r: FaceScanner.SelfieResult) {
        val png = r.png ?: return
        val bmp = BitmapFactory.decodeByteArray(png, 0, png.size)?.asImageBitmap()
        dialog = DialogSpec(
            title = "프로필 사진",
            content = {
                val c = LocalColors.current
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (bmp != null) Image(bmp, "프로필 사진", Modifier.size(200.dp, 267.dp).clip(RoundedCornerShape(20.dp)).background(c.tile), contentScale = ContentScale.Crop)
                    Text(
                        if (r.matched == true) "학생증 사진과 일치해요. 이 사진이 학생증에 표시돼요." else "얼굴 비교 모델이 없어 학생증 사진과의 대조는 하지 않았어요.",
                        color = c.textSub, fontSize = 13.fsp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            actions = listOf(
                DialogAction("재촬영") { dialog = null; setStep(Step.SELFIE) },
                DialogAction("확인") {
                    val p = pending
                    dialog = null
                    FaceSession.clear()
                    if (p != null) {
                        state.register(p.first, p.second, png)
                        toast.show("학생증이 등록되었어요")
                        state.tab = Tab.CARD
                    }
                    close()
                },
            ),
            onCancel = { dialog = null; setStep(Step.SELFIE) },
        )
    }

    private companion object {
        const val HINT_ID = "중동중학교 학생증을 가이드라인 안에 대주세요"
        const val HINT_CARD_FACE = "학생증 앞면(얼굴 사진이 있는 면)을 가이드라인 안에 대주세요"
        const val HINT_SELFIE = "얼굴을 틀 안에 맞춰주세요"
    }
}

@androidx.compose.runtime.Composable
private fun InfoRow(label: String, value: String, divider: Boolean) {
    val c = LocalColors.current
    Column(Modifier.fillMaxWidth().padding(top = if (divider) 0.dp else 14.dp)) {
        if (divider) androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(1.dp).background(c.divider))
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = c.textSub, fontSize = 15.fsp)
            Text(value, color = c.text, fontSize = 15.fsp, fontWeight = FontWeight.Bold)
        }
    }
}
