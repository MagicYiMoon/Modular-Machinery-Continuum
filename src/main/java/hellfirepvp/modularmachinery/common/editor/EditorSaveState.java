/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

/** Single-flight editor writes. Success is ready only after its authoritative snapshot arrives. */
public final class EditorSaveState {
    public record Result(UUID requestId, boolean success, String message) {}
    private UUID pending;
    private UUID snapshot;
    private Result result;

    public UUID begin() {
        if (pending != null) throw new IllegalStateException("已有编辑请求正在保存");
        pending = UUID.randomUUID();
        snapshot = null;
        result = null;
        return pending;
    }
    public boolean pending() { return pending != null; }
    public boolean matches(UUID id) { return id.equals(pending); }
    public void snapshotReceived(UUID id) { if (matches(id)) snapshot = id; }
    public void receive(Result incoming) { if (result == null && matches(incoming.requestId())) result = incoming; }
    public Result takeReady() {
        if (result == null || result.success() && !result.requestId().equals(snapshot)) return null;
        Result ready = result;
        pending = null;
        snapshot = null;
        result = null;
        return ready;
    }
    public static String revision(String document) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
