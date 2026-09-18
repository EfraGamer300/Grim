package ac.grim.grimac.wolf;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WolfNetwork Edition: protocolo de checagem de mods do cliente.
 *
 * <p>Canal: {@code wolfnetwork:settings} (mesmo do WolfPlugin/WolfMOD).
 * Payload: duas strings com prefixo VarInt (formato PacketByteBuf.writeString):
 * key + value.</p>
 *
 * <p>Fluxo:
 * servidor --(wolfac_checkmods, requestId)--> cliente
 * cliente --(wolfac_mods, requestId|mod1:ver,mod2:ver,...)--> servidor</p>
 */
public final class WolfModCheckManager {
    public static final String CHANNEL = "wolfnetwork:settings";
    public static final String REQUEST_KEY = "wolfac_checkmods";
    public static final String RESPONSE_KEY = "wolfac_mods";

    public record PendingCheck(UUID requestId, UUID targetUuid, String targetName,
                               String requesterName, UUID requesterUuid, long timestampMillis) {
    }

    public record ModListResult(UUID targetUuid, String targetName, String modListRaw,
                                int modCount, long receivedMillis, long pingMillis) {
    }

    private static final Map<UUID, PendingCheck> PENDING_BY_TARGET = new ConcurrentHashMap<>();
    private static final Map<UUID, PendingCheck> PENDING_BY_REQUEST = new ConcurrentHashMap<>();
    private static final Map<UUID, ModListResult> LAST_RESULTS = new ConcurrentHashMap<>();

    private WolfModCheckManager() {
    }

    public static PendingCheck registerRequest(UUID targetUuid, String targetName,
                                               String requesterName, UUID requesterUuid) {
        UUID requestId = UUID.randomUUID();
        PendingCheck pending = new PendingCheck(requestId, targetUuid, targetName,
                requesterName, requesterUuid, System.currentTimeMillis());
        PENDING_BY_TARGET.put(targetUuid, pending);
        PENDING_BY_REQUEST.put(requestId, pending);
        return pending;
    }

    public static PendingCheck takeByRequestId(UUID requestId) {
        PendingCheck pending = PENDING_BY_REQUEST.remove(requestId);
        if (pending != null) {
            PENDING_BY_TARGET.remove(pending.targetUuid(), pending);
        }
        return pending;
    }

    public static PendingCheck getPendingForTarget(UUID targetUuid) {
        return PENDING_BY_TARGET.get(targetUuid);
    }

    public static void timeoutIfCurrent(UUID requestId) {
        PendingCheck pending = PENDING_BY_REQUEST.remove(requestId);
        if (pending != null) {
            PENDING_BY_TARGET.remove(pending.targetUuid(), pending);
        }
    }

    public static void storeResult(ModListResult result) {
        LAST_RESULTS.put(result.targetUuid(), result);
    }

    public static ModListResult getLastResult(UUID targetUuid) {
        return LAST_RESULTS.get(targetUuid);
    }

    public static void clearForQuit(UUID uuid) {
        PENDING_BY_TARGET.remove(uuid);
        LAST_RESULTS.remove(uuid);
    }

    public static byte[] encode(String key, String value) {
        try {
            byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
            byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
            ByteArrayOutputStream baos = new ByteArrayOutputStream(keyBytes.length + valueBytes.length + 10);
            DataOutputStream out = new DataOutputStream(baos);
            writeVarInt(out, keyBytes.length);
            out.write(keyBytes);
            writeVarInt(out, valueBytes.length);
            out.write(valueBytes);
            out.flush();
            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("WolfAC: falha ao codificar payload", e);
        }
    }

    public record Decoded(String key, String value) {
    }

    public static Decoded decode(byte[] data) {
        try {
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(data));
            int keyLen = readVarInt(in);
            if (keyLen < 0 || keyLen > 32767) throw new IOException("key len invalido: " + keyLen);
            byte[] keyBytes = new byte[keyLen];
            in.readFully(keyBytes);
            int valueLen = readVarInt(in);
            if (valueLen < 0 || valueLen > 1048576) throw new IOException("value len invalido: " + valueLen);
            byte[] valueBytes = new byte[valueLen];
            if (valueBytes.length > 0) in.readFully(valueBytes);
            return new Decoded(new String(keyBytes, StandardCharsets.UTF_8),
                    new String(valueBytes, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException("WolfAC: falha ao decodificar payload", e);
        }
    }

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & 0xFFFFFF80) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = in.readByte();
            value |= (current & 0x7F) << position;
            position += 7;
            if (position >= 32) throw new IOException("VarInt muito grande");
        } while ((current & 0x80) != 0);
        return value;
    }
}
