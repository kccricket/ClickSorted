package me.desht.dhutils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public class DHUtilsException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final Component componentMessage;

    public DHUtilsException(String message) {
        super(message);
        this.componentMessage = Component.text(message);
    }

    public DHUtilsException(Component message) {
        super(PlainTextComponentSerializer.plainText().serialize(message));
        this.componentMessage = message;
    }

    public Component getComponentMessage() {
        return componentMessage;
    }
}
