import Toybox.WatchUi;
import Toybox.Lang;

class AudioRemoteDelegate extends WatchUi.BehaviorDelegate {

    var view as AudioRemoteView;

    function initialize(view as AudioRemoteView) {
        BehaviorDelegate.initialize();
        self.view = view;
    }

    function onTap(clickEvent as WatchUi.ClickEvent) as Boolean {
        var coords = clickEvent.getCoordinates();
        var x = coords[0];
        var y = coords[1];

        if (view.hitTest(view.seekBackBtn, x, y)) {
            getApp().sendCommand("seek_back");
            return true;
        }
        if (view.hitTest(view.nextQueueBtn, x, y)) {
            getApp().sendCommand("next_queue");
            return true;
        }
        if (view.hitTest(view.seekFwdBtn, x, y)) {
            getApp().sendCommand("seek_fwd");
            return true;
        }
        if (view.hitTest(view.playBtn, x, y)) {
            getApp().sendCommand("play_pause");
            return true;
        }
        return false;
    }

    function onBack() as Boolean {
        // Let the system pop the widget off the loop as usual.
        return false;
    }
}
