package com.foliagui.gui;

import com.foliagui.FoliaGUIService;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.ApiStatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/** Composes managed text inputs into a typed result, with explicit back navigation and optional menu restoration. */
@ApiStatus.Experimental
public final class InputForm<T> {
    /** A submitted typed result, or a terminal input status without a value. */
    public record Result<T>(InputResult.Status status, Optional<T> value) {
        /** Requires a value only for submitted forms. */
        public Result {
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(value, "value");
            if ((status == InputResult.Status.SUBMITTED) != value.isPresent()) {
                throw new IllegalArgumentException("Only submitted forms carry values");
            }
        }
    }

    private record Step(String name, TextInput input, Function<String, ?> parser) { }
    private final List<Step> steps;
    private final Function<Map<String, Object>, T> mapper;
    private final boolean restore;

    private InputForm(Builder<T> builder) {
        steps = List.copyOf(builder.steps);
        mapper = builder.mapper;
        restore = builder.restore;
    }

    /** Starts a form builder with a final typed result mapper. Step values are named and immutable at mapping time. */
    public static <T> Builder<T> builder(Function<Map<String, Object>, T> mapper) {
        return new Builder<>(mapper);
    }

    /** Starts independent form state on the player's owning thread. */
    public Session open(FoliaGUIService service, Player player) {
        Session session = new Session(service, player);
        service.scheduler().runForEntity(player, () -> {
            session.origin = service.guis().getOpenGui(player);
            session.show();
        }, () -> session.end(InputResult.Status.DISCONNECTED));
        return session;
    }

    /** One player's form progression. Every action schedules through the service. */
    public final class Session implements AutoCloseable {
        private final FoliaGUIService service;
        private final Player player;
        private final Map<String, Object> values = new LinkedHashMap<>();
        private final CompletableFuture<Result<T>> completion = new CompletableFuture<>();
        private BaseGui origin;
        private InputSession active;
        private int step;
        private long revision;

        private Session(FoliaGUIService service, Player player) {
            this.service = Objects.requireNonNull(service, "service");
            this.player = Objects.requireNonNull(player, "player");
        }

        /** Returns the eventual typed form result without exposing the mutable completion. */
        public CompletableFuture<Result<T>> result() {
            return completion.copy();
        }

        /** Returns to the preceding step, discarding values for that step and all following steps. */
        public void back() {
            service.scheduler().runForEntity(player, () -> {
                if (completion.isDone() || step == 0) {
                    return;
                }
                revision++;
                if (active != null) {
                    active.cancel();
                }
                step--;
                for (int index = step; index < steps.size(); index++) {
                    values.remove(steps.get(index).name());
                }
                show();
            }, () -> end(InputResult.Status.DISCONNECTED));
        }

        private void show() {
            if (completion.isDone()) {
                return;
            }
            if (step == steps.size()) {
                try {
                    T value = Objects.requireNonNull(mapper.apply(Map.copyOf(values)), "mapped value");
                    completion.complete(new Result<>(InputResult.Status.SUBMITTED, Optional.of(value)));
                    restore();
                } catch (RuntimeException failure) {
                    end(InputResult.Status.FAILED);
                }
                return;
            }
            long expected = ++revision;
            Step current = steps.get(step);
            active = current.input().open(service, player);
            active.result().thenAccept(result -> {
                if (expected != revision || completion.isDone()) {
                    return;
                }
                if (result.status() != InputResult.Status.SUBMITTED) {
                    end(result.status());
                    return;
                }
                try {
                    values.put(current.name(), Objects.requireNonNull(current.parser().apply(result.text().orElseThrow()), "step value"));
                    step++;
                    show();
                } catch (RuntimeException invalid) {
                    player.sendMessage(com.foliagui.util.Text.of(service.theme(player).message(GuiMessage.INVALID_INPUT)));
                    show();
                }
            });
        }

        private void end(InputResult.Status status) {
            if (completion.complete(new Result<>(status, Optional.empty()))) {
                restore();
            }
        }

        private void restore() {
            if (restore && origin != null && !service.isClosed()) {
                service.scheduler().runForEntity(player, () -> {
                    if (player.isOnline()) {
                        origin.open(player);
                    }
                }, null);
            }
        }

        /** Cancels all remaining steps and restores the originating menu when configured. */
        @Override
        public void close() {
            service.scheduler().runForEntity(player, () -> {
                revision++;
                end(InputResult.Status.CANCELLED);
                if (active != null) {
                    active.cancel();
                }
            }, () -> end(InputResult.Status.DISCONNECTED));
        }
    }

    /** Builds an immutable sequence of named input recipes and parsers. */
    public static final class Builder<T> {
        private final List<Step> steps = new ArrayList<>();
        private final Function<Map<String, Object>, T> mapper;
        private boolean restore = true;

        private Builder(Function<Map<String, Object>, T> mapper) {
            this.mapper = Objects.requireNonNull(mapper, "mapper");
        }

        /** Adds one uniquely named step. Parser failures display feedback and retry that step. */
        public Builder<T> step(String name, TextInput input, Function<String, ?> parser) {
            Objects.requireNonNull(name, "name");
            if (name.isBlank() || steps.stream().anyMatch(step -> step.name().equals(name))) {
                throw new IllegalArgumentException("Step names must be nonblank and unique");
            }
            steps.add(new Step(name, Objects.requireNonNull(input, "input"), Objects.requireNonNull(parser, "parser")));
            return this;
        }

        /** Controls restoration of the service's originating menu after completion or cancellation. */
        public Builder<T> restoreMenu(boolean restore) {
            this.restore = restore;
            return this;
        }

        /** Freezes a nonempty form recipe. */
        public InputForm<T> build() {
            if (steps.isEmpty()) {
                throw new IllegalStateException("A form needs at least one step");
            }
            return new InputForm<>(this);
        }
    }
}
