package net.folianpc.internal.skin;

import net.folianpc.api.SkinFetchResult;
import java.time.Duration;

final class SkinFailure extends RuntimeException {
    final SkinFetchResult.Status status;
    final Duration retryAfter;
    SkinFailure(SkinFetchResult.Status status, String message, Duration retryAfter) {
        super(message);
        this.status = status;
        this.retryAfter = retryAfter;
    }
}
