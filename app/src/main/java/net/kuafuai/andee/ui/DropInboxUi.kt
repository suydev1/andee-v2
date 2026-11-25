package net.kuafuai.andee.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import net.kuafuai.andee.compat.WindowMetricsCompat
import net.kuafuai.andee.config.DropInbox
import net.kuafuai.andee.net.DropFence
import java.util.concurrent.Executors

class DropInboxUi(
    private val context: Context,
    private val inbox: DropInbox,
    private val onSubmit: (String) -> Unit,
    private val onDismiss: () -> Unit = {},
) {
    private val wm: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor { Thread(it, "drop-inbox-ui") }
    private var root: View? = null
    private var body: LinearLayout? = null
    private var frosted = false
    private var items: List<DropInbox.Item> = emptyList()
    private var expanded: String? = null

    fun show() {
        if (root != null) return
        val p = WindowManager.LayoutParams(
            cardWidth(), cardHeight(),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }
        frosted = Glass.frost(context, wm, p)
        val v = build()
        runCatching {
            v.hideFromAccessibility()
            wm.addView(v, p)
            root = v
            OwnCard.shown()
            Glass.enter(v)
            DropPickerActivity.onDone = ::refresh
        }
        refresh()
    }

    fun hide() {
        val v = root ?: return
        root = null
        DropPickerActivity.onDone = null
        io.shutdownNow()
        Glass.exit(v) {
            runCatching { wm.removeView(v) }
            OwnCard.hidden()
            onDismiss()
        }
    }

    private fun refresh() {
        if (root == null) return
        io.execute {
            val found = runCatching { inbox.scan() }.getOrDefault(emptyList())
            ui.post {
                if (root == null) return@post
                expanded = null
                body?.let { render(it, found) }
            }
        }
    }

    private fun cardWidth() = minOf(dp(500), (WindowMetricsCompat.bounds(wm).width() * 0.94f).toInt())
    private fun cardHeight() = minOf(dp(600), (WindowMetricsCompat.bounds(wm).height() * 0.86f).toInt())

    private fun build(): View {
        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = Glass.card(context, dp(28), frosted)
            clipToOutline = true
        }
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(22), dp(18), dp(14), dp(12))
        }
        val titles = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        titles.addView(TextView(context).apply {
            text = "Inbox"
            textSize = Glass.Type.HEADLINE
            setTextColor(Color.parseColor(Glass.TITLE))
        })
        titles.addView(TextView(context).apply {
            text = "Anything you put here, Andee only reads when you ask it to"
            textSize = Glass.Type.CAPTION
            setTextColor(Color.parseColor(Glass.SECONDARY))
        })
        header.addView(titles, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(TextView(context).apply {
            text = "✕"
            textSize = Glass.Type.CAPTION
            setTextColor(Color.parseColor(Glass.SECONDARY))
            gravity = Gravity.CENTER
            background = Glass.panel(context, dp(16))
            isClickable = true
            Glass.pressable(this)
            setOnClickListener { hide() }
        }, LinearLayout.LayoutParams(dp(32), dp(32)))
        card.addView(header, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        divider(card)

        val scroll = ScrollView(context).apply {
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        body = list
        scroll.addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        card.addView(scroll, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        divider(card)

        val footer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), dp(16))
        }
        footer.addView(TextView(context).apply {
            text = inbox.path()
            textSize = Glass.Type.MICRO
            setTextColor(Color.parseColor(Glass.MUTED))
            background = Glass.well(context, dp(10))
            setPadding(dp(10), dp(8), dp(10), dp(8))
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        footer.addView(button("Add from Files", filled = false) {
            context.startActivity(
                Intent(context, DropPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(10) })
        card.addView(footer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return card
    }

    private fun render(host: LinearLayout, found: List<DropInbox.Item>) {
        items = found
        host.removeAllViews()
        if (found.isEmpty()) {
            host.addView(TextView(context).apply {
                text = "No files yet. Put a file in the folder above, or add one from Files."
                textSize = Glass.Type.CAPTION
                setTextColor(Color.parseColor(Glass.MUTED))
                setPadding(0, dp(12), 0, dp(12))
            })
            return
        }
        for (item in found) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = Glass.panel(context, dp(14))
                isClickable = true
                Glass.pressable(this)
            }
            val line = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
            line.addView(TextView(context).apply {
                text = item.name
                textSize = Glass.Type.BODY
                setTextColor(Color.parseColor(Glass.TITLE))
                isSingleLine = true
                ellipsize = android.text.TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            line.addView(TextView(context).apply {
                text = if (item.readAt != null) "Read" else DropInbox.label(item.status)
                textSize = Glass.Type.CAPTION
                setTextColor(Color.parseColor(if (item.readAt != null) Glass.OK else Glass.ACCENT))
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            row.addView(line)
            row.addView(TextView(context).apply {
                text = "${DropInbox.formatSize(item.size)} · id ${item.id.take(8)}"
                textSize = Glass.Type.CAPTION
                setTextColor(Color.parseColor(Glass.SECONDARY))
                setPadding(0, dp(2), 0, 0)
            })

            if (expanded == item.id) {
                val detail = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(8), 0, 0)
                    visibility = View.GONE
                }
                detail.addView(button("Let Andee look", filled = true) {
                    // Both values carry the file's own words: the id is
                    // "<sha>:<name>". Fence them together, or a hostile name
                    // leaks through the id into the hub's copy of this turn.
                    // The latch in BodyCoreService is the half that holds if
                    // the model is talked out of the fence.
                    val data = "File name: ${item.name}\nFile id: ${item.id}"
                        .replace(DropFence.OPEN, "")
                        .replace(DropFence.CLOSE, "")
                    onSubmit("Look at this in the inbox: ${DropFence.wrap(data)}")
                    hide()
                })
                row.addView(detail)
                row.post { Glass.expand(detail) }
            }
            row.setOnClickListener {
                expanded = if (expanded == item.id) null else item.id
                render(host, items)
            }
            host.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = dp(8) })
        }
    }

    private fun button(label: String, filled: Boolean, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        textSize = Glass.Type.BODY
        setTextColor(Color.parseColor(if (filled) Glass.ON_TINT else Glass.LABEL))
        gravity = Gravity.CENTER
        background = if (filled) Glass.tinted(context, dp(20)) else Glass.panel(context, dp(20))
        setPadding(dp(18), dp(9), dp(18), dp(9))
        isClickable = true
        Glass.pressable(this)
        setOnClickListener { onClick() }
    }

    private fun divider(parent: LinearLayout) {
        parent.addView(View(context).apply { setBackgroundColor(Color.parseColor(Glass.PANEL_EDGE)) },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)))
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()
}
