package codechicken.translocator.tile;

import codechicken.lib.data.MCDataInput;
import codechicken.lib.data.MCDataOutput;
import codechicken.lib.math.MathHelper;
import codechicken.lib.packet.ICustomPacketTile;
import codechicken.lib.packet.PacketCustom;
import codechicken.lib.raytracer.ICuboidProvider;
import codechicken.lib.raytracer.IndexedCuboid6;
import codechicken.lib.util.ItemUtils;
import codechicken.lib.vec.Cuboid6;
import codechicken.lib.vec.Vector3;
import codechicken.translocator.network.TranslocatorSPH;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import static codechicken.lib.vec.Rotation.sideRotations;
import static codechicken.lib.vec.Vector3.center;

@Deprecated
public abstract class TileTranslocator extends TileEntity implements ICustomPacketTile, ITickable, ICuboidProvider {
    public class Attachment {
        public final int side;

        public boolean a_eject;
        public boolean b_eject;

        public boolean redstone;
        public boolean invert_redstone;
        public boolean fast;

        public double a_insertpos;
        public double b_insertpos;

        public Attachment(int side) {
            this.side = side;
            a_eject = b_eject = invert_redstone = true;
            a_insertpos = b_insertpos = 1;
        }

        public void read(NBTTagCompound tag) {
            invert_redstone = tag.getBoolean("invert_redstone");
            redstone = tag.getBoolean("redstone");
            fast = tag.getBoolean("fast");
        }

        public void update(boolean client) {
            b_insertpos = a_insertpos;
            a_insertpos = MathHelper.approachExp(a_insertpos, approachInsertPos(), 0.5, 0.1);

            if (!client) {
                b_eject = a_eject;
                a_eject = (redstone && gettingPowered()) != invert_redstone;
                if (a_eject != b_eject) {
                    markUpdate();
                }
            }
        }

        public double approachInsertPos() {
            return a_eject ? 1 : 0;
        }

        public void write(MCDataOutput packet) {
            packet.writeBoolean(a_eject);
            packet.writeBoolean(redstone);
            packet.writeBoolean(fast);
        }

        public void read(MCDataInput packet, boolean described) {
            a_eject = packet.readBoolean();
            redstone = packet.readBoolean();
            fast = packet.readBoolean();

            if (!described) {
                a_insertpos = b_insertpos = approachInsertPos();
            }
        }

        public NBTTagCompound write(NBTTagCompound tag) {
            tag.setBoolean("invert_redstone", invert_redstone);
            tag.setBoolean("redstone", redstone);
            tag.setBoolean("fast", fast);
            return tag;
        }

        public boolean activate(EntityPlayer player, int subPart) {
            ItemStack held = player.inventory.getCurrentItem();
            if (held.isEmpty() && player.isSneaking()) {
                stripModifiers();
                markUpdate();
            } else if (held.isEmpty()) {
                if (subPart == 1) {
                    invert_redstone = !invert_redstone;
                } else {
                    openGui(player);
                }
            } else if (held.getItem() == Items.REDSTONE && !redstone) {
                redstone = true;
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }

                if ((gettingPowered() != invert_redstone) != a_eject) {
                    invert_redstone = !invert_redstone;
                }
                markUpdate();
            } else if (held.getItem() == Items.GLOWSTONE_DUST && !fast) {
                fast = true;
                if (!player.capabilities.isCreativeMode) {
                    held.shrink(1);
                }
                markUpdate();
            } else {
                openGui(player);
            }

            return true;
        }

        public void stripModifiers() {
            if (redstone) {
                redstone = false;
                dropItem(new ItemStack(Items.REDSTONE));

                if (invert_redstone != a_eject) {
                    invert_redstone = !invert_redstone;
                }
            }
            if (fast) {
                fast = false;
                dropItem(new ItemStack(Items.GLOWSTONE_DUST));
            }
        }

        public void openGui(EntityPlayer player) {
        }

        public void markUpdate() {
            IBlockState state = world.getBlockState(getPos());
            world.notifyBlockUpdate(getPos(), state, state, 3);
            markDirty();
        }

        public Collection<ItemStack> getDrops(IBlockState state) {
            LinkedList<ItemStack> items = new LinkedList<>();
            items.add(new ItemStack(getBlockType(), 1, getBlockType().getMetaFromState(state)));
            if (redstone) {
                items.add(new ItemStack(Items.REDSTONE));
            }
            if (fast) {
                items.add(new ItemStack(Items.GLOWSTONE_DUST));
            }
            return items;
        }

        public int getIconIndex() {
            int i = 0;
            if (redstone) {
                i |= gettingPowered() ? 2 : 1;
            }
            if (fast) {
                i |= 4;
            }
            return i;
        }

        public boolean canConnectRedstone() {
            return redstone;
        }
    }

    public Attachment[] attachments = new Attachment[6];

    @Override
    public void update() {
        for (Attachment a : attachments) {
            if (a != null) {
                a.update(world.isRemote);
            }
        }
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        PacketCustom packet = new PacketCustom(TranslocatorSPH.channel, 1);
        writeToPacket(packet);

        return packet.toTilePacket(getPos());
    }

    @Override
    public NBTTagCompound getUpdateTag() {
        PacketCustom packet = new PacketCustom(TranslocatorSPH.channel, 1);
        writeToPacket(packet);
        return packet.toNBTTag(super.getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        readFromPacket(PacketCustom.fromTilePacket(pkt));
    }

    public void handlePacket(PacketCustom packetCustom) {
        readFromPacket(packetCustom);
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        readFromPacket(PacketCustom.fromNBTTag(tag));
    }

    @Override
    public void writeToPacket(MCDataOutput packet) {
        int attachmask = 0;
        for (int i = 0; i < 6; i++) {
            if (attachments[i] != null) {
                attachmask |= 1 << i;
            }
        }

        packet.writeByte(attachmask);
        for (Attachment a : attachments) {
            if (a != null) {
                a.write(packet);
            }
        }
    }

    @Override
    public void readFromPacket(MCDataInput packet) {
        int attachmask = packet.readUByte();
        for (int i = 0; i < 6; i++) {
            if ((attachmask & 1 << i) != 0) {
                boolean described = attachments[i] != null;
                if (!described) {
                    createAttachment(i);
                }
                attachments[i].read(packet, described);
            } else {
                attachments[i] = null;
            }
        }

        world.markBlockRangeForRenderUpdate(getPos(), getPos());
    }

    public void createAttachment(int side) {
        attachments[side] = new Attachment(side);
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        for (int i = 0; i < 6; i++) {
            if (attachments[i] != null) {
                tag.setTag("atmt" + i, attachments[i].write(new NBTTagCompound()));
            }
        }
        return tag;
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);

        for (int i = 0; i < 6; i++) {
            if (tag.hasKey("atmt" + i)) {
                createAttachment(i);
                attachments[i].read(tag.getCompoundTag("atmt" + i));
            }
        }
    }

    @Override
    public List<IndexedCuboid6> getIndexedCuboids() {
        ArrayList<IndexedCuboid6> cuboids = new ArrayList<>();
        addTraceableCuboids(cuboids);
        return cuboids;
    }

    public void addTraceableCuboids(List<IndexedCuboid6> cuboids) {

        Cuboid6 base = new Cuboid6(3 / 16D, 0, 3 / 16D, 13 / 16D, 2 / 16D, 13 / 16D);

        for (int i = 0; i < 6; i++) {
            Attachment a = attachments[i];
            if (a != null) {
                cuboids.add(new IndexedCuboid6(i, transformPart(base, i)));
                cuboids.add(new IndexedCuboid6(i + 6, transformPart(new Cuboid6(6 / 16D, 0, 6 / 16D, 10 / 16D, a.a_insertpos * 2 / 16D + 1 / 16D, 10 / 16D), i)));
            }
        }
    }

    private Cuboid6 transformPart(Cuboid6 box, int i) {
        return box.copy().apply(sideRotations[i].at(center));
    }

    @Override
    public AxisAlignedBB getRenderBoundingBox() {
        return new Cuboid6(getPos(), getPos().add(1, 1, 1)).aabb();
    }

    public boolean harvestPart(int i, boolean drop) {
        Attachment a = attachments[i];
        IBlockState state = world.getBlockState(getPos());
        if (!world.isRemote && drop) {
            for (ItemStack stack : a.getDrops(state)) {
                dropItem(stack);
            }
        }

        attachments[i] = null;

        world.notifyBlockUpdate(getPos(), state, state, 3);
        for (Attachment a1 : attachments) {
            if (a1 != null) {
                return false;
            }
        }

        world.setBlockToAir(getPos());
        return true;
    }

    public void dropItem(ItemStack stack) {
        ItemUtils.dropItem(stack, world, Vector3.fromTileCenter(this));
    }

    public boolean gettingPowered() {
        return world.isBlockPowered(getPos());
    }

    public boolean connectRedstone() {
        for (Attachment a : attachments) {
            if (a != null && a.canConnectRedstone()) {
                return true;
            }
        }
        return false;
    }

    public int strongPowerLevel(EnumFacing facing) {
        return 0;
    }
}
