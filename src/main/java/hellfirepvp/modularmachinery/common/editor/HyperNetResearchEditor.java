/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Research definitions only; never modifies database progress or grants a completed research. */
public final class HyperNetResearchEditor {
    private HyperNetResearchEditor(){}
    public static Path directory(){return ModDataHolder.INSTANCE.getMachineryDirectory().toPath().getParent().resolve("research");}
    public static String revision(){var c=HyperNetResearchRegistry.loadedCatalog();return c==null?"unavailable":c.revision();}
    public static void save(String originalId,String revision,JsonObject input)throws java.io.IOException {
        var loaded=HyperNetResearchRegistry.loadedCatalog();
        if(loaded==null)throw new IllegalArgumentException("研究目录加载失败，请先修复文件并重载");
        if(!loaded.revision().equals(revision))throw new IllegalArgumentException("研究目录已变化，请重新打开研究列表");
        var candidate=HyperNetResearch.parse(input);
        if(!originalId.isEmpty()&&!originalId.equals(candidate.id()))throw new IllegalArgumentException("已有研究 ID 不可更改，以免破坏成果和配方引用");
        var disk=HyperNetResearchRegistry.readCatalog(directory());
        if(!disk.revision().equals(loaded.revision()))throw new IllegalArgumentException("研究源文件已变化，请重载后重新编辑");
        boolean create=originalId.isEmpty();
        if(create&&disk.definitions().containsKey(candidate.id()))throw new IllegalArgumentException("研究 ID 已存在");
        if(!create&&!disk.definitions().containsKey(originalId))throw new IllegalArgumentException("研究已不存在");
        var values=new LinkedHashMap<>(disk.definitions());values.put(candidate.id(),candidate);
        HyperNetResearchRegistry.validate(values.values()); // Validate the entire candidate graph BEFORE touching files.
        Path root=directory().toRealPath(),file;
        if(create)file=root.resolve("research-"+EditorSaveState.revision(candidate.id())+".json");
        else file=disk.files().get(originalId);
        if(!file.toAbsolutePath().normalize().startsWith(root))throw new IllegalArgumentException("研究来源只读");
        if(create&&Files.exists(file))throw new IllegalArgumentException("目标研究文件已存在");
        byte[] bytes=new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(candidate.json()).getBytes(StandardCharsets.UTF_8);
        if(bytes.length>256*1024)throw new IllegalArgumentException("研究文件超过 256 KiB");
        var temp=Files.createTempFile(root,".research-",".mmc.tmp");
        try {
            Files.write(temp,bytes);
            // Recheck the complete disk batch immediately before replacing the source.
            if(!HyperNetResearchRegistry.readCatalog(root).revision().equals(disk.revision()))throw new IllegalArgumentException("研究文件在保存期间发生变化");
            if(create)Files.move(temp,file);
            else try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
                 catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}
        HyperNetResearchRegistry.reload(root);
        if(!candidate.equals(HyperNetResearchRegistry.find(candidate.id())))throw new IllegalStateException("文件已保存，但研究重新加载失败，请检查服务端日志");
    }
}
