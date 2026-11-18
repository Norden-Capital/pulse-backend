package com.nordencapital.pulse.profile;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * The optional {@code profile} of a register request. Only the answers and an explicit
 * manual override are taken from the client; the plan is always recalculated on the
 * server.
 *
 * @param targets the manual override, or null (use the calculated plan)
 */
public record ProfileRequest(@NotNull @Valid ProfileAnswers answers, @Valid ManualTargets targets) {
}
