package com.foliagui.util;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Objects;

public final class ItemStackSerializer {

    private static final int MAX_ITEMS = 4096;
    private static final int MAX_BYTES = 16 * 1024 * 1024;

    private ItemStackSerializer() {
    }

    private static byte[] decode(String encoded) {
        if (encoded.length() > ((long) MAX_BYTES + 2) / 3 * 4) {
            throw new IllegalArgumentException("Inventory data exceeds byte limit");
        }
        byte[] bytes = Base64.getDecoder().decode(encoded);
        if (bytes.length > MAX_BYTES) {
            throw new IllegalArgumentException("Inventory data exceeds byte limit");
        }
        return bytes;
    }

    private static int count(int count) throws IOException {
        if (count < 0 || count > MAX_ITEMS) {
            throw new IOException("Inventory count must be between 0 and " + MAX_ITEMS);
        }
        return count;
    }

    public static @NotNull String toBase64(@Nullable ItemStack @NotNull [] contents) {
        Objects.requireNonNull(contents, "contents cannot be null");
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataStream = new BukkitObjectOutputStream(byteStream)) {
            dataStream.writeInt(contents.length);
            for (ItemStack stack : contents) {
                dataStream.writeObject(stack);
            }
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialise ItemStack[] to base64", e);
        }
    }

    public static @Nullable ItemStack @NotNull [] fromBase64(@NotNull String base64) {
        Objects.requireNonNull(base64, "base64 cannot be null");
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(decode(base64));
             BukkitObjectInputStream dataStream = new BukkitObjectInputStream(byteStream)) {
            dataStream.setObjectInputFilter(info -> info.depth() > 32 || info.references() > 100000 || info.arrayLength() > MAX_BYTES
                    ? java.io.ObjectInputFilter.Status.REJECTED : java.io.ObjectInputFilter.Status.UNDECIDED);
            int length = count(dataStream.readInt());
            ItemStack[] contents = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                Object value = dataStream.readObject();
                if (value != null && !(value instanceof ItemStack)) {
                    throw new IOException("Unexpected inventory object type");
                }
                contents[i] = (ItemStack) value;
            }
            if (dataStream.read() != -1) {
                throw new IOException("Trailing inventory data");
            }
            return contents;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Failed to deserialise ItemStack[] from base64", e);
        }
    }

    public static @NotNull String toBase64Compact(@Nullable ItemStack @NotNull [] contents) {
        Objects.requireNonNull(contents, "contents cannot be null");
        try (ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
             DataOutputStream dataStream = new DataOutputStream(byteStream)) {
            dataStream.writeInt(contents.length);
            for (ItemStack stack : contents) {
                if (stack == null) {
                    dataStream.writeInt(-1);
                } else {
                    byte[] bytes = stack.serializeAsBytes();
                    dataStream.writeInt(bytes.length);
                    dataStream.write(bytes);
                }
            }
            return Base64.getEncoder().encodeToString(byteStream.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to serialise ItemStack[] to base64", e);
        }
    }

    public static @Nullable ItemStack @NotNull [] fromBase64Compact(@NotNull String base64) {
        Objects.requireNonNull(base64, "base64 cannot be null");
        try (ByteArrayInputStream byteStream = new ByteArrayInputStream(decode(base64));
             DataInputStream dataStream = new DataInputStream(byteStream)) {
            int length = count(dataStream.readInt());
            ItemStack[] contents = new ItemStack[length];
            for (int i = 0; i < length; i++) {
                int size = dataStream.readInt();
                if (size == -1) {
                    contents[i] = null;
                } else {
                    if (size <= 0 || size > MAX_BYTES || size > dataStream.available()) {
                        throw new IOException("Invalid item byte length");
                    }
                    byte[] bytes = new byte[size];
                    dataStream.readFully(bytes);
                    contents[i] = ItemStack.deserializeBytes(bytes);
                }
            }
            if (dataStream.read() != -1) {
                throw new IOException("Trailing inventory data");
            }
            return contents;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to deserialise ItemStack[] from base64", e);
        }
    }
}
