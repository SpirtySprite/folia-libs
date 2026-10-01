package com.foliagui.gui;

import com.foliagui.FoliaGUIService;
import com.foliagui.util.Text;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Portable text input with validation, retry, timeouts and capability-based fallback. Instances are reusable recipes. */
@ApiStatus.Experimental
public final class TextInput {
    /** AUTO tries supported anvil, sign, then chat presentation. Explicit modes report unsupported capabilities. */
    public enum Mode { AUTO, ANVIL, SIGN, CHAT }

    private final String prompt;
    private final List<Mode> modes;
    private final InputValidator validator;
    private final long timeoutTicks;

    private TextInput(Builder builder) {
        prompt = builder.prompt;
        modes = List.copyOf(builder.modes);
        validator = builder.validator;
        timeoutTicks = builder.timeoutTicks;
    }

    /** Starts building an immutable input recipe. */
    public static Builder builder() {
        return new Builder();
    }

    /** Opens a fresh session on the player's thread, replacing any managed input in this service. */
    public InputSession open(FoliaGUIService service, Player player) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(player, "player");
        InputSession session = new InputSession(service, player);
        if (service.isClosed()) {
            session.finish(InputResult.ended(InputResult.Status.CANCELLED));
            return session;
        }
        service.scheduler().runForEntity(player, () -> {
            InputSession previous = service.sessions().input.get(player);
            if (previous != null) {
                previous.finish(InputResult.ended(InputResult.Status.CANCELLED));
            }
            service.sessions().input.put(player, session);
            if (timeoutTicks > 0) {
                session.timeout = service.scheduler().runForEntityTimer(player,
                        () -> session.finish(InputResult.ended(InputResult.Status.TIMED_OUT)),
                        () -> session.finish(InputResult.ended(InputResult.Status.DISCONNECTED)), timeoutTicks, timeoutTicks);
            }
            present(session, 0);
        }, () -> session.finish(InputResult.ended(InputResult.Status.DISCONNECTED)));
        return session;
    }

    private void present(InputSession session, int first) {
        if (session.isCancelled()) {
            return;
        }
        for (int index = first; index < modes.size(); index++) {
            Mode mode = modes.get(index);
            if (!supported(mode)) {
                continue;
            }
            try {
                show(session, mode);
                return;
            } catch (RuntimeException | LinkageError unsupported) {
                if (index == modes.size() - 1) {
                    session.finish(InputResult.ended(InputResult.Status.FAILED));
                    return;
                }
            }
        }
        session.finish(InputResult.ended(InputResult.Status.UNSUPPORTED));
    }

    private void show(InputSession session, Mode mode) {
        switch (mode) {
            case CHAT -> {
                ChatPrompt.ask(session.service, session.player, prompt, 0, text -> {
                    if (!session.isCancelled()) {
                        if (text == null) {
                            session.finish(InputResult.ended(InputResult.Status.CANCELLED));
                        } else if (!accept(session, text)) {
                            show(session, Mode.CHAT);
                        }
                    }
                });
                ChatPrompt installed = session.service.sessions().chat.get(session.player);
                session.cleanup = () -> {
                    if (session.service.sessions().chat.remove(session.player, installed)) {
                        installed.cancelTimeout();
                    }
                };
            }
            case ANVIL -> {
                AnvilGui gui = AnvilGui.builder().service(session.service).title(prompt).text("")
                        .onComplete((player, text) -> accept(session, text) ? AnvilGui.Response.close() : AnvilGui.Response.keepOpen())
                        .onClose(player -> session.finish(InputResult.ended(InputResult.Status.CANCELLED))).build();
                session.cleanup = () -> gui.cancel(session.player);
                opened(session, mode, gui.openAsync(session.player));
            }
            case SIGN -> {
                SignGui gui = SignGui.builder().service(session.service).lines("", prompt, "", "").timeout(0)
                        .onComplete((player, lines) -> {
                            if (!accept(session, lines.getFirst())) {
                                show(session, Mode.SIGN);
                            }
                        }).build();
                session.cleanup = () -> gui.cancel(session.player);
                opened(session, mode, gui.openAsync(session.player));
            }
            case AUTO -> throw new IllegalStateException("AUTO is expanded before presentation");
        }
    }

    private void opened(InputSession session, Mode mode, java.util.concurrent.CompletableFuture<GuiOperationResult> opening) {
        opening.whenComplete((outcome, failure) -> {
            if (session.isCancelled()) {
                session.cleanup.run();
                return;
            }
            if (outcome == GuiOperationResult.RETIRED) {
                session.finish(InputResult.ended(InputResult.Status.DISCONNECTED));
            } else if (session.service.isClosed()) {
                session.finish(InputResult.ended(InputResult.Status.CANCELLED));
            } else if (failure != null || outcome != GuiOperationResult.OPENED) {
                session.cleanup.run();
                present(session, modes.indexOf(mode) + 1);
            }
        });
    }

    private boolean accept(InputSession session, String text) {
        if (session.isCancelled()) {
            return false;
        }
        try {
            Optional<String> error = validator.validate(text);
            if (error.isPresent()) {
                session.player.sendMessage(Text.of(error.get()));
                return false;
            }
            session.finish(InputResult.submitted(text));
            return true;
        } catch (RuntimeException failure) {
            session.finish(InputResult.ended(InputResult.Status.FAILED));
            return true;
        }
    }

    private static boolean supported(Mode mode) {
        String type = switch (mode) {
            case ANVIL -> "org.bukkit.inventory.MenuType";
            case SIGN -> "io.papermc.paper.event.packet.UncheckedSignChangeEvent";
            case CHAT -> "io.papermc.paper.event.player.AsyncChatEvent";
            case AUTO -> throw new IllegalArgumentException("AUTO must be expanded");
        };
        try {
            Class.forName(type, false, TextInput.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError missing) {
            return false;
        }
    }

    /** Configures presentation, validation and an overall deadline that includes retries. */
    public static final class Builder {
        private String prompt = "Enter text";
        private List<Mode> modes = List.of(Mode.ANVIL, Mode.SIGN, Mode.CHAT);
        private InputValidator validator = text -> Optional.empty();
        private long timeoutTicks = 1200;

        /** Sets the title, sign hint or chat prompt. */
        public Builder prompt(String prompt) {
            this.prompt = Objects.requireNonNull(prompt, "prompt");
            return this;
        }

        /** Selects explicit presentation or automatic capability fallback. */
        public Builder mode(Mode mode) {
            modes = mode == Mode.AUTO ? List.of(Mode.ANVIL, Mode.SIGN, Mode.CHAT) : List.of(Objects.requireNonNull(mode, "mode"));
            return this;
        }

        /** Sets preferred fallback order. AUTO is not a concrete presentation mode. */
        public Builder fallback(Mode... preferred) {
            modes = List.of(preferred);
            if (modes.isEmpty() || modes.contains(Mode.AUTO)) {
                throw new IllegalArgumentException("Concrete fallback modes required");
            }
            return this;
        }

        /** Installs a validator; invalid submissions display feedback and keep the input active. */
        public Builder validate(InputValidator validator) {
            this.validator = Objects.requireNonNull(validator, "validator");
            return this;
        }

        /** Sets an overall tick deadline. Zero disables timeout. */
        public Builder timeout(long ticks) {
            if (ticks < 0) {
                throw new IllegalArgumentException("timeout must be nonnegative");
            }
            timeoutTicks = ticks;
            return this;
        }

        /** Freezes the recipe for reuse by independent player sessions. */
        public TextInput build() {
            return new TextInput(this);
        }
    }
}
