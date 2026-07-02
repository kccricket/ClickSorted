package net.kccricket.clicksorted.migration;

/**
 * A composable, store-neutral migration rule: {@link #apply} returns {@code true} if it made a
 * change. Rules are composed in a list; the list is iterated in order so that earlier rules (e.g.
 * key-renames) finish before later ones (e.g. value-remaps) read the renamed keys.
 *
 * <p>Static factory methods cover the four common cases:
 * <ul>
 *   <li>{@link #renameKey} — move a value from one leaf to another and clear the old leaf.</li>
 *   <li>{@link #remap} — rewrite a value via a {@link ValueMigration} lineage.</li>
 *   <li>{@link #remove} — drop a deprecated leaf.</li>
 *   <li>{@link #when when(leaf).is(value).then(effects…)} — conditional branch.</li>
 * </ul>
 *
 * <p>Effect factories ({@link #set(String, String)}, {@link #set(String, boolean)},
 * {@link #clear(String)}) produce {@link Effect} values for use inside {@code then(…)}.
 */
@FunctionalInterface
public interface Migration {
    boolean apply(Store store);

    /** A side-effect applied to a {@link Store} inside a conditional branch. */
    @FunctionalInterface
    interface Effect {
        void apply(Store store);
    }

    /**
     * Moves the string value stored at {@code oldLeaf} to {@code newLeaf} and clears the old leaf.
     * No-op when the old leaf is absent.
     */
    static Migration renameKey(String oldLeaf, String newLeaf) {
        return store -> {
            if (!store.contains(oldLeaf)) return false;
            String value = store.getString(oldLeaf);
            if (value != null) store.setString(newLeaf, value);
            store.clear(oldLeaf);
            return true;
        };
    }

    /**
     * Moves the boolean value stored at {@code oldLeaf} to {@code newLeaf} and clears the old leaf.
     * No-op when the old leaf is absent. Use instead of {@link #renameKey} for boolean preferences
     * (PDC stores these as BYTE, not STRING, so {@code getString} returns null for them).
     */
    static Migration renameBooleanKey(String oldLeaf, String newLeaf) {
        return store -> {
            if (!store.contains(oldLeaf)) return false;
            Boolean value = store.getBoolean(oldLeaf);
            if (value != null) store.setBoolean(newLeaf, value);
            store.clear(oldLeaf);
            return true;
        };
    }

    /** Remaps the value at {@code leaf} through {@code lineage}. No-op when absent or already canonical. */
    static Migration remap(String leaf, ValueMigration lineage) {
        return store -> {
            String current = store.getString(leaf);
            String migrated = lineage.migrate(current);
            if (migrated != null && !migrated.equals(current)) {
                store.setString(leaf, migrated);
                return true;
            }
            return false;
        };
    }

    /** Removes the deprecated leaf from the store. No-op when absent. */
    static Migration remove(String leaf) {
        return store -> {
            if (!store.contains(leaf)) return false;
            store.clear(leaf);
            return true;
        };
    }

    /** Starts a conditional rule: {@code when(leaf).is(value).then(effects…)}. */
    static WhenBuilder when(String leaf) {
        return new WhenBuilder(leaf);
    }

    /** Effect factory: write a string value to {@code leaf}. */
    static Effect set(String leaf, String value) {
        return store -> store.setString(leaf, value);
    }

    /** Effect factory: write a boolean value to {@code leaf}. */
    static Effect set(String leaf, boolean value) {
        return store -> store.setBoolean(leaf, value);
    }

    /** Effect factory: clear {@code leaf} from the store. */
    static Effect clear(String leaf) {
        return store -> store.clear(leaf);
    }

    final class WhenBuilder {
        private final String leaf;
        private WhenBuilder(String leaf) { this.leaf = leaf; }
        public WhenIsBuilder is(String value) { return new WhenIsBuilder(leaf, value); }
    }

    final class WhenIsBuilder {
        private final String leaf;
        private final String matchValue;
        private WhenIsBuilder(String leaf, String matchValue) { this.leaf = leaf; this.matchValue = matchValue; }

        /** Creates a {@link Migration} that applies {@code effects} when the stored value matches (case-insensitive). */
        public Migration then(Effect... effects) {
            return store -> {
                String current = store.getString(leaf);
                if (current == null || !current.equalsIgnoreCase(matchValue)) return false;
                for (Effect e : effects) e.apply(store);
                return true;
            };
        }
    }
}
