package pauseMod;

import arc.*;
import arc.util.*;
import mindustry.core.*;
import mindustry.game.EventType.*;
import mindustry.gen.*;
import mindustry.input.*;
import mindustry.mod.*;
import mindustry.ui.*;

import static arc.Core.*;
import static mindustry.Vars.*;

public class PauseMod extends Mod {
    private long lastSyncTime;

    public PauseMod() {
        addSettings();
        setupEvents();
        setupPackets();
    }

    void addSettings() {
        ui.settings.addCategory("Multiplayer Pause", Icon.pause, s -> {
            s.checkPref("multiplayerpause-toasts", true);
            s.checkPref("multiplayerpause-allowany", false);
            s.checkPref("multiplayerpause-synconpause", false);
            s.checkPref("multiplayerpause-synconunpause", false); // Is enabling this by default a good idea? I have no clue how much desync this mod is going to cause...
            s.checkPref("multiplayerpause-schedulesync", false);
        });
    }

    void setupEvents() {
        Events.run(Trigger.update, () -> {
            if (input.keyTap(Binding.pause) && !renderer.isCutscene() && !scene.hasDialog() && !scene.hasKeyboard() && !ui.restart.isShown() && state.isGame() && net.active()) {
                if (net.client()) Call.serverPacketReliable("multiplayerpause-request", ""); // Send pause request
                else showToast(player, !state.isPaused()); // Show toast for host pausing (inverted as the state hasn't been updated yet)
            }
        });
    }

    void setupPackets() {
        netServer.addPacketHandler("multiplayerpause-request", (p, data) -> {
            if (!(p.admin || Core.settings.getBool("multiplayerpause-allowany")) || state.isMenu()) return;

            state.set(state.isPaused() ? GameState.State.playing : GameState.State.paused);
            showToast(p, state.isPaused());
        });
        // State changes are forwarded to clients for more responsive pausing (avoids waiting for next stateSnapshot) which should reduce desync (I hope) and allows for toasts
        netClient.addPacketHandler("multiplayerpause-updatestate", data -> {
            String[] d = data.split(" ");
            if (d.length != 2) return;
            boolean paused = d[1].equals("t");
            state.set(paused ? GameState.State.paused : GameState.State.playing); // Reflect state change on the client ASAP
            showToast(Groups.player.getByID(Strings.parseInt(d[0])), paused);
            if (Core.settings.getBool("multiplayerpause-syncon" + (paused ? "pause" : "unpause"))) {
                long since = Time.millis() - lastSyncTime;
                if (since > 5100) { // Sync now
                    Call.sendChatMessage("/sync");
                    lastSyncTime = Time.millis();
                } else if (Core.settings.getBool("multiplayerpause-schedulesync") && since > 0) { // Schedule a sync as one has taken place recently
                    Timer.schedule(() -> Call.sendChatMessage("/sync"), (5100 - since) / 1000f);
                    lastSyncTime = Time.millis() + 5100 - since;
                }

            }
        });
    }

    void showToast(Player p, boolean paused) {
        if (net.server()) Call.clientPacketReliable("multiplayerpause-updatestate", p.id + " " + (paused ? "t" : "f")); // Forward state change to clients
        if (!Core.settings.getBool("multiplayerpause-toasts")) return;

        Menus.infoToast(Strings.format("@ @ the game.", p == null ? "[lightgray]Unknown player[]" : Strings.stripColors(p.name), paused ? "paused" : "unpaused"), 2f);
    }
}
