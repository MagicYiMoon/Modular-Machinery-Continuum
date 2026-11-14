/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.nio.charset.StandardCharsets;

/** Server-authoritative, slotless menu used by {@code /modularmachinery gui}. */
public final class MenuMachineEditor extends AbstractContainerMenu {
    public static final int MAX_SNAPSHOT_BYTES = 16 * 1024 * 1024;
    /** Placeholder sent in the vanilla open-menu packet; the real document uses the mod channel. */
    public static final String EMPTY_SNAPSHOT = "{\"machines\":[]}";
    private String snapshot;
    private java.util.UUID researchUpload;
    private byte[] researchUploadBytes;
    private int researchUploadOffset;
    private long researchUploadExpires;
    public byte[] acceptResearchUpload(java.util.UUID token,int total,int offset,byte[] part,long tick){
        if(researchUploadBytes!=null&&tick>researchUploadExpires){researchUploadBytes=null;researchUpload=null;}
        if(total<1||total>280*1024||part.length<1||part.length>16384||offset<0||offset>total-part.length)throw new IllegalArgumentException("Invalid research upload bounds");
        if(offset==0){researchUpload=token;researchUploadBytes=new byte[total];researchUploadOffset=0;researchUploadExpires=tick+200;}
        if(!token.equals(researchUpload)||researchUploadBytes==null||researchUploadBytes.length!=total||offset!=researchUploadOffset)throw new IllegalArgumentException("Expired or out-of-order research upload");
        System.arraycopy(part,0,researchUploadBytes,offset,part.length);researchUploadOffset+=part.length;
        if(researchUploadOffset!=total)return null;
        var complete=researchUploadBytes;researchUploadBytes=null;researchUpload=null;return complete;
    }
    private java.util.UUID researchQuery;
    private String researchPage;
    public void beginResearchQuery(java.util.UUID token){researchQuery=token;researchPage=null;}
    public void acceptResearchPage(java.util.UUID token,String page){if(token.equals(researchQuery))researchPage=page;}
    public String researchPage(){return researchPage;}
    public void clearResearchQuery(){researchQuery=null;researchPage=null;}
    private boolean initialSnapshotReceived;
    private final hellfirepvp.modularmachinery.common.editor.EditorSaveState saves = new hellfirepvp.modularmachinery.common.editor.EditorSaveState();
    private final java.util.LinkedHashSet<java.util.UUID> requests = new java.util.LinkedHashSet<>();

    /** Client constructor used by the registered menu type. */
    public MenuMachineEditor(int windowId, Inventory inventory, FriendlyByteBuf data) {
        this(windowId, inventory, readSnapshot(data));
    }

    public MenuMachineEditor(int windowId, Inventory inventory, String snapshot) {
        super(RegistryMenus.MACHINE_EDITOR.get(), windowId);
        this.snapshot = snapshot == null ? EMPTY_SNAPSHOT : snapshot;
    }

    private static String readSnapshot(FriendlyByteBuf data) {
        byte[] bytes = data.readByteArray(MAX_SNAPSHOT_BYTES);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeSnapshot(FriendlyByteBuf data, String snapshot) {
        byte[] bytes = (snapshot == null ? EMPTY_SNAPSHOT : snapshot)
                .getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SNAPSHOT_BYTES) throw new IllegalArgumentException("editor snapshot is too large");
        data.writeByteArray(bytes);
    }

    public String snapshot() { return snapshot; }
    public void setSnapshot(String snapshot) { this.snapshot = snapshot == null ? EMPTY_SNAPSHOT : snapshot; }
    public hellfirepvp.modularmachinery.common.editor.EditorSaveState saves() { return saves; }
    public boolean acceptRequest(java.util.UUID request) {
        if (!requests.add(request)) return false;
        if (requests.size() > 64) requests.remove(requests.iterator().next());
        return true;
    }
    public void acceptSnapshot(java.util.UUID transfer, String document) {
        if (saves.pending() ? !saves.matches(transfer) : initialSnapshotReceived) return;
        setSnapshot(document);
        initialSnapshotReceived = true;
        saves.snapshotReceived(transfer);
    }

    @Override public boolean stillValid(Player player) {
        return player != null && player.hasPermissions(2);
    }

    @Override public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }
}
