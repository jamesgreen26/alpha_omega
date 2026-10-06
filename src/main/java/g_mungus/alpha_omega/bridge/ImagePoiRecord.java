package g_mungus.alpha_omega.bridge;

import g_mungus.alpha_omega.mixin.bridges.PoiRecordInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

/**
 * A POI record seen through an image ({@link PoiBridge}): the owner's record, at the copy of its cell in the querier's
 * frame. Everything but the position is the owner record's, so tickets taken or released through it are the owner's.
 * Never stored in a section.
 */
final class ImagePoiRecord extends PoiRecord {

    private final PoiRecord owner;

    ImagePoiRecord(PoiRecord owner, BlockPos at) {
        super(at, owner.getPoiType(), () -> {
        });
        this.owner = owner;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getFreeTickets() {
        return this.owner.getFreeTickets();
    }

    @Override
    protected boolean acquireTicket() {
        return ((PoiRecordInvoker) this.owner).alpha_omega$acquireTicket();
    }

    @Override
    protected boolean releaseTicket() {
        return ((PoiRecordInvoker) this.owner).alpha_omega$releaseTicket();
    }

    @Override
    public boolean hasSpace() {
        return this.owner.hasSpace();
    }

    @Override
    public boolean isOccupied() {
        return this.owner.isOccupied();
    }
}
