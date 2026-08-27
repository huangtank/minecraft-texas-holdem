package com.tank1114.holdem.game;

import com.tank1114.holdem.config.HoldemConfig;
import com.tank1114.holdem.display.TableDisplayManager;
import com.tank1114.holdem.engine.Card;
import com.tank1114.holdem.engine.Deck;
import com.tank1114.holdem.engine.HandEvaluator;
import com.tank1114.holdem.engine.HandValue;
import com.tank1114.holdem.storage.ChipStorage;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The full state machine for the one fixed table this plugin runs: seats, blinds,
 * betting rounds, side pots, showdown and hand-to-hand progression. There is no
 * human dealer - every transition here is triggered automatically once the
 * players still in the hand have matching bets (or are all-in).
 */
public final class PokerTable {

    private static final long RUNOUT_DELAY_TICKS = 30L; // 1.5s pause between auto-dealt streets when everyone's all-in
    private static final long SHOWDOWN_DELAY_TICKS = 160L; // 8s to let people read the result before the table clears
    private static final long FOLD_WIN_DELAY_TICKS = 80L; // 4s
    private static final long NEXT_HAND_DELAY_TICKS = 60L; // 3s breather before the next hand deals in

    private final Plugin plugin;
    private final TableDisplayManager display;
    private final ChipStorage chipStorage;
    private final HoldemConfig config;

    private final Seat[] seats;
    private final Set<Integer> pendingLeave = new HashSet<>();

    private GameStage stage = GameStage.WAITING;
    private Deck deck;
    private final List<Card> community = new ArrayList<>();

    private int dealerButton = -1;
    private int actingSeatIndex = -1;
    private long currentBet;
    private long minRaiseIncrement;

    private BukkitTask turnTimeoutTask;

    public PokerTable(Plugin plugin, TableDisplayManager display, ChipStorage chipStorage, HoldemConfig config) {
        this.plugin = plugin;
        this.display = display;
        this.chipStorage = chipStorage;
        this.config = config;
        this.seats = new Seat[config.seatCount()];
        for (int i = 0; i < seats.length; i++) {
            seats[i] = new Seat(i);
        }
    }

    public Seat seat(int index) {
        return seats[index];
    }

    public int seatCount() {
        return seats.length;
    }

    public GameStage stage() {
        return stage;
    }

    public List<Card> community() {
        return community;
    }

    public long potTotal() {
        long total = 0;
        for (Seat s : seats) {
            total += s.committedThisHand();
        }
        return total;
    }

    public int dealerButton() {
        return dealerButton;
    }

    public int actingSeatIndex() {
        return actingSeatIndex;
    }

    // ---------------------------------------------------------------- seating

    public String sitDown(Player player, int seatIndex) {
        if (seatIndex < 0 || seatIndex >= seats.length) {
            return "沒有這個座位編號。";
        }
        if (seatIndexOf(player.getUniqueId()) >= 0) {
            return "你已經坐在其他座位了，請先離座。";
        }
        Seat seat = seats[seatIndex];
        if (seat.isOccupied()) {
            return "這個座位已經有人坐了。";
        }

        long balance = chipStorage.getOrInit(player.getUniqueId(), config.startingStack());
        if (balance <= 0) {
            if (config.rebuyMode() == RebuyMode.AUTO) {
                balance = config.startingStack();
                chipStorage.set(player.getUniqueId(), balance);
            } else {
                return "你的籌碼是 0，請聯絡管理員補充後再入座。";
            }
        }

        seat.seat(player.getUniqueId(), player.getName(), balance);
        display.reapplyVisibility(player, seatIndex);
        broadcast(player.getName() + " 坐上了第 " + (seatIndex + 1) + " 號座位（籌碼：" + balance + "）");

        if (stage == GameStage.WAITING && countOccupied() >= 2) {
            Bukkit.getScheduler().runTaskLater(plugin, this::startHand, NEXT_HAND_DELAY_TICKS);
        }
        return null;
    }

    public String requestLeave(UUID uuid) {
        int seatIndex = seatIndexOf(uuid);
        if (seatIndex < 0) {
            return "你沒有坐在任何座位。";
        }
        if (stage != GameStage.WAITING) {
            forceFold(seatIndex, "離座");
            pendingLeave.add(seatIndex);
            broadcast(seats[seatIndex].occupantName() + " 將在這局結束後離座。");
        } else {
            vacateNow(seatIndex);
        }
        return null;
    }

    private void vacateNow(int seatIndex) {
        Seat seat = seats[seatIndex];
        if (!seat.isOccupied()) {
            return;
        }
        chipStorage.set(seat.occupant(), seat.stack());
        display.clearHoleCards(seatIndex);
        String name = seat.occupantName();
        seat.vacate();
        broadcast(name + " 離開了座位。");
    }

    public int seatIndexOf(UUID uuid) {
        for (Seat seat : seats) {
            if (seat.isOccupied() && seat.occupant().equals(uuid)) {
                return seat.index();
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- hand lifecycle

    public void startHand() {
        applyBustRules();
        if (countOccupied() < 2) {
            stage = GameStage.WAITING;
            return;
        }

        deck = new Deck(ThreadLocalRandom.current());
        community.clear();
        display.clearCommunityCards();
        for (Seat s : seats) {
            if (s.isOccupied()) {
                s.resetForNewHand();
            }
        }

        dealerButton = dealerButton < 0 ? firstOccupiedSeat() : nextOccupiedSeat(dealerButton);
        int occCount = countOccupied();

        int sbIndex;
        int bbIndex;
        if (occCount == 2) {
            sbIndex = dealerButton;
            bbIndex = nextOccupiedSeat(dealerButton);
        } else {
            sbIndex = nextOccupiedSeat(dealerButton);
            bbIndex = nextOccupiedSeat(sbIndex);
        }
        seats[sbIndex].commit(Math.min(config.smallBlind(), seats[sbIndex].stack()));
        seats[bbIndex].commit(Math.min(config.bigBlind(), seats[bbIndex].stack()));
        currentBet = config.bigBlind();
        minRaiseIncrement = config.bigBlind();

        int start = nextOccupiedSeat(dealerButton);
        for (int pass = 0; pass < 2; pass++) {
            int idx = start;
            for (int c = 0; c < occCount; c++) {
                seats[idx].dealHoleCard(deck.draw());
                idx = nextOccupiedSeat(idx);
            }
        }
        for (Seat s : seats) {
            if (s.isOccupied()) {
                Player p = Bukkit.getPlayer(s.occupant());
                if (p != null) {
                    display.setHoleCards(s.index(), s.holeCards(), p);
                }
            }
        }

        stage = GameStage.PRE_FLOP;
        broadcast("新的一局開始！莊家鈕：第 " + (dealerButton + 1) + " 號座位，小盲：第 " + (sbIndex + 1)
                + " 號（" + config.smallBlind() + "），大盲：第 " + (bbIndex + 1) + " 號（" + config.bigBlind() + "）。");

        actingSeatIndex = nextActiveSeat(bbIndex);
        beginBettingOrRunout();
    }

    private void applyBustRules() {
        for (Seat seat : seats) {
            if (!seat.isOccupied() || seat.stack() > 0) {
                continue;
            }
            String name = seat.occupantName();
            switch (config.rebuyMode()) {
                case AUTO -> {
                    seat.setStack(config.startingStack());
                    chipStorage.set(seat.occupant(), config.startingStack());
                    broadcast(name + " 的籌碼已自動補回 " + config.startingStack() + "。");
                }
                case DISABLED, ADMIN_ONLY -> {
                    chipStorage.set(seat.occupant(), 0);
                    display.clearHoleCards(seat.index());
                    seat.vacate();
                    broadcast(name + " 籌碼歸零，已離座。");
                }
            }
        }
    }

    // ---------------------------------------------------------------- betting

    public String performAction(UUID uuid, PlayerAction action, long amount) {
        if (stage == GameStage.WAITING || stage == GameStage.SHOWDOWN) {
            return "現在沒有進行中的回合可以行動。";
        }
        int seatIndex = seatIndexOf(uuid);
        if (seatIndex < 0) {
            return "你沒有坐在牌桌上。";
        }
        if (actingSeatIndex != seatIndex) {
            return "還沒輪到你行動。";
        }
        Seat seat = seats[seatIndex];

        switch (action) {
            case FOLD -> {
                seat.fold();
                broadcast(seat.occupantName() + " 棄牌。");
            }
            case CHECK -> {
                if (seat.committedThisRound() != currentBet) {
                    return "目前有人下注，不能看牌，請跟注、加注或棄牌。";
                }
                seat.markActed();
                broadcast(seat.occupantName() + " 看牌。");
            }
            case CALL -> {
                if (currentBet == seat.committedThisRound()) {
                    return "目前沒有需要跟注的金額，請用看牌（check）。";
                }
                long need = currentBet - seat.committedThisRound();
                long actual = seat.commit(need);
                seat.markActed();
                broadcast(seat.occupantName() + (actual < need ? " 全下跟注 " : " 跟注 ") + actual + "。");
            }
            case BET -> {
                if (currentBet != 0) {
                    return "已經有人下注了，請用加注（raise）。";
                }
                if (amount <= 0) {
                    return "下注金額必須大於 0。";
                }
                long committed = Math.min(amount, seat.stack());
                if (committed < seat.stack() && committed < config.bigBlind()) {
                    return "最小下注金額是 " + config.bigBlind() + "。";
                }
                seat.commit(committed);
                currentBet = committed;
                minRaiseIncrement = Math.max(committed, config.bigBlind());
                resetOthersActedFlag(seatIndex);
                seat.markActed();
                broadcast(seat.occupantName() + " 下注 " + committed + "。");
            }
            case RAISE -> {
                if (currentBet == 0) {
                    return "目前沒有人下注，請用下注（bet）。";
                }
                if (amount <= currentBet) {
                    return "加注後的總下注必須高於目前的 " + currentBet + "。";
                }
                long stackCap = seat.committedThisRound() + seat.stack();
                long target = Math.min(amount, stackCap);
                long raiseSize = target - currentBet;
                if (target < stackCap && raiseSize < minRaiseIncrement) {
                    return "最小加注幅度是 " + minRaiseIncrement + "（總下注至少要到 " + (currentBet + minRaiseIncrement) + "）。";
                }
                seat.commit(target - seat.committedThisRound());
                currentBet = target;
                minRaiseIncrement = Math.max(raiseSize, minRaiseIncrement);
                resetOthersActedFlag(seatIndex);
                seat.markActed();
                broadcast(seat.occupantName() + " 加注到 " + target + "。");
            }
            case ALL_IN -> {
                if (seat.stack() <= 0) {
                    return "你已經沒有籌碼了。";
                }
                long total = seat.committedThisRound() + seat.stack();
                seat.commit(seat.stack());
                if (total > currentBet) {
                    long raiseSize = total - currentBet;
                    currentBet = total;
                    minRaiseIncrement = Math.max(raiseSize, minRaiseIncrement);
                    resetOthersActedFlag(seatIndex);
                }
                seat.markActed();
                broadcast(seat.occupantName() + " 全下 " + total + "！");
            }
        }

        return resolveAfterAction(seatIndex);
    }

    /** Shared tail for both a normal on-turn action and an on-turn forced fold (timeout/quit). */
    private String resolveAfterAction(int actedSeatIndex) {
        Integer sole = soleRemainingSeatIndex();
        if (sole != null) {
            endHandByFold(sole);
            return null;
        }
        cancelTurnTimer();
        if (bettingRoundComplete()) {
            advanceStage();
        } else {
            actingSeatIndex = nextActiveSeat(actedSeatIndex);
            if (actingSeatIndex < 0) {
                advanceStage();
            } else {
                promptTurn(actingSeatIndex);
            }
        }
        return null;
    }

    private void resetOthersActedFlag(int exceptSeatIndex) {
        for (Seat seat : seats) {
            if (seat.index() != exceptSeatIndex && seat.isActive()) {
                seat.clearActedFlag();
            }
        }
    }

    private boolean bettingRoundComplete() {
        if (countActive() <= 1) {
            return true;
        }
        for (Seat seat : seats) {
            if (seat.isActive() && (!seat.isActedThisRound() || seat.committedThisRound() != currentBet)) {
                return false;
            }
        }
        return true;
    }

    private void advanceStage() {
        cancelTurnTimer();
        for (Seat seat : seats) {
            seat.startNewBettingRound();
        }
        currentBet = 0;
        minRaiseIncrement = config.bigBlind();

        switch (stage) {
            case PRE_FLOP -> {
                dealCommunity(3);
                stage = GameStage.FLOP;
            }
            case FLOP -> {
                dealCommunity(1);
                stage = GameStage.TURN;
            }
            case TURN -> {
                dealCommunity(1);
                stage = GameStage.RIVER;
            }
            case RIVER -> {
                stage = GameStage.SHOWDOWN;
                runShowdown();
                return;
            }
            default -> {
                return;
            }
        }

        actingSeatIndex = nextActiveSeat(dealerButton);
        beginBettingOrRunout();
    }

    private void beginBettingOrRunout() {
        if (countActive() <= 1 || actingSeatIndex < 0) {
            Bukkit.getScheduler().runTaskLater(plugin, this::advanceStage, RUNOUT_DELAY_TICKS);
        } else {
            promptTurn(actingSeatIndex);
        }
    }

    private void dealCommunity(int count) {
        int startIndex = community.size();
        for (int i = 0; i < count; i++) {
            Card card = deck.draw();
            community.add(card);
            display.setCommunityCard(startIndex + i, card);
        }
        broadcast(stageLabel(stage) + "：" + community.subList(startIndex, community.size()));
    }

    private String stageLabel(GameStage s) {
        return switch (s) {
            case PRE_FLOP -> "翻牌前";
            case FLOP -> "翻牌";
            case TURN -> "轉牌";
            case RIVER -> "河牌";
            case SHOWDOWN -> "攤牌";
            case WAITING -> "等待中";
        };
    }

    private void promptTurn(int seatIndex) {
        Seat seat = seats[seatIndex];
        Player player = Bukkit.getPlayer(seat.occupant());
        long toCall = currentBet - seat.committedThisRound();
        if (player != null) {
            String hint = toCall > 0
                    ? "需要跟注 " + toCall + "。可用：/holdem fold, call, raise <總下注>, allin"
                    : "可以看牌或下注。可用：/holdem fold, check, bet <金額>, allin";
            player.sendMessage(Component.text("輪到你行動了！底池：" + potTotal() + "。" + hint));
        }
        broadcast("輪到 " + seat.occupantName() + " 行動。");

        turnTimeoutTask = Bukkit.getScheduler().runTaskLater(plugin,
                () -> onTurnTimeout(seatIndex), config.turnTimeoutSeconds() * 20L);
    }

    private void onTurnTimeout(int seatIndex) {
        if (actingSeatIndex != seatIndex || stage == GameStage.WAITING || stage == GameStage.SHOWDOWN) {
            return;
        }
        broadcast(seats[seatIndex].occupantName() + " 超過時間未行動，自動棄牌。");
        forceFold(seatIndex, null);
    }

    private void cancelTurnTimer() {
        if (turnTimeoutTask != null) {
            turnTimeoutTask.cancel();
            turnTimeoutTask = null;
        }
    }

    /** Folds a seat outside the normal on-turn flow: disconnect, voluntary stand-up mid-hand, or timeout. */
    public void forceFold(int seatIndex, String reason) {
        Seat seat = seats[seatIndex];
        if (!seat.isOccupied() || seat.isFolded() || stage == GameStage.WAITING || stage == GameStage.SHOWDOWN) {
            return;
        }
        boolean wasActing = actingSeatIndex == seatIndex;
        seat.fold();
        if (reason != null) {
            broadcast(seat.occupantName() + " 棄牌（" + reason + "）。");
        }
        Integer sole = soleRemainingSeatIndex();
        if (sole != null) {
            endHandByFold(sole);
            return;
        }
        if (wasActing) {
            cancelTurnTimer();
            if (bettingRoundComplete()) {
                advanceStage();
            } else {
                actingSeatIndex = nextActiveSeat(seatIndex);
                if (actingSeatIndex < 0) {
                    advanceStage();
                } else {
                    promptTurn(actingSeatIndex);
                }
            }
        }
    }

    private Integer soleRemainingSeatIndex() {
        int remaining = -1;
        int count = 0;
        for (Seat seat : seats) {
            if (seat.isOccupied() && !seat.isFolded()) {
                count++;
                remaining = seat.index();
            }
        }
        return count == 1 ? remaining : null;
    }

    private void endHandByFold(int winnerIndex) {
        cancelTurnTimer();
        long total = potTotal();
        Seat winner = seats[winnerIndex];
        winner.setStack(winner.stack() + total);
        broadcast(winner.occupantName() + " 靠棄牌獲勝，贏得 " + total + " 籌碼！");
        stage = GameStage.SHOWDOWN;
        actingSeatIndex = -1;
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanupAndMaybeStartNext, FOLD_WIN_DELAY_TICKS);
    }

    private void runShowdown() {
        List<Pot> pots = PotCalculator.compute(List.of(seats));
        for (Seat seat : seats) {
            if (seat.isOccupied() && !seat.isFolded()) {
                display.revealHoleCards(seat.index());
            }
        }

        StringBuilder summary = new StringBuilder("攤牌結果：");
        for (Pot pot : pots) {
            Map<Integer, HandValue> values = new HashMap<>();
            for (int idx : pot.eligibleSeatIndices()) {
                List<Card> seven = new ArrayList<>(seats[idx].holeCards());
                seven.addAll(community);
                values.put(idx, HandEvaluator.evaluateBest(seven));
            }
            if (values.isEmpty()) {
                continue;
            }
            HandValue best = values.values().stream().max(Comparator.naturalOrder()).orElseThrow();
            List<Integer> winners = values.entrySet().stream()
                    .filter(e -> e.getValue().compareTo(best) == 0)
                    .map(Map.Entry::getKey)
                    .sorted(Comparator.comparingInt(this::distanceFromDealer))
                    .toList();

            long share = pot.amount() / winners.size();
            long remainder = pot.amount() % winners.size();
            for (int i = 0; i < winners.size(); i++) {
                Seat winnerSeat = seats[winners.get(i)];
                long amount = share + (i == 0 ? remainder : 0);
                winnerSeat.setStack(winnerSeat.stack() + amount);
            }

            summary.append("\n底池 ").append(pot.amount()).append(" 由 ");
            for (int i = 0; i < winners.size(); i++) {
                if (i > 0) {
                    summary.append("、");
                }
                summary.append(seats[winners.get(i)].occupantName());
            }
            summary.append(" 贏得（").append(best.category().label()).append("）");
        }
        broadcast(summary.toString());

        actingSeatIndex = -1;
        Bukkit.getScheduler().runTaskLater(plugin, this::cleanupAndMaybeStartNext, SHOWDOWN_DELAY_TICKS);
    }

    private int distanceFromDealer(int seatIndex) {
        int from = nextOccupiedSeat(dealerButton);
        return Math.floorMod(seatIndex - from, seats.length);
    }

    private void cleanupAndMaybeStartNext() {
        for (int idx : pendingLeave) {
            vacateNow(idx);
        }
        pendingLeave.clear();

        for (Seat seat : seats) {
            display.clearHoleCards(seat.index());
        }
        community.clear();
        display.clearCommunityCards();

        for (Seat seat : seats) {
            if (seat.isOccupied()) {
                chipStorage.set(seat.occupant(), seat.stack());
            }
        }

        stage = GameStage.WAITING;
        actingSeatIndex = -1;

        if (countOccupied() >= 2) {
            Bukkit.getScheduler().runTaskLater(plugin, this::startHand, NEXT_HAND_DELAY_TICKS);
        }
    }

    /** Admin rescue command: void the current hand, refund whatever was committed, and reset to waiting. */
    public void forceReset() {
        cancelTurnTimer();
        for (Seat seat : seats) {
            if (seat.isOccupied()) {
                seat.setStack(seat.stack() + seat.committedThisHand());
                chipStorage.set(seat.occupant(), seat.stack());
            }
            display.clearHoleCards(seat.index());
            seat.resetForNewHand();
        }
        community.clear();
        display.clearCommunityCards();
        pendingLeave.clear();
        stage = GameStage.WAITING;
        actingSeatIndex = -1;
        broadcast("管理員已強制重置牌桌，這一局的下注已全數退還。");
    }

    // ---------------------------------------------------------------- seat traversal helpers

    private int countOccupied() {
        int n = 0;
        for (Seat seat : seats) {
            if (seat.isOccupied()) {
                n++;
            }
        }
        return n;
    }

    private int countActive() {
        int n = 0;
        for (Seat seat : seats) {
            if (seat.isActive()) {
                n++;
            }
        }
        return n;
    }

    private int firstOccupiedSeat() {
        for (Seat seat : seats) {
            if (seat.isOccupied()) {
                return seat.index();
            }
        }
        return -1;
    }

    private int nextOccupiedSeat(int from) {
        return nextSeatMatching(from, Seat::isOccupied);
    }

    private int nextActiveSeat(int from) {
        return nextSeatMatching(from, Seat::isActive);
    }

    private int nextSeatMatching(int from, java.util.function.Predicate<Seat> predicate) {
        int n = seats.length;
        for (int step = 1; step <= n; step++) {
            int idx = Math.floorMod(from + step, n);
            if (predicate.test(seats[idx])) {
                return idx;
            }
        }
        return -1;
    }

    // ---------------------------------------------------------------- admin chip control

    /** Sets a player's chip balance to an absolute amount. Applies live if they're seated between hands. */
    public String adminSetChips(UUID uuid, long amount) {
        if (amount < 0) {
            return "籌碼不能是負數。";
        }
        chipStorage.set(uuid, amount);
        int seatIndex = seatIndexOf(uuid);
        if (seatIndex >= 0) {
            if (stage == GameStage.WAITING) {
                seats[seatIndex].setStack(amount);
            } else {
                return "已更新玩家的籌碼紀錄，但這一局進行中不會馬上生效，下一局開始時才會套用。";
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- disconnects

    /** Called by the quit listener; folds the player out of any hand in progress and schedules their seat to be freed. */
    public void handlePlayerQuit(UUID uuid) {
        int seatIndex = seatIndexOf(uuid);
        if (seatIndex < 0) {
            return;
        }
        if (stage != GameStage.WAITING) {
            forceFold(seatIndex, "斷線");
            pendingLeave.add(seatIndex);
        } else {
            vacateNow(seatIndex);
        }
    }

    private void broadcast(String message) {
        Bukkit.broadcast(Component.text(message));
    }
}
