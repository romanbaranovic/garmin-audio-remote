import Toybox.WatchUi;
import Toybox.Graphics;
import Toybox.Lang;
import Toybox.System;
import Toybox.Timer;

class AudioRemoteView extends WatchUi.View {

    // Button hit-boxes, computed in onUpdate() so they always match what's drawn.
    var seekBackBtn as Array = [0, 0, 0, 0];   // [x, y, w, h]
    var seekFwdBtn as Array = [0, 0, 0, 0];
    var nextQueueBtn as Array = [0, 0, 0, 0];
    var playBtn as Array = [0, 0, 0, 0];

    var tickTimer as Timer.Timer?;

    function initialize() {
        View.initialize();
    }

    function onShow() as Void {
        tickTimer = new Timer.Timer();
        tickTimer.start(method(:onTick), 1000, true);
    }

    function onHide() as Void {
        if (tickTimer != null) {
            tickTimer.stop();
            tickTimer = null;
        }
    }

    function onTick() as Void {
        // Only need to redraw ourselves; position is interpolated in onUpdate.
        if (getApp().nowPlaying.get("playing")) {
            WatchUi.requestUpdate();
        }
    }

    function onUpdate(dc as Graphics.Dc) as Void {
        var app = getApp();
        var np = app.nowPlaying;
        var w = dc.getWidth();
        var h = dc.getHeight();

        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_BLACK);
        dc.clear();

        // --- title ---
        var title = np.get("title") as String;
        title = truncateText(dc, title, Graphics.FONT_SMALL, w - 16);
        dc.drawText(w / 2, 20, Graphics.FONT_SMALL, title, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        // --- interpolate position while playing ---
        var duration = np.get("duration") as Number;
        var position = np.get("position") as Number;
        var playing = np.get("playing") as Boolean;
        if (playing) {
            var elapsed = System.getTimer() - app.lastUpdateTimer;
            position += elapsed;
            if (duration > 0 && position > duration) {
                position = duration;
            }
        }

        // --- progress bar ---
        var barX = 16;
        var barY = h / 2 - 10;
        var barW = w - 32;
        var barH = 8;
        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(barX, barY, barW, barH, 4);
        if (duration > 0) {
            var fillW = (barW * position / duration).toNumber();
            if (fillW > 0) {
                dc.setColor(Graphics.COLOR_BLUE, Graphics.COLOR_TRANSPARENT);
                dc.fillRoundedRectangle(barX, barY, fillW, barH, 4);
            }
        }

        // --- time text ---
        var timeFont = Graphics.FONT_MEDIUM;
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        var timeStr = formatTime(position) + " / " + formatTime(duration);
        var timeY = barY + 12 + dc.getFontHeight(timeFont) / 2;
        dc.drawText(w / 2, timeY, timeFont, timeStr, Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER);

        // --- buttons: row of 3 (-10s / next-in-queue / +10s), big play/pause below ---
        // Positioned relative to timeY (not the screen bottom) so there's
        // always clearance below the time text regardless of screen size.
        var gap = 4;
        var row1Y = timeY + dc.getFontHeight(timeFont) / 2 + 12;
        var row1H = 46;
        var smallBtnW = (w - 2 * gap) / 3;
        seekBackBtn = [0, row1Y, smallBtnW, row1H];
        nextQueueBtn = [smallBtnW + gap, row1Y, smallBtnW, row1H];
        seekFwdBtn = [2 * (smallBtnW + gap), row1Y, smallBtnW, row1H];

        var row2Y = row1Y + row1H + gap;
        var row2H = 62;
        playBtn = [16, row2Y, w - 32, row2H];

        drawButton(dc, seekBackBtn, "-10s");
        drawButton(dc, nextQueueBtn, ">>|");
        drawButton(dc, seekFwdBtn, "+10s");
        drawButton(dc, playBtn, playing ? "||" : ">");
    }

    // Trims text with a trailing "..." so it fits within maxWidth pixels,
    // using getTextWidthInPixels since character count alone is not a
    // reliable proxy for rendered width in a proportional font.
    function truncateText(dc as Graphics.Dc, text as String, font, maxWidth as Number) as String {
        if (dc.getTextWidthInPixels(text, font) <= maxWidth) {
            return text;
        }
        var ellipsis = "...";
        var lo = 0;
        var hi = text.length();
        while (lo < hi) {
            var mid = (lo + hi + 1) / 2;
            var candidate = text.substring(0, mid) + ellipsis;
            if (dc.getTextWidthInPixels(candidate, font) <= maxWidth) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return text.substring(0, lo) + ellipsis;
    }

    function drawButton(dc as Graphics.Dc, rect as Array, label as String) as Void {
        dc.setColor(Graphics.COLOR_DK_GRAY, Graphics.COLOR_TRANSPARENT);
        dc.fillRoundedRectangle(rect[0], rect[1], rect[2], rect[3], 8);
        dc.setColor(Graphics.COLOR_WHITE, Graphics.COLOR_TRANSPARENT);
        dc.drawText(
            rect[0] + rect[2] / 2,
            rect[1] + rect[3] / 2,
            Graphics.FONT_MEDIUM,
            label,
            Graphics.TEXT_JUSTIFY_CENTER | Graphics.TEXT_JUSTIFY_VCENTER
        );
    }

    function formatTime(ms as Number) as String {
        if (ms < 0) {
            ms = 0;
        }
        var totalSec = ms / 1000;
        var h = totalSec / 3600;
        var m = (totalSec % 3600) / 60;
        var s = totalSec % 60;
        if (h > 0) {
            return h.format("%d") + ":" + m.format("%02d") + ":" + s.format("%02d");
        }
        return m.format("%d") + ":" + s.format("%02d");
    }

    function hitTest(rect as Array, x as Number, y as Number) as Boolean {
        return x >= rect[0] && x <= rect[0] + rect[2] && y >= rect[1] && y <= rect[1] + rect[3];
    }
}
