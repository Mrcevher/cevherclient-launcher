package net.cevherclient.client.module.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.cevherclient.client.module.HudModule;
import net.cevherclient.client.module.ModuleManager;
import net.cevherclient.client.module.Option;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Yan skor tablosunu istenen yere taşır ve boyutlandırılabilir yapar; skor numaraları gizlenebilir.
 * Tabloyu oyunla aynı kurallarla (takım rengi yuvası, en fazla 15 satır, sıralama) kendisi çizer.
 */
public final class ScoreboardModule extends HudModule {
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value)
		.reversed()
		.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final int ROW = 9;

	private final Option.Bool numbers = add(new Option.Bool("numbers", "Skor numaraları", true));
	private final Option.Range opacity = add(new Option.Range("opacity", "Arka plan", 0.3f, 0f, 1f, 0.05f));

	private record Line(Component name, Component score) {
	}

	private record Table(Component title, List<Line> lines) {
	}

	public ScoreboardModule() {
		super("scoreboard", "Skor Tablosu", "SKR", false, 0.84f, 0.32f);
	}

	public static boolean replacesVanilla() {
		ScoreboardModule module = ModuleManager.get(ScoreboardModule.class);
		return module != null && module.isEnabled();
	}

	private static Objective objective(Minecraft mc) {
		if (mc.level == null || mc.player == null) {
			return null;
		}
		Scoreboard scoreboard = mc.level.getScoreboard();
		PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
		if (team != null) {
			DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
			Objective teamObjective = slot == null ? null : scoreboard.getDisplayObjective(slot);
			if (teamObjective != null) {
				return teamObjective;
			}
		}
		return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
	}

	private static Table table(Minecraft mc, boolean preview) {
		Objective objective = objective(mc);
		if (objective == null) {
			if (!preview) {
				return null;
			}
			List<Line> sample = new ArrayList<>();
			for (String name : new String[] {"Oyuncular: 24", "Harita: Ada", "", "oyna.sunucu.com"}) {
				sample.add(new Line(Component.literal(name), Component.literal(Integer.toString(sample.size())).withStyle(ChatFormatting.RED)));
			}
			return new Table(Component.literal("Cevherclient").withStyle(ChatFormatting.GOLD), sample);
		}
		Scoreboard scoreboard = objective.getScoreboard();
		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<Line> lines = scoreboard.listPlayerScores(objective).stream()
			.filter(entry -> !entry.isHidden())
			.sorted(ORDER)
			.limit(15L)
			.map(entry -> new Line(
				PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()),
				entry.formatValue(format)))
			.toList();
		return new Table(objective.getDisplayName(), lines);
	}

	@Override
	public int width(Minecraft mc, boolean preview) {
		Table table = table(mc, preview);
		if (table == null) {
			return 80;
		}
		int width = mc.font.width(table.title());
		int separator = mc.font.width(": ");
		for (Line line : table.lines()) {
			int scoreWidth = numbers.value ? mc.font.width(line.score()) : 0;
			width = Math.max(width, mc.font.width(line.name()) + (scoreWidth > 0 ? separator + scoreWidth : 0));
		}
		return width + 4;
	}

	@Override
	public int height(Minecraft mc, boolean preview) {
		Table table = table(mc, preview);
		return table == null ? ROW + 2 : (table.lines().size() + 1) * ROW + 2;
	}

	@Override
	public void draw(GuiGraphics graphics, Minecraft mc, boolean preview) {
		Table table = table(mc, preview);
		if (table == null) {
			return;
		}
		int w = width(mc, preview);
		int h = height(mc, preview);
		int body = mc.options.getBackgroundColor(opacity.value);
		int header = mc.options.getBackgroundColor(Math.min(1f, opacity.value + 0.1f));
		graphics.fill(0, 0, w, ROW + 1, header);
		graphics.fill(0, ROW + 1, w, h, body);
		graphics.drawString(mc.font, table.title(), (w - mc.font.width(table.title())) / 2, 1, 0xFFFFFFFF, false);
		int y = ROW + 2;
		for (Line line : table.lines()) {
			graphics.drawString(mc.font, line.name(), 2, y, 0xFFFFFFFF, false);
			if (numbers.value) {
				graphics.drawString(mc.font, line.score(), w - 2 - mc.font.width(line.score()), y, 0xFFFFFFFF, false);
			}
			y += ROW;
		}
	}
}
