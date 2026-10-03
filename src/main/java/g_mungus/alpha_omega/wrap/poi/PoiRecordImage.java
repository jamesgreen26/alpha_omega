package g_mungus.alpha_omega.wrap.poi;

import g_mungus.alpha_omega.mixin.server.PoiRecordAccessor;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

/** A stored POI record seen at another image: a shifted position, with all ticket state delegated to the original. */
public final class PoiRecordImage extends PoiRecord {

    private final PoiRecord original;

    public PoiRecordImage(PoiRecord original, int dx, int dz) {
        super(original.getPos().offset(dx, 0, dz), original.getPoiType(), () -> {});
        this.original = original;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getFreeTickets() {
        return this.original.getFreeTickets();
    }

    @Override
    protected boolean acquireTicket() {
        return ((PoiRecordAccessor) this.original).alpha_omega$acquireTicket();
    }

    @Override
    protected boolean releaseTicket() {
        return ((PoiRecordAccessor) this.original).alpha_omega$releaseTicket();
    }

    @Override
    public boolean hasSpace() {
        return this.original.hasSpace();
    }

    @Override
    public boolean isOccupied() {
        return this.original.isOccupied();
    }
}
