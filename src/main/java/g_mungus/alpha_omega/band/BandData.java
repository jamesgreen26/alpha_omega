package g_mungus.alpha_omega.band;

import g_mungus.alpha_omega.AlphaOmegaMod;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import java.util.Arrays;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * A linked chunk's band data, saved with the chunk as a NeoForge data attachment (RS §3.1, §3.7):
 *
 * <ul>
 * <li><b>Flips</b>, a bitset per section over its 4096 cells: in a tile chunk a set bit means the cell is owned by a
 * copy instead; in a band or skirt chunk it means this copy owns the cell. Written in both copies. Most sections have
 * none, and cost a null.</li>
 * <li><b>Stamps</b>, one per link: bumped on every mirrored write between the two chunks, in both, so after a crash
 * between two saves the newer chunk is known.</li>
 * </ul>
 */
public final class BandData {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, AlphaOmegaMod.MOD_ID);
    public static final Supplier<AttachmentType<BandData>> TYPE = ATTACHMENTS.register("band",
        () -> AttachmentType.builder(BandData::new).serialize(new Serializer()).build());

    /** Per section index, 64 longs (a bit per cell, index {@code y << 8 | z << 4 | x}), or null for none. */
    private long[][] flips = new long[0][];
    private final Long2LongOpenHashMap stamps = new Long2LongOpenHashMap();

    public static void register(IEventBus modBus) {
        ATTACHMENTS.register(modBus);
    }

    public static int cell(int x, int y, int z) {
        return (y & 15) << 8 | (z & 15) << 4 | (x & 15);
    }

    // ---- Flips ----

    public boolean hasFlips(int section) {
        return section >= 0 && section < this.flips.length && this.flips[section] != null;
    }

    public boolean flip(int section, int cell) {
        if (section < 0 || section >= this.flips.length) return false;
        long[] bits = this.flips[section];
        return bits != null && (bits[cell >> 6] & 1L << (cell & 63)) != 0;
    }

    /** Sets or clears a bit; returns whether it changed. */
    public boolean setFlip(int section, int cell, boolean value) {
        if (section < 0) return false;
        if (section >= this.flips.length) {
            if (!value) return false;
            this.flips = Arrays.copyOf(this.flips, section + 1);
        }
        long[] bits = this.flips[section];
        if (bits == null) {
            if (!value) return false;
            bits = this.flips[section] = new long[64];
        }
        long mask = 1L << (cell & 63);
        boolean was = (bits[cell >> 6] & mask) != 0;
        if (was == value) return false;
        if (value) {
            bits[cell >> 6] |= mask;
        } else {
            bits[cell >> 6] &= ~mask;
            boolean any = false;
            for (long word : bits) any |= word != 0;
            if (!any) this.flips[section] = null;
        }
        return true;
    }

    public int sections() {
        return this.flips.length;
    }

    public long flipCount() {
        long count = 0;
        for (long[] bits : this.flips) {
            if (bits != null) for (long word : bits) count += Long.bitCount(word);
        }
        return count;
    }

    // ---- Stamps ----

    public long stamp(long link) {
        return this.stamps.get(link);
    }

    public void setStamp(long link, long stamp) {
        this.stamps.put(link, stamp);
    }

    public void bump(long link) {
        this.stamps.addTo(link, 1);
    }

    // ---- Saving ----

    private static final class Serializer implements IAttachmentSerializer<CompoundTag, BandData> {

        @Override
        public BandData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider provider) {
            BandData data = new BandData();
            ListTag flips = tag.getList("flips", Tag.TAG_COMPOUND);
            for (int i = 0; i < flips.size(); i++) {
                CompoundTag entry = flips.getCompound(i);
                int section = entry.getInt("section");
                long[] bits = entry.getLongArray("bits");
                if (section < 0 || bits.length != 64) continue;
                if (section >= data.flips.length) data.flips = Arrays.copyOf(data.flips, section + 1);
                data.flips[section] = bits;
            }
            ListTag stamps = tag.getList("stamps", Tag.TAG_COMPOUND);
            for (int i = 0; i < stamps.size(); i++) {
                CompoundTag entry = stamps.getCompound(i);
                data.stamps.put(entry.getLong("chunk"), entry.getLong("stamp"));
            }
            return data;
        }

        @Override
        @Nullable
        public CompoundTag write(BandData data, HolderLookup.Provider provider) {
            ListTag flips = new ListTag();
            for (int section = 0; section < data.flips.length; section++) {
                if (data.flips[section] == null) continue;
                CompoundTag entry = new CompoundTag();
                entry.putInt("section", section);
                entry.put("bits", new LongArrayTag(data.flips[section].clone()));
                flips.add(entry);
            }
            ListTag stamps = new ListTag();
            for (Long2LongMap.Entry stamp : data.stamps.long2LongEntrySet()) {
                CompoundTag entry = new CompoundTag();
                entry.putLong("chunk", stamp.getLongKey());
                entry.putLong("stamp", stamp.getLongValue());
                stamps.add(entry);
            }
            if (flips.isEmpty() && stamps.isEmpty()) return null;
            CompoundTag tag = new CompoundTag();
            tag.put("flips", flips);
            tag.put("stamps", stamps);
            return tag;
        }
    }
}
