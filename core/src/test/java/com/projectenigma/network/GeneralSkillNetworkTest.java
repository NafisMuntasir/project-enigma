package com.projectenigma.network;

import com.projectenigma.model.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class GeneralSkillNetworkTest {
    @Test void generalSkillRequestsSnapshotsAndReconnectStateRoundTripOverSockets() throws Exception {
        Hero host = new Hero(HeroClass.WARRIOR), guest = new Hero(HeroClass.MAGE);
        guest.gainExperience(120);
        guest.progression().choose(guest, "PASSIVE:DEFENSE");
        guest.progression().choose(guest, "SKILL:ARC_DISCHARGE");
        PvPMatch match = new PvPMatch(host, guest, 900);
        var states = new ArrayBlockingQueue<PvPBattleState>(20);
        var connected = new ArrayBlockingQueue<Boolean>(1);
        try (PvPServer server = new PvPServer(0)) {
            server.setListener(new PvPServer.EventListener() {
                public void onGuestConnected(boolean reconnect) { }
                public void onGuestDisconnected() { }
                public void onClassSelected(HeroClass type) { }
                public void onActionReceived(BattleAction action) { server.broadcast(match.applyAction(1, action)); }
                public void onSkillReceived(Skill skill) { server.broadcast(match.applySkill(1, skill)); }
                public void onAbandon() { }
            });
            PvPClient client = new PvPClient();
            client.setListener(new PvPClient.EventListener() {
                public void onConnected(boolean reconnect) { connected.add(true); }
                public void onDisconnected() { }
                public void onStateReceived(PvPBattleState state) { states.add(state); }
            });
            try {
                client.connect("127.0.0.1", server.port()); assertNotNull(connected.poll(5, TimeUnit.SECONDS));
                match.applyAction(0, BattleAction.GUARD);
                int energy = guest.mana, hp = host.health;
                client.sendSkill(Skill.ARC_DISCHARGE);
                PvPBattleState state = states.poll(5, TimeUnit.SECONDS); assertNotNull(state);
                assertEquals(energy - 5, state.player1().mana()); assertTrue(state.player0().health() < hp);
                assertTrue(state.player0().skills().status().contains("Interrupted"));
                assertEquals(4, state.player1().skills().cooldowns().get(Skill.ARC_DISCHARGE.name()));
                assertTrue(state.player1().displayHero().progression().equipped(Skill.ARC_DISCHARGE));
                client.sendSkill(Skill.ARC_DISCHARGE); // Duplicate guest click: host turn, no extra spend.
                PvPBattleState duplicate = states.poll(5, TimeUnit.SECONDS); assertNotNull(duplicate);
                assertEquals(state.player1().mana(), duplicate.player1().mana());
                assertEquals(0, duplicate.currentTurn());
                match.pauseForDisconnect();
                assertEquals(state.player1().skills(), match.resume().player1().skills());
                int guestHp = guest.health; match.applyAction(0, BattleAction.ATTACK); assertEquals(guestHp, guest.health);
                assertEquals(1, match.currentState().currentTurn());
            } finally { client.close(); }
        }
    }
    @Test void progressedLoadoutRetainsPassivesAndSkillsWithoutApplyingStatsTwice() {
        Hero h = new Hero(HeroClass.WARRIOR); h.gainExperience(45);
        h.progression().choose(h, "PASSIVE:VITALITY");
        assertEquals(146, h.maxHealth); assertEquals(146, HeroLoadout.of(h).toHero().maxHealth);
        h.gainExperience(75); h.progression().choose(h, "SKILL:ARC_DISCHARGE");
        Hero transferred = HeroLoadout.of(h).toHero();
        assertEquals(h.attack(), transferred.attack()); assertEquals(h.maxHealth, transferred.maxHealth);
        assertEquals(1, transferred.progression().rank(PassiveUpgrade.VITALITY));
        assertTrue(transferred.progression().equipped(Skill.ARC_DISCHARGE));
    }
    @Test void transferredProgressionRejectsUnknownDuplicateAndOverBudgetUnlocks() {
        var input = new ProgressionSnapshot(List.of("BAD", "ARC_DISCHARGE", "ARC_DISCHARGE", "PHOTON_LANCE"),
                List.of("ARC_DISCHARGE", "ARC_DISCHARGE", "BAD"), List.of(999));
        Progression p = input.restore(2);
        assertEquals(1, p.rank(PassiveUpgrade.DAMAGE)); assertTrue(p.unlocked.isEmpty());
        assertTrue(p.equipped.stream().allMatch(String::isEmpty));
    }
}
