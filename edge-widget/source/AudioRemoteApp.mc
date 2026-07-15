import Toybox.Application;
import Toybox.Lang;
import Toybox.WatchUi;
import Toybox.Communications;
import Toybox.System;

// Holds the latest "now playing" state pushed from the phone and the
// commands we can send back. Kept on the App so the View/Delegate can
// share it without extra plumbing.
class AudioRemoteApp extends Application.AppBase {

    // dictionary keys: "title", "position" (ms), "duration" (ms), "playing" (bool)
    var nowPlaying as Dictionary = {
        "title" => "Nespárované",
        "position" => 0,
        "duration" => 0,
        "playing" => false
    };

    // System.getTimer() value (ms) when nowPlaying was last received.
    // The view uses this to locally interpolate the progress bar between
    // updates instead of requiring a message every second.
    var lastUpdateTimer as Number = 0;

    function initialize() {
        AppBase.initialize();
    }

    function onStart(state as Dictionary?) as Void {
        Communications.registerForPhoneAppMessages(method(:onPhoneMessage));
        // Ask the phone for a fresh snapshot as soon as the widget opens.
        sendCommand("refresh");
    }

    function onStop(state as Dictionary?) as Void {
    }

    function getInitialView() as [Views] or [Views, InputDelegates] {
        var view = new AudioRemoteView();
        return [view, new AudioRemoteDelegate(view)];
    }

    function onPhoneMessage(msg as Communications.PhoneAppMessage) as Void {
        var data = msg.data;
        if (data instanceof Dictionary && data.hasKey("title")) {
            nowPlaying = data;
            lastUpdateTimer = System.getTimer();
        }
        WatchUi.requestUpdate();
    }

    // cmd: "play_pause", "seek_back", "seek_fwd", "refresh"
    function sendCommand(cmd as String) as Void {
        Communications.transmit(cmd, {}, new CommListener());
    }
}

class CommListener extends Communications.ConnectionListener {
    function initialize() {
        ConnectionListener.initialize();
    }
    function onComplete() as Void {
    }
    function onError() as Void {
    }
}

function getApp() as AudioRemoteApp {
    return Application.getApp() as AudioRemoteApp;
}
