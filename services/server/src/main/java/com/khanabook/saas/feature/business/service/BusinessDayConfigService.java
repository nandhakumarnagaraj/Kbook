package com.khanabook.saas.feature.business.service;

import com.khanabook.saas.core.utility.BusinessDay;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfile;
import com.khanabook.saas.feature.restaurants.data.RestaurantProfileRepository;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminal;
import com.khanabook.saas.feature.restaurants.data.RestaurantTerminalRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * The only writer of {@code business_day_cutoff_time} / {@code cutoff_effective_from}.
 *
 * <p>Two rules govern this class, and both exist to protect receipts that are already
 * printed:
 *
 * <ol>
 *   <li><b>Deactivation is always allowed; activation is gated.</b> Turning the cutoff off
 *       returns every terminal to the calendar day — exactly what an old build computes, so
 *       it cannot renumber anything. Turning it on does the opposite: an old client keeps
 *       printing day-local numbers while the server files the bill under the previous
 *       business day, the duplicate-key repair kicks in, and the receipt reprints with a new
 *       number. So activation requires every ACTIVE terminal to have registered from a build
 *       that ships the shared rule ({@code BusinessDay} / {@code BusinessDate.kt}).</li>
 *   <li><b>The effective date only moves forward.</b> Once a cutoff is in force, bills have
 *       been attributed under it; moving the date later would re-attribute those early-morning
 *       hours, and moving it earlier would do the same in reverse. A date still in the future
 *       may be postponed, never pulled closer.</li>
 * </ol>
 *
 * <p>{@code app.version.business-day-min} is the build number at which a terminal is trusted.
 * It defaults to {@code 0}, which means <em>no</em> build is trusted yet: activation stays
 * blocked until ops sets the released version code. That default is deliberate — trusting
 * every versioned client would mark today's shipped builds capable and remove the very
 * protection the gate exists for.
 */
@Service
@RequiredArgsConstructor
public class BusinessDayConfigService {

    /**
     * Latest acceptable cutoff. Restaurants close after midnight; a "cutoff" in the afternoon
     * is a mis-entered closing time, and silently accepting it would shift a whole day's trade.
     */
    private static final LocalTime LATEST_CUTOFF = LocalTime.NOON;

    private final RestaurantProfileRepository profileRepository;
    private final RestaurantTerminalRepository terminalRepository;

    @Value("${app.version.business-day-min:0}")
    private int businessDayMinVersion = 0;

    /** True when this build number understands the cutoff rule. */
    public boolean isCutoffCapable(Integer appVersionCode) {
        return businessDayMinVersion > 0
                && appVersionCode != null
                && appVersionCode >= businessDayMinVersion;
    }

    /**
     * Marks the terminal capable when the calling build qualifies. Idempotent, and never
     * downgrades: capability is a property of the hardware's last-seen build, so a later
     * request from a stale process must not revoke it while the device is actually updated
     * only on the next install.
     *
     * @return true when this call flipped the flag and saved.
     */
    @Transactional
    public boolean recordCapability(RestaurantTerminal terminal, Integer appVersionCode) {
        if (terminal == null
                || terminal.getId() == null
                || Boolean.TRUE.equals(terminal.getCutoffCapable())
                || !isCutoffCapable(appVersionCode)) {
            return false;
        }
        terminal.setCutoffCapable(true);
        terminalRepository.save(terminal);
        return true;
    }

    @Transactional(readOnly = true)
    public BusinessDayConfig get(Long restaurantId) {
        return toConfig(requireProfile(restaurantId));
    }

    /**
     * Sets the cutoff. A {@code null} effective date deactivates (calendar days), which is
     * always permitted.
     *
     * @param cutoffTime     {@code HH:mm} or {@code HH:mm:ss}; {@code 00:00} means "no cutoff".
     * @param effectiveFrom  first business day under the new rule, or {@code null} to deactivate.
     */
    @Transactional
    public BusinessDayConfig update(Long restaurantId, String cutoffTime, LocalDate effectiveFrom) {
        RestaurantProfile profile = requireProfile(restaurantId);
        ZoneId zone = BusinessDay.zoneOf(profile.getTimezone());
        LocalTime cutoff = parseCutoff(cutoffTime);

        if (!cutoff.isBefore(LATEST_CUTOFF)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A business-day cutoff must be before 12:00 — usually between 02:00 and 06:00.");
        }

        if (effectiveFrom == null) {
            // Deactivation cannot renumber anything: the calendar day is what every build,
            // capable or not, already computes. No gate, no history check.
            profile.setBusinessDayCutoffTime(cutoff);
            profile.setCutoffEffectiveFrom(null);
            return toConfig(save(profile));
        }

        if (LocalTime.MIDNIGHT.equals(cutoff)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A 00:00 cutoff is the calendar day. Leave the effective date empty to stay on calendar days.");
        }

        // With no trusted build, no terminal can ever qualify — including the next one to
        // register. Refusing here keeps a shop from activating while empty and then having
        // an old build start printing day-local numbers under the new rule.
        if (businessDayMinVersion <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "No app build is trusted to compute a business day yet. Set "
                            + "app.version.business-day-min to the released app version code before activating the cutoff.");
        }

        LocalDate today = LocalDate.now(zone);
        if (effectiveFrom.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The effective date cannot be in the past (today is " + today + " in " + zone + ").");
        }

        Long existing = profile.getCutoffEffectiveFrom();
        if (existing != null) {
            LocalDate existingDate = Instant.ofEpochMilli(existing).atZone(zone).toLocalDate();
            if (!existingDate.isAfter(today)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "The cutoff has been in force since " + existingDate + ". Bills are already "
                                + "attributed under it, so the activation date cannot be changed. "
                                + "Turn the cutoff off if the shop should return to calendar days.");
            }
            if (!effectiveFrom.isAfter(existingDate)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A scheduled cutoff can only be postponed, not moved earlier "
                                + "(it is currently set for " + existingDate + ").");
            }
        }

        List<RestaurantTerminal> blocked = terminalRepository
                .findByRestaurantIdAndStatus(restaurantId, "ACTIVE")
                .stream()
                .filter(t -> !Boolean.TRUE.equals(t.getCutoffCapable()))
                .toList();
        if (!blocked.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, blockedMessage(blocked));
        }

        profile.setBusinessDayCutoffTime(cutoff);
        profile.setCutoffEffectiveFrom(effectiveFrom.atStartOfDay(zone).toInstant().toEpochMilli());
        return toConfig(save(profile));
    }

    private String blockedMessage(List<RestaurantTerminal> blocked) {
        String names = blocked.stream()
                .map(t -> t.getTerminalSeries() == null ? String.valueOf(t.getId()) : t.getTerminalSeries())
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String why = businessDayMinVersion > 0
                ? "Update the KhanaBook app on " + blocked.size() + " terminal(s) first"
                : "Set app.version.business-day-min to the released app version code first";
        return "The cutoff cannot be activated yet: terminals " + names
                + " run a build that computes calendar days and would have their printed order "
                + "numbers renumbered. " + why + ".";
    }

    private RestaurantProfile save(RestaurantProfile profile) {
        long now = System.currentTimeMillis();
        profile.setUpdatedAt(now);
        profile.setServerUpdatedAt(now);
        return profileRepository.save(profile);
    }

    private RestaurantProfile requireProfile(Long restaurantId) {
        return profileRepository.findByRestaurantId(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Restaurant profile not found"));
    }

    private LocalTime parseCutoff(String raw) {
        if (raw == null || raw.isBlank()) {
            return LocalTime.MIDNIGHT;
        }
        String text = raw.trim();
        try {
            // Strict on purpose: BusinessDay.parseCutoff falls back to midnight, which would
            // turn a typo into a silent "no cutoff" on a screen that claims one is active.
            return LocalTime.parse(text.length() == 5 ? text + ":00" : text);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cutoff time must look like HH:mm.");
        }
    }

    private BusinessDayConfig toConfig(RestaurantProfile profile) {
        List<TerminalCapability> terminals = terminalRepository
                .findByRestaurantIdOrderByIdAsc(profile.getRestaurantId())
                .stream()
                .map(t -> new TerminalCapability(
                        t.getId(),
                        t.getTerminalSeries(),
                        t.getTerminalName(),
                        "ACTIVE".equals(t.getStatus()),
                        Boolean.TRUE.equals(t.getCutoffCapable()),
                        t.getLastSeenAt()))
                .toList();
        return new BusinessDayConfig(
                format(profile.getBusinessDayCutoffTime()),
                profile.getCutoffEffectiveFrom(),
                BusinessDay.isActive(profile.getBusinessDayCutoffTime(), profile.getCutoffEffectiveFrom()),
                profile.getTimezone(),
                businessDayMinVersion,
                terminals);
    }

    private static String format(LocalTime time) {
        LocalTime value = time == null ? LocalTime.MIDNIGHT : time;
        return String.format("%02d:%02d", value.getHour(), value.getMinute());
    }

    /**
     * @param cutoffTime          {@code HH:mm}
     * @param effectiveFrom       epoch ms, or null when the cutoff is dormant
     * @param active              true only when the rule is actually in force
     * @param capableFromVersion  configured build gate; 0 means no build is trusted yet
     */
    public record BusinessDayConfig(
            String cutoffTime,
            Long effectiveFrom,
            boolean active,
            String timezone,
            int capableFromVersion,
            List<TerminalCapability> terminals) {
    }

    public record TerminalCapability(
            Long terminalId,
            String terminalSeries,
            String terminalName,
            boolean active,
            boolean cutoffCapable,
            Long lastSeenAt) {
    }
}
