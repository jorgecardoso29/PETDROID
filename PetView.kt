package com.pet.robo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Choreographer
import android.view.MotionEvent
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * O pet: rosto de robô (o mesmo do desenho_rosto.py) que passeia pela tela.
 * Toque nele = carinho; arraste = muda de lugar; sozinho ele anda, pisca, muda de humor e dorme.
 */
class PetView(context: Context, private val aoMover: (Int, Int) -> Unit) : View(context) {

    companion object {
        // layout lógico (o mesmo da versão web): sobra espaço à direita/acima para os Zzz
        const val LW = 220f
        const val LH = 180f
        const val CX = 75f
        const val CY = 110f
        const val BODY_W = 120f
        const val BODY_H = 90f

        const val DORMIR_APOS = 90f     // segundos sem interação até dormir
        const val SONO_DURACAO = 40f    // dorme esse tempo e acorda sozinho
        const val CARINHO = 1.6f
        const val TAMANHO = 0.45f       // dp por unidade lógica (muda o tamanho do pet)

        private const val W = 0
        private const val H = 1
        private const val DY = 2
        private const val INNER = 3
        private const val OUTER = 4
        private const val SMILE = 5
        private const val TAU = (2 * PI).toFloat()

        val FORMAS = mapOf(
            "observando" to floatArrayOf(22f, 30f, 0f, 0f, 0f, 0f),
            "feliz" to floatArrayOf(24f, 26f, 0f, 0f, 0f, .45f),
            "curioso" to floatArrayOf(22f, 32f, -2f, 0f, 0f, 0f),
            "bravo" to floatArrayOf(24f, 26f, 2f, .5f, .05f, 0f),
            "triste" to floatArrayOf(22f, 28f, 3f, .05f, .5f, 0f),
            "sono" to floatArrayOf(24f, 14f, 4f, .45f, .45f, 0f)
        )
        private val HUMORES = listOf("observando", "observando", "curioso", "feliz")
    }

    private val moldura = Color.parseColor("#63e56f")
    private val casca = Color.parseColor("#101512")
    private val tela = Color.parseColor("#171e19")
    private val corOlho = Color.parseColor("#3ee877")
    private val brilho = Color.parseColor("#c8ffd8")

    private val dens = resources.displayMetrics.density
    private val f = dens * TAMANHO
    val larguraPx = (LW * f).toInt()
    val alturaPx = (LH * f).toInt()

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // estado
    private val t0 = System.nanoTime()
    private fun agora() = (System.nanoTime() - t0) / 1e9f

    private var telaW = 1080
    private var telaH = 1920
    private var iniciado = false
    private var px = 0f                 // centro do rosto na tela (px)
    private var py = 0f
    private var destX = 0f
    private var destY = 0f
    private var temDestino = false
    private var espera = 0f
    private var andando = false

    private var humor = "observando"
    private var proxHumor = 10f
    private val forma = FORMAS.getValue("observando").copyOf()
    private var gx = 0f
    private var gy = 0f
    private var alvoGx = 0f
    private var alvoGy = 0f
    private var proxOlhar = 2f
    private var piscada = 1f
    private var iniPisc = -1f
    private var proxPisc = 2f
    private var carinhoAte = 0f
    private var ultimoToque = 0f
    private var dormindo = false
    private var inicioSono = 0f
    private var yoff = 0f
    private var rot = 0f
    private var ultimoQ = 0f

    // toque
    private var tocando = false
    private var arrastando = false
    private var toqueX = 0f
    private var toqueY = 0f

    private val quadro = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            passo(agora())
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ultimoQ = agora()
        ultimoToque = ultimoQ
        Choreographer.getInstance().postFrameCallback(quadro)
    }

    override fun onDetachedFromWindow() {
        Choreographer.getInstance().removeFrameCallback(quadro)
        super.onDetachedFromWindow()
    }

    // ---------- limites e posição ----------
    private fun minX() = 62f * f
    private fun maxX() = telaW - 62f * f
    private fun minY() = 96f * f + 24f * dens
    private fun maxY() = telaH - 56f * dens - 50f * f

    private fun limitar() {
        px = px.coerceIn(minX(), maxX())
        py = py.coerceIn(minY(), maxY())
    }

    private fun notificar() = aoMover((px - CX * f).roundToInt(), (py - CY * f).roundToInt())

    fun definirTela(w: Int, h: Int) {
        telaW = w
        telaH = h
        if (!iniciado) {
            px = w / 2f
            py = h * 0.6f
            iniciado = true
        }
        limitar()
        temDestino = false
        notificar()
    }

    // ---------- lógica ----------
    private fun passo(ag: Float) {
        val dt = min(.05f, ag - ultimoQ)
        ultimoQ = ag

        // sono
        if (!dormindo && ag - ultimoToque > DORMIR_APOS) {
            dormindo = true
            inicioSono = ag
        }
        if (dormindo && ag - inicioSono > SONO_DURACAO) {
            dormindo = false
            ultimoToque = ag
        }

        // humor muda de vez em quando
        if (!dormindo && ag > proxHumor) {
            humor = HUMORES.random()
            proxHumor = ag + 12f + Random.nextFloat() * 18f
        }
        val alvoHumor = when {
            ag < carinhoAte -> "feliz"
            dormindo -> "sono"
            else -> humor
        }
        val alvoForma = FORMAS.getValue(alvoHumor)
        for (i in forma.indices) forma[i] += (alvoForma[i] - forma[i]) * .18f

        // passeio
        var dirx = 0f
        var diry = 0f
        andando = false
        if (!dormindo && !arrastando && ag >= carinhoAte && ag >= espera) {
            if (!temDestino) {
                destX = minX() + Random.nextFloat() * (maxX() - minX())
                destY = minY() + Random.nextFloat() * (maxY() - minY())
                temDestino = true
            }
            val ddx = destX - px
            val ddy = destY - py
            val d = hypot(ddx, ddy)
            if (d < 3f) {
                temDestino = false
                espera = ag + 1.5f + Random.nextFloat() * 3.5f
            } else {
                val passoPx = min(d, 130f * f * dt)
                px += ddx / d * passoPx
                py += ddy / d * passoPx
                andando = true
                dirx = ddx / d
                diry = ddy / d
                notificar()
            }
        }
        val ty = when {
            andando -> -abs(sin(ag * 10f)) * 7f
            dormindo -> sin(ag * 2f) * 1.5f
            else -> 0f
        }
        val tr = if (andando) dirx * .12f + sin(ag * 10f) * .05f else 0f
        yoff += (ty - yoff) * .4f
        rot += (tr - rot) * .25f

        // olhar: para onde anda; parado, passeia o olhar por aí
        if (andando) {
            alvoGx = dirx
            alvoGy = diry
        } else if (dormindo) {
            alvoGx = 0f
            alvoGy = 0f
        } else if (ag > proxOlhar) {
            alvoGx = Random.nextFloat() * 2f - 1f
            alvoGy = (Random.nextFloat() * 2f - 1f) * .6f
            proxOlhar = ag + 1.5f + Random.nextFloat() * 2.5f
        }
        gx += (alvoGx - gx) * .2f
        gy += (alvoGy - gy) * .2f

        // piscada
        if (dormindo) {
            piscada = 1f
        } else if (iniPisc < 0f && ag >= proxPisc) {
            iniPisc = ag
        }
        if (iniPisc >= 0f) {
            val t = (ag - iniPisc) / .18f
            if (t >= 1f) {
                iniPisc = -1f
                piscada = 1f
                proxPisc = ag + 2f + Random.nextFloat() * 3f
            } else {
                piscada = abs(2f * t - 1f)
            }
        }
    }

    // ---------- toque ----------
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val dx = e.x - CX * f
                val dy = e.y - CY * f
                if (abs(dx) > 66f * f || abs(dy) > 52f * f) return false
                toqueX = e.rawX
                toqueY = e.rawY
                tocando = true
                arrastando = false
                dormindo = false
                ultimoToque = agora()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!tocando) return false
                if (!arrastando && hypot(e.rawX - toqueX, e.rawY - toqueY) > 12f * dens) arrastando = true
                if (arrastando) {
                    px = e.rawX
                    py = e.rawY
                    limitar()
                    temDestino = false
                    notificar()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!tocando) return false
                tocando = false
                val ag = agora()
                if (!arrastando && e.actionMasked == MotionEvent.ACTION_UP) carinhoAte = ag + CARINHO
                else espera = ag + 1.5f
                arrastando = false
                ultimoToque = ag
                return true
            }
        }
        return super.onTouchEvent(e)
    }

    // ---------- desenho ----------
    override fun onDraw(c: Canvas) {
        val ag = agora()
        c.save()
        c.translate(CX * f, CY * f + yoff * f)
        c.rotate(Math.toDegrees(rot.toDouble()).toFloat())
        c.scale(f, f)
        rosto(c, ag)
        if (dormindo) zzz(c, ag)
        c.restore()
    }

    private fun rosto(c: Canvas, ag: Float) {
        val corpo = RectF(-BODY_W / 2, -BODY_H / 2, BODY_W / 2, BODY_H / 2)
        p.style = Paint.Style.FILL
        p.color = casca
        c.drawRoundRect(corpo, 20f, 20f, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f
        p.color = moldura
        c.drawRoundRect(corpo, 20f, 20f, p)
        p.style = Paint.Style.FILL
        p.color = tela
        c.drawRoundRect(
            RectF(-BODY_W / 2 + 11, -BODY_H / 2 + 11, BODY_W / 2 - 11, BODY_H / 2 - 11), 12f, 12f, p
        )

        if (ag < carinhoAte) {
            // olhinhos fechados de felicidade: ^ ^
            p.style = Paint.Style.STROKE
            p.strokeWidth = 5f
            p.strokeCap = Paint.Cap.ROUND
            p.color = corOlho
            for (side in intArrayOf(-1, 1)) {
                val cx = side * 24f
                val cy = forma[DY]
                c.drawArc(RectF(cx - 15, cy - 8, cx + 15, cy + 14), 180f, 180f, false, p)
            }
            p.strokeCap = Paint.Cap.BUTT
            p.style = Paint.Style.FILL
        } else {
            olhos(c)
        }
    }

    private fun olhos(c: Canvas) {
        p.style = Paint.Style.FILL
        for (side in intArrayOf(-1, 1)) {
            val w = forma[W]
            var h = forma[H] * piscada
            if (humor == "curioso" && side == 1) h *= 1.15f
            h = max(h, 3f)
            val cx = side * 24f + gx * 4f
            val cy = forma[DY] + gy * 3f
            val top = cy - h / 2
            val r = min(w, h) * .38f

            val olho = Path()
            olho.addRoundRect(RectF(cx - w / 2, top, cx + w / 2, top + h), r, r, Path.Direction.CW)

            if (piscada > .5f) {
                val fora = cx + side * w / 2
                val dentro = cx - side * w / 2
                val palpebra = Path().apply {
                    moveTo(fora + side * 4, top - 40)
                    lineTo(dentro - side * 4, top - 40)
                    lineTo(dentro - side * 4, top + forma[INNER] * h)
                    lineTo(fora + side * 4, top + forma[OUTER] * h)
                    close()
                }
                olho.op(palpebra, Path.Op.DIFFERENCE)
                if (forma[SMILE] > .02f) {
                    val ch = h * forma[SMILE] * 1.6f
                    val y0 = top + h - ch * .45f
                    val corte = Path().apply {
                        addOval(RectF(cx - w * .75f, y0, cx + w * .75f, y0 + ch), Path.Direction.CW)
                    }
                    olho.op(corte, Path.Op.DIFFERENCE)
                }
            }

            p.color = corOlho
            c.drawPath(olho, p)

            if (piscada > .5f && h >= 8f) {            // olho fechado não tem brilho
                c.save()
                c.clipPath(olho)
                p.color = brilho
                val sx = cx - w * .18f + gx * 3f
                val sy = top + h * (.24f + max(forma[INNER], forma[OUTER]) * .6f) + gy * 2f
                c.drawCircle(sx, sy, 3.6f, p)
                c.restore()
            }
        }
    }

    private fun zzz(c: Canvas, ag: Float) {
        p.style = Paint.Style.FILL
        p.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        p.textAlign = Paint.Align.CENTER
        for (i in 0 until 3) {
            val t = ((ag / 3f) + i / 3f) % 1f
            val x = BODY_W * .32f + 34f * t + 5f * sin(t * TAU * 1.5f)
            val y = -BODY_H * .42f - 44f * t
            val alfa = (255 * min(1f, t * 5f) * (1f - t).pow(.6f)).toInt().coerceIn(0, 255)
            c.save()
            c.translate(x, y)
            c.rotate(-12f + 10f * sin(t * TAU))
            p.textSize = 8f + 9f * t
            p.color = corOlho
            p.alpha = alfa
            c.drawText("Z", 0f, p.textSize * .35f, p)
            c.restore()
        }
        p.alpha = 255
        p.textAlign = Paint.Align.LEFT
    }
}
