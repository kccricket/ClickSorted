package net.kccricket.clicksorted.migration;

/*
 * This file is part of ClickSorted
 *
 * ClickSorted is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * ClickSorted is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with ClickSorted. If not, see <http://www.gnu.org/licenses/>.
 */

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * An immutable old&rarr;new remapping for a single string-valued setting, used to migrate values that
 * were renamed between plugin versions (for example the {@code ClickMethod} enum constant
 * {@code DOUBLE} becoming {@code DOUBLE_CLICK}).
 * <p>
 * Renamed settings are modelled as ordered <em>lineages</em> via {@link #builder()}:
 * {@code rename(old).to(next).to(newer)…}. The last token in a lineage is the current canonical
 * value; every earlier token is a historical alias that maps <em>directly</em> to it. Because the
 * lineage collapses to a single {@code alias → canonical} map, any value ever stored converges in a
 * single {@link #migrate} pass — there is no ordering dependence or cycle risk — and extending a
 * lineage is a pure append ({@code .to("X")}) that leaves earlier hops untouched.
 * <p>
 * Lookups are case-insensitive. Tokens with no rule — including {@code null} — are returned
 * unchanged, so a migration can be applied safely to any stored value without first checking
 * whether it is legacy.
 */
public final class ValueMigration {

    private final Map<String, String> remap; // keys upper-cased

    private ValueMigration(Map<String, String> remap) {
        this.remap = remap;
    }

    /**
     * Builds a migration from a map of legacy token &rarr; canonical token. Keys are compared
     * case-insensitively; values are stored verbatim (use the exact canonical spelling).
     */
    public static ValueMigration of(Map<String, String> remap) {
        Map<String, String> upper = new HashMap<>(remap.size());
        remap.forEach((legacy, canonical) -> upper.put(legacy.toUpperCase(Locale.ROOT), canonical));
        return new ValueMigration(Map.copyOf(upper));
    }

    /**
     * @return the canonical token for {@code stored}, or {@code stored} unchanged (including
     *         {@code null}) when no rule applies.
     */
    public String migrate(String stored) {
        if (stored == null) {
            return null;
        }
        return remap.getOrDefault(stored.toUpperCase(Locale.ROOT), stored);
    }

    /** @return true if {@code stored} is a legacy token this migration would rewrite. */
    public boolean isLegacy(String stored) {
        if (stored == null) {
            return false;
        }
        String canonical = remap.get(stored.toUpperCase(Locale.ROOT));
        return canonical != null && !canonical.equals(stored);
    }

    /** @return a new {@link Builder} for declaring renamed-setting lineages. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for one or more renamed-setting lineages. Each lineage is started with
     * {@link #rename(String)} and extended with {@link #to(String)}; for a path
     * {@code [t0…tn]} every earlier token {@code ti} (i &lt; n) maps directly to the canonical
     * last token {@code tn}.
     */
    public static final class Builder {
        private final Map<String, String> remap = new HashMap<>();
        private List<String> current; // the in-progress lineage path

        private Builder() {
        }

        /**
         * Flushes any in-progress lineage and starts a new one seeded with {@code from}.
         */
        public Builder rename(String from) {
            flush();
            current = new ArrayList<>();
            current.add(from);
            return this;
        }

        /**
         * Appends {@code next} to the current lineage.
         *
         * @throws IllegalStateException if no {@link #rename(String)} has started a lineage
         */
        public Builder to(String next) {
            if (current == null) {
                throw new IllegalStateException("to() called before rename()");
            }
            current.add(next);
            return this;
        }

        /**
         * Flushes the final lineage and builds the immutable migration.
         */
        public ValueMigration build() {
            flush();
            Map<String, String> upper = new HashMap<>(remap.size());
            remap.forEach((legacy, canonical) -> upper.put(legacy.toUpperCase(Locale.ROOT), canonical));
            return new ValueMigration(Map.copyOf(upper));
        }

        private void flush() {
            if (current == null) {
                return;
            }
            String canonical = current.get(current.size() - 1);
            for (int i = 0; i < current.size() - 1; i++) {
                String alias = current.get(i);
                if (remap.put(alias, canonical) != null) {
                    throw new IllegalStateException("Token declared as an alias twice: " + alias);
                }
            }
            current = null;
        }
    }
}
