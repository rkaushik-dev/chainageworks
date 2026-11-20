package io.github.rkaushikdev.chainageworks.domain.credit;

import io.github.rkaushikdev.chainageworks.BadInputException;
import io.github.rkaushikdev.chainageworks.Details;
import io.github.rkaushikdev.chainageworks.NotFoundException;
import io.github.rkaushikdev.chainageworks.util.Text;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The rules of credit a job can use, by id.
 *
 * @param profiles the profiles, in the order they were registered
 */
public record ProfileRegistry(Map<String, CreditProfile> profiles) {

    public ProfileRegistry {
        profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
    }

    /** A registry of the standard profiles. */
    public static ProfileRegistry standard() {
        return build(CreditCatalogue.STANDARD_PROFILES);
    }

    /** A registry of {@code profiles}, refusing two that share an id. */
    public static ProfileRegistry build(List<CreditProfile> profiles) {
        Map<String, CreditProfile> map = new LinkedHashMap<>();
        for (CreditProfile profile : profiles) {
            if (map.containsKey(profile.id())) {
                throw new BadInputException("two rules of credit share an id", Details.of("profile", profile.id()));
            }
            map.put(profile.id(), profile);
        }
        return new ProfileRegistry(map);
    }

    public Optional<CreditProfile> find(String id) {
        return Optional.ofNullable(profiles.get(id));
    }

    public CreditProfile require(String id) {
        return find(id).orElseThrow(() -> new NotFoundException("rule of credit", id));
    }

    /**
     * The profile a work class defaults to. Anything not listed falls back to the single step
     * profile, which never overstates progress: it pays on completion and nothing before.
     */
    public CreditProfile defaultFor(String workClass) {
        String id = CreditCatalogue.BY_WORK_CLASS.getOrDefault(Text.upper(workClass), CreditCatalogue.SINGLE_STEP.id());
        return require(id);
    }

    /** The ids, sorted. */
    public List<String> registeredIds() {
        List<String> ids = new ArrayList<>(profiles.keySet());
        Collections.sort(ids);
        return List.copyOf(ids);
    }

    /** A registry with one more profile in it, or one replaced. */
    public ProfileRegistry withProfile(CreditProfile profile) {
        List<CreditProfile> kept = new ArrayList<>(profiles.values().stream()
                .filter(p -> !p.id().equals(profile.id()))
                .toList());
        kept.add(profile);
        return build(kept);
    }
}
