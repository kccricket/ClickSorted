package net.kccricket.clicksorted.selftest;

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

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A compact slot-indexed text form for staging and asserting self-test inventories, e.g.
 * {@code "a:64 a:32 b:10 . c:1 ."}. Each token is {@code <key>:<amount>} (amount defaults to 1),
 * {@code .} for an empty slot, or a nested bundle {@code B[a:20 b:10]:1}. {@code key} indexes a
 * per-case {@link Map} of prototype {@link ItemStack}s (the "palette"); every prototype is cloned
 * and given the requested amount.
 *
 * <p>Used for both staging (the layout a case writes into the inventory before the click) and
 * goldens (the exact expected layout after it) — the same parser guarantees both are interpreted
 * identically.
 */
public final class Layout {

    private Layout() {
    }

    /**
     * Parses {@code spec} into an {@code ItemStack[size]}; slots beyond the token count are left
     * {@code null} (empty).
     *
     * @throws IllegalArgumentException if there are more tokens than {@code size}, a key is not in
     *                                  {@code palette}, or a token is otherwise malformed
     */
    public static ItemStack[] parse(String spec, int size, Map<String, ItemStack> palette) {
        ItemStack[] out = new ItemStack[size];
        String trimmed = spec == null ? "" : spec.trim();
        if (trimmed.isEmpty()) {
            return out;
        }
        List<String> tokens = tokenize(trimmed);
        if (tokens.size() > size) {
            throw new IllegalArgumentException("Layout has " + tokens.size() + " tokens but only "
                    + size + " slots: " + spec);
        }
        for (int i = 0; i < tokens.size(); i++) {
            out[i] = parseToken(tokens.get(i), palette);
        }
        return out;
    }

    /**
     * Splits on top-level whitespace only — whitespace inside a {@code B[...]} nested-bundle token
     * is preserved so bundle contents can list more than one item.
     */
    private static List<String> tokenize(String spec) {
        List<String> tokens = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < spec.length(); i++) {
            char c = spec.charAt(i);
            if (c == '[') depth++;
            if (c == ']') depth--;
            boolean isSpace = Character.isWhitespace(c) && depth == 0;
            if (isSpace) {
                if (start >= 0) {
                    tokens.add(spec.substring(start, i));
                    start = -1;
                }
            } else if (start < 0) {
                start = i;
            }
        }
        if (start >= 0) {
            tokens.add(spec.substring(start));
        }
        return tokens;
    }

    private static ItemStack parseToken(String token, Map<String, ItemStack> palette) {
        if (token.equals(".")) {
            return null;
        }
        if (token.startsWith("B[")) {
            int close = token.lastIndexOf(']');
            if (close < 0) {
                throw new IllegalArgumentException("Unterminated bundle token: " + token);
            }
            String inner = token.substring(2, close);
            String rest = token.substring(close + 1);
            int amount = rest.startsWith(":") ? Integer.parseInt(rest.substring(1)) : 1;

            ItemStack bundle = new ItemStack(Material.BUNDLE, amount);
            if (!(bundle.getItemMeta() instanceof BundleMeta meta)) {
                throw new IllegalStateException("BUNDLE has no BundleMeta on this server");
            }
            List<ItemStack> contents = new ArrayList<>();
            for (String part : tokenize(inner)) {
                ItemStack item = parseToken(part, palette);
                if (item != null) {
                    contents.add(item);
                }
            }
            meta.setItems(contents);
            bundle.setItemMeta(meta);
            return bundle;
        }

        int colon = token.indexOf(':');
        String key = colon >= 0 ? token.substring(0, colon) : token;
        int amount = colon >= 0 ? Integer.parseInt(token.substring(colon + 1)) : 1;

        ItemStack proto = palette.get(key);
        if (proto == null) {
            throw new IllegalArgumentException("Unknown palette key '" + key + "' in token: " + token);
        }
        ItemStack stack = proto.clone();
        stack.setAmount(amount);
        return stack;
    }

    /** Renders {@code items} back into the compact form, for failure messages. */
    public static String render(ItemStack[] items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(renderOne(items[i]));
        }
        return sb.toString();
    }

    private static String renderOne(ItemStack is) {
        if (is == null || is.getType() == Material.AIR) {
            return ".";
        }
        if (is.getItemMeta() instanceof BundleMeta meta && Material.BUNDLE == is.getType()) {
            StringBuilder inner = new StringBuilder("B[");
            List<ItemStack> contents = meta.getItems();
            for (int i = 0; i < contents.size(); i++) {
                if (i > 0) inner.append(' ');
                inner.append(renderOne(contents.get(i)));
            }
            inner.append(']').append(':').append(is.getAmount());
            return inner.toString();
        }
        String name = is.hasItemMeta() && is.getItemMeta().hasDisplayName()
                ? net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                        .serialize(is.getItemMeta().displayName())
                : is.getType().name();
        return name + ":" + is.getAmount();
    }
}
