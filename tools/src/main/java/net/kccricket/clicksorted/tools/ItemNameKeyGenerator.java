package net.kccricket.clicksorted.tools;

import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;

/**
 * Generates {@code tools/items.generated.yml}: a table of {@code items.yml}-style
 * material → sort-key overrides that rotate leading qualifier words (colors, wood species,
 * copper weathering, tool tiers, ...) to the end of the name, so variants of the same base
 * item land contiguously under the {@code NAME}/{@code TREEMAP} sort methods.
 *
 * <p>Example: {@code COBBLED_DEEPSLATE} -> {@code DEEPSLATE_COBBLED},
 * {@code WAXED_WEATHERED_CUT_COPPER_STAIRS} -> {@code COPPER_STAIRS_CUT_WEATHERED_WAXED}.
 *
 * <p>Not wired into the plugin build or runtime. Run via {@code ./gradlew generateItemNames};
 * review the report on stdout, then commit the regenerated {@code tools/items.generated.yml}.
 */
public final class ItemNameKeyGenerator {

    private ItemNameKeyGenerator() {}

    // -------------------------------------------------------------------------------------------
    // Phase 1: suffix family promotion — a trailing family noun moves to the front.
    // Longest suffix first so e.g. "SMITHING_TEMPLATE" isn't shadowed by a shorter match.
    // -------------------------------------------------------------------------------------------
    private static final List<List<String>> SUFFIX_FAMILIES = List.of(
            List.of("ON", "A", "STICK"),
            List.of("SPAWN", "EGG"),
            List.of("POTTERY", "SHERD"),
            List.of("SMITHING", "TEMPLATE"),
            List.of("BANNER", "PATTERN"),
            List.of("HORSE", "ARMOR"),
            List.of("ITEM", "FRAME"),
            List.of("BOAT"),
            List.of("RAFT"),
            List.of("MINECART"),
            List.of("BUCKET"),
            List.of("HEAD"),
            List.of("SKULL")
    );

    // -------------------------------------------------------------------------------------------
    // Phase 2: leading qualifier rotation.
    // -------------------------------------------------------------------------------------------

    /** Qualifiers that rotate whenever they lead, regardless of what follows. */
    private static final Set<String> UNCONDITIONAL_QUALIFIERS = Set.of(
            // Colors
            "WHITE", "ORANGE", "MAGENTA", "LIGHT_BLUE", "YELLOW", "LIME", "PINK", "GRAY",
            "LIGHT_GRAY", "CYAN", "PURPLE", "BLUE", "BROWN", "GREEN", "RED", "BLACK",
            // Wood species
            "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY",
            "PALE_OAK", "BAMBOO", "CRIMSON", "WARPED",
            // Copper weathering
            "WAXED", "EXPOSED", "WEATHERED", "OXIDIZED",
            // Stone / surface treatment
            "POLISHED", "CHISELED", "CRACKED", "SMOOTH", "CUT", "COBBLED", "MOSSY", "INFESTED",
            "CARVED", "GLAZED", "STAINED", "PACKED",
            // Misc leading adjectives
            "STRIPPED", "RAW", "DEAD", "WET", "ENCHANTED", "GLOW", "SOUL", "SUSPICIOUS",
            "SPLASH", "LINGERING", "TIPPED", "SPECTRAL", "POWERED", "DETECTOR", "ACTIVATOR",
            "CHIPPED", "DAMAGED", "BIG", "SMALL", "MEDIUM", "LARGE", "REINFORCED",
            // Pale Garden moss variants (PALE_MOSS_BLOCK, PALE_MOSS_CARPET, PALE_HANGING_MOSS) —
            // distinct from the 2-token "PALE_OAK" wood-species qualifier above, which the
            // length-first match order always tries first, so no collision.
            "PALE"
    );

    /** Longest multi-token qualifier is 2 tokens (LIGHT_BLUE, DARK_OAK, ...). */
    private static final int MAX_QUALIFIER_TOKENS = 2;

    private static final Set<String> TOOL_ARMOR_NOUNS = Set.of(
            "SWORD", "AXE", "PICKAXE", "SHOVEL", "HOE", "SPEAR", "HELMET", "CHESTPLATE",
            "LEGGINGS", "BOOTS", "NAUTILUS_ARMOR"
    );

    /** Qualifiers that rotate only when the remaining base tokens satisfy a guard. */
    private static final Map<String, Predicate<List<String>>> GUARDED_QUALIFIERS = Map.ofEntries(
            // Tier tokens rotate only ahead of a tool/armor noun (NETHERITE_SWORD -> SWORD_NETHERITE),
            // never ahead of a raw material noun (IRON_INGOT, STONE_BRICKS stay put).
            Map.entry("WOODEN", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("STONE", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("IRON", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("GOLDEN", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("DIAMOND", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("NETHERITE", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("COPPER", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("CHAINMAIL", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("LEATHER", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            Map.entry("TURTLE", remainder -> TOOL_ARMOR_NOUNS.contains(join(remainder))),
            // Ore hosts rotate only ahead of an "*_ORE" remainder (DEEPSLATE_COAL_ORE ->
            // COAL_ORE_DEEPSLATE), never ahead of e.g. DEEPSLATE_BRICKS or NETHER_STAR.
            Map.entry("DEEPSLATE", remainder -> remainder.get(remainder.size() - 1).equals("ORE")),
            Map.entry("NETHER", remainder -> remainder.get(remainder.size() - 1).equals("ORE")),
            // REDSTONE_TORCH -> TORCH_REDSTONE, but REDSTONE_BLOCK/_ORE/_LAMP stay put.
            Map.entry("REDSTONE", remainder -> remainder.equals(List.of("TORCH"))),
            // CHAIN_COMMAND_BLOCK -> COMMAND_BLOCK_CHAIN, but plain CHAIN stays put.
            Map.entry("CHAIN", remainder -> remainder.equals(List.of("COMMAND", "BLOCK"))),
            // Coral species rotate only ahead of CORAL/CORAL_BLOCK/CORAL_FAN (FIRE_CORAL ->
            // CORAL_FIRE), never ahead of an unrelated word (FIRE_CHARGE stays put).
            Map.entry("BRAIN", remainder -> remainder.get(0).equals("CORAL")),
            Map.entry("BUBBLE", remainder -> remainder.get(0).equals("CORAL")),
            Map.entry("FIRE", remainder -> remainder.get(0).equals("CORAL")),
            Map.entry("HORN", remainder -> remainder.get(0).equals("CORAL")),
            Map.entry("TUBE", remainder -> remainder.get(0).equals("CORAL"))
    );

    /** Hand-fixes for anything the rule tables above get wrong; checked before both phases. */
    private static final Map<String, String> OVERRIDES = Map.of();

    // -------------------------------------------------------------------------------------------
    // Transform
    // -------------------------------------------------------------------------------------------

    static String transform(String name) {
        String override = OVERRIDES.get(name);
        if (override != null) {
            return override;
        }

        List<String> tokens = new ArrayList<>(Arrays.asList(name.split("_")));
        tokens = promoteSuffixFamily(tokens);
        tokens = rotateLeadingQualifiers(tokens);
        return join(tokens);
    }

    private static List<String> promoteSuffixFamily(List<String> tokens) {
        for (List<String> family : SUFFIX_FAMILIES) {
            if (tokens.size() > family.size() && endsWith(tokens, family)) {
                List<String> promoted = new ArrayList<>(family);
                promoted.addAll(tokens.subList(0, tokens.size() - family.size()));
                return promoted;
            }
        }
        return tokens;
    }

    private static boolean endsWith(List<String> tokens, List<String> suffix) {
        int offset = tokens.size() - suffix.size();
        return tokens.subList(offset, tokens.size()).equals(suffix);
    }

    private static List<String> rotateLeadingQualifiers(List<String> tokens) {
        List<List<String>> groups = new ArrayList<>();
        int pos = 0;
        outer:
        while (pos < tokens.size()) {
            for (int len = Math.min(MAX_QUALIFIER_TOKENS, tokens.size() - pos); len >= 1; len--) {
                List<String> candidate = tokens.subList(pos, pos + len);
                String key = join(candidate);
                List<String> remainder = tokens.subList(pos + len, tokens.size());
                if (remainder.isEmpty()) {
                    // Never consume the last remaining token(s) — a qualifier always needs a base.
                    continue;
                }
                if (UNCONDITIONAL_QUALIFIERS.contains(key)) {
                    groups.add(new ArrayList<>(candidate));
                    pos += len;
                    continue outer;
                }
                Predicate<List<String>> guard = GUARDED_QUALIFIERS.get(key);
                if (guard != null && guard.test(remainder)) {
                    groups.add(new ArrayList<>(candidate));
                    pos += len;
                    continue outer;
                }
            }
            break;
        }

        List<String> result = new ArrayList<>(tokens.subList(pos, tokens.size()));
        for (int i = groups.size() - 1; i >= 0; i--) {
            result.addAll(groups.get(i));
        }
        return result;
    }

    private static String join(List<String> tokens) {
        return String.join("_", tokens);
    }

    // -------------------------------------------------------------------------------------------
    // Material name sourcing — field names only, via reflection. No Material/ItemType constant
    // is ever invoked or initialized, so this runs standalone with no Bukkit server present.
    // -------------------------------------------------------------------------------------------

    /**
     * Loads {@code className} WITHOUT running its static initializer (the 3-arg
     * {@link Class#forName(String, boolean, ClassLoader)} form) and returns the names of its
     * public fields assignable to {@code assignableTo}. Both {@code Material} and
     * {@code ItemType} eagerly resolve live server registries in their {@code <clinit>} — {@code
     * Class.forName(name)} alone would trigger that and blow up with no server running — but
     * reflective field-metadata queries (unlike reading a field's value) don't require
     * initialization, so this is safe standalone.
     */
    private static Set<String> fieldNames(String className) throws ClassNotFoundException {
        ClassLoader loader = ItemNameKeyGenerator.class.getClassLoader();
        Class<?> clazz = Class.forName(className, false, loader);
        Set<String> names = new TreeSet<>();
        for (Field field : clazz.getFields()) {
            if (clazz.isAssignableFrom(field.getType())) {
                names.add(field.getName());
            }
        }
        return names;
    }

    private static List<String> collectMaterialNames() throws ClassNotFoundException {
        Set<String> itemTypeNames = fieldNames("org.bukkit.inventory.ItemType");
        Set<String> materialNames = fieldNames("org.bukkit.Material");
        List<String> both = new ArrayList<>(itemTypeNames);
        both.retainAll(materialNames);
        both.sort(String::compareTo);
        return both;
    }

    // -------------------------------------------------------------------------------------------
    // main
    // -------------------------------------------------------------------------------------------

    public static void main(String[] args) throws Exception {
        Path outputPath = Path.of(args.length > 0 ? args[0] : "tools/items.generated.yml");
        String paperApiVersion = args.length > 1 ? args[1] : paperApiVersion();

        List<String> materials = collectMaterialNames();

        Map<String, String> changed = new TreeMap<>();
        List<String> unchanged = new ArrayList<>();
        Map<String, List<String>> valuesToKeys = new LinkedHashMap<>();

        for (String name : materials) {
            String value = transform(name);
            if (value.equals(name)) {
                unchanged.add(name);
            } else {
                changed.put(name, value);
                valuesToKeys.computeIfAbsent(value, k -> new ArrayList<>()).add(name);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Generated by ItemNameKeyGenerator (tools/) against paper-api ")
                .append(paperApiVersion)
                .append(".\n");
        sb.append("# Do not hand-edit — regenerate via `./gradlew generateItemNames`.\n");
        sb.append("# See tools/ItemNameKeyGenerator.java for the rule tables.\n");
        for (Map.Entry<String, String> entry : changed.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append('\n');
        }
        Files.writeString(outputPath, sb.toString(), StandardCharsets.UTF_8);

        System.out.println("Materials examined: " + materials.size());
        System.out.println("Changed (written):  " + changed.size());
        System.out.println("Unchanged:          " + unchanged.size());
        System.out.println("Output:             " + outputPath.toAbsolutePath());

        List<Map.Entry<String, List<String>>> duplicates = valuesToKeys.entrySet().stream()
                .filter(e -> e.getValue().size() > 1)
                .toList();
        if (!duplicates.isEmpty()) {
            System.out.println();
            System.out.println("WARNING: " + duplicates.size() + " output value(s) produced by more than one key:");
            for (Map.Entry<String, List<String>> dup : duplicates) {
                System.out.println("  " + dup.getKey() + " <- " + dup.getValue());
            }
        }

        System.out.println();
        System.out.println("Unchanged materials (" + unchanged.size() + ") — review for missed variant families:");
        for (String name : unchanged) {
            System.out.println("  " + name);
        }
    }

    private static String paperApiVersion() {
        String version = org.bukkit.Material.class.getPackage().getImplementationVersion();
        return version == null ? "unknown" : version;
    }
}
