package net.rose.elementevents.lifedeath;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.rose.elementSMPRefined.util.parse.ParseUtil;
import net.rose.elementevents.mythic.MythicBook.Kind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds the dialogs that show the Life &amp; Death scoring: live standings and final results. */
final class LifeDeathDialogs {
    private static final int TOP_PLAYERS = 5;
    private static final int BODY_WIDTH = 260;
    private static final TextColor LIFE_COLOR = TextColor.color(0x7dff7d);
    private static final TextColor DEATH_COLOR = TextColor.color(0xd36bff);

    private LifeDeathDialogs() {}

    /** The standings while the event is running. */
    static Dialog standings(int remainingSeconds, Map<Kind, Map<String, Integer>> standings) {
        List<DialogBody> body = new ArrayList<>();
        body.add(
                message(
                        Component.text(
                                "Time left: " + ParseUtil.formatClock(remainingSeconds),
                                NamedTextColor.GRAY)));
        for (Kind kind : Kind.values()) {
            body.add(message(section(kind, standings.get(kind), null)));
        }
        return dialog(body);
    }

    /** The final results; {@code winners} holds the winner's name per kind, or null for none. */
    static Dialog results(Map<Kind, Map<String, Integer>> standings, Map<Kind, String> winners) {
        List<DialogBody> body = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            String winner = winners.get(kind);
            Component summary =
                    winner == null
                            ? Component.text("No winner", NamedTextColor.GRAY)
                            : Component.text(
                                    "Winner: "
                                            + winner
                                            + " ("
                                            + standings.get(kind).get(winner)
                                            + " kills)",
                                    NamedTextColor.YELLOW);
            body.add(message(section(kind, standings.get(kind), summary)));
        }
        return dialog(body);
    }

    private static Component section(Kind kind, Map<String, Integer> top, Component summary) {
        TextColor color = kind == Kind.LIFE ? LIFE_COLOR : DEATH_COLOR;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text(LifeDeathEvent.capitalise(kind), color, TextDecoration.BOLD));
        if (summary != null) lines.add(summary);
        if (top.isEmpty()) {
            if (summary == null) lines.add(Component.text("Nobody yet", NamedTextColor.GRAY));
        } else {
            int rank = 0;
            for (Map.Entry<String, Integer> entry : top.entrySet()) {
                if (rank == TOP_PLAYERS) break;
                rank++;
                lines.add(
                        Component.text(
                                rank + ". " + entry.getKey() + " - " + entry.getValue(),
                                NamedTextColor.WHITE));
            }
        }
        return Component.join(JoinConfiguration.newlines(), lines);
    }

    private static DialogBody message(Component text) {
        return DialogBody.plainMessage(text, BODY_WIDTH);
    }

    private static Dialog dialog(List<DialogBody> body) {
        return Dialog.create(
                factory ->
                        factory.empty()
                                .base(
                                        DialogBase.builder(LifeDeathEvent.TITLE)
                                                .body(body)
                                                .canCloseWithEscape(true)
                                                .build())
                                .type(DialogType.notice()));
    }
}
