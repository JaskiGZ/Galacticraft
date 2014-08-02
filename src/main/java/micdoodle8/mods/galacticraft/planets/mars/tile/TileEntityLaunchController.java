package micdoodle8.mods.galacticraft.planets.mars.tile;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.relauncher.Side;
import micdoodle8.mods.galacticraft.api.entity.IDockable;
import micdoodle8.mods.galacticraft.api.prefab.entity.EntityAutoRocket;
import micdoodle8.mods.galacticraft.api.tile.ILandingPadAttachable;
import micdoodle8.mods.galacticraft.api.transmission.item.ItemElectric;
import micdoodle8.mods.galacticraft.core.GalacticraftCore;
import micdoodle8.mods.galacticraft.core.blocks.BlockLandingPadFull;
import micdoodle8.mods.galacticraft.core.blocks.GCBlocks;
import micdoodle8.mods.galacticraft.core.tile.ElectricBlockWithInventory;
import micdoodle8.mods.galacticraft.core.tile.TileEntityLandingPad;
import micdoodle8.mods.galacticraft.core.util.GCCoreUtil;
import micdoodle8.mods.galacticraft.core.world.ChunkLoadingCallback;
import micdoodle8.mods.galacticraft.core.world.IChunkLoader;
import micdoodle8.mods.galacticraft.planets.mars.ConfigManagerMars;
import micdoodle8.mods.galacticraft.planets.mars.network.PacketSimpleMars;
import micdoodle8.mods.galacticraft.planets.mars.network.PacketSimpleMars.EnumSimplePacketMars;
import micdoodle8.mods.miccore.Annotations.NetworkedField;
import net.minecraft.block.Block;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import java.util.ArrayList;
import java.util.List;

public class TileEntityLaunchController extends ElectricBlockWithInventory implements IChunkLoader, ISidedInventory, ILandingPadAttachable
{
	public static final int WATTS_PER_TICK = 1;
	private ItemStack[] containingItems = new ItemStack[1];
	@NetworkedField(targetSide = Side.CLIENT)
	public boolean launchPadRemovalDisabled = true;
	private Ticket chunkLoadTicket;
	private List<ChunkCoordinates> connectedPads = new ArrayList<ChunkCoordinates>();
	@NetworkedField(targetSide = Side.CLIENT)
	public int frequency = -1;
	@NetworkedField(targetSide = Side.CLIENT)
	public int destFrequency = -1;
	@NetworkedField(targetSide = Side.CLIENT)
	public String ownerName = "";
	@NetworkedField(targetSide = Side.CLIENT)
	public boolean frequencyValid;
	@NetworkedField(targetSide = Side.CLIENT)
	public boolean destFrequencyValid;
	@NetworkedField(targetSide = Side.CLIENT)
	public int launchDropdownSelection;
	@NetworkedField(targetSide = Side.CLIENT)
	public boolean launchSchedulingEnabled;
	public boolean requiresClientUpdate;
	public Object attachedDock = null;
	private boolean frequencyCheckNeeded = false;

	public TileEntityLaunchController()
	{
		this.storage.setMaxExtract(10);
	}

	@Override
	public void updateEntity()
	{
		super.updateEntity();

		if (!this.worldObj.isRemote)
		{
			if (this.frequencyCheckNeeded)
			{	
				this.checkDestFrequencyValid();
				this.frequencyCheckNeeded = false;
			}
			
			if (this.requiresClientUpdate)
			{
				// PacketDispatcher.sendPacketToAllPlayers(this.getPacket());
				// TODO
				this.requiresClientUpdate = false;
			}

			if (this.ticks % 40 == 0)
			{
				this.setFrequency(this.frequency);
				this.setDestinationFrequency(this.destFrequency);
			}

			if (this.ticks % 20 == 0)
			{
				if (this.chunkLoadTicket != null)
				{
					for (int i = 0; i < this.connectedPads.size(); i++)
					{
						ChunkCoordinates coords = this.connectedPads.get(i);
						Block block = this.worldObj.getBlock(coords.posX, coords.posY, coords.posZ);

						if (block != GCBlocks.landingPadFull)
						{
							this.connectedPads.remove(i);
							ForgeChunkManager.unforceChunk(this.chunkLoadTicket, new ChunkCoordIntPair(coords.posX >> 4, coords.posZ >> 4));
						}
					}
				}
			}
		}
		else
		{
			if (this.frequency == -1 && this.destFrequency == -1)
			{
				GalacticraftCore.packetPipeline.sendToServer(new PacketSimpleMars(EnumSimplePacketMars.S_UPDATE_ADVANCED_GUI, new Object[] { 5, this.xCoord, this.yCoord, this.zCoord, 0 }));
			}
		}
	}

	@Override
	public String getOwnerName()
	{
		return this.ownerName;
	}

	@Override
	public void setOwnerName(String ownerName)
	{
		this.ownerName = ownerName;
	}

	@Override
	public void invalidate()
	{
		super.invalidate();

		if (this.chunkLoadTicket != null)
		{
			ForgeChunkManager.releaseTicket(this.chunkLoadTicket);
		}
	}

	@Override
	public void onTicketLoaded(Ticket ticket, boolean placed)
	{
		if (!this.worldObj.isRemote && ConfigManagerMars.launchControllerChunkLoad)
		{
			if (ticket == null)
			{
				return;
			}

			if (this.chunkLoadTicket == null)
			{
				this.chunkLoadTicket = ticket;
			}

			NBTTagCompound nbt = this.chunkLoadTicket.getModData();
			nbt.setInteger("ChunkLoaderTileX", this.xCoord);
			nbt.setInteger("ChunkLoaderTileY", this.yCoord);
			nbt.setInteger("ChunkLoaderTileZ", this.zCoord);

			for (int x = -2; x <= 2; x++)
			{
				for (int z = -2; z <= 2; z++)
				{
					Block blockID = this.worldObj.getBlock(this.xCoord + x, this.yCoord, this.zCoord + z);

					if (blockID instanceof BlockLandingPadFull)
					{
						if (this.xCoord + x >> 4 != this.xCoord >> 4 || this.zCoord + z >> 4 != this.zCoord >> 4)
						{
							this.connectedPads.add(new ChunkCoordinates(this.xCoord + x, this.yCoord, this.zCoord + z));

							if (placed)
							{
								ChunkLoadingCallback.forceChunk(this.chunkLoadTicket, this.worldObj, this.xCoord + x, this.yCoord, this.zCoord + z, this.getOwnerName());
							}
							else
							{
								ChunkLoadingCallback.addToList(this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.getOwnerName());
							}
						}
					}
				}
			}

			ChunkLoadingCallback.forceChunk(this.chunkLoadTicket, this.worldObj, this.xCoord, this.yCoord, this.zCoord, this.getOwnerName());
		}
	}

	@Override
	public Ticket getTicket()
	{
		return this.chunkLoadTicket;
	}

	@Override
	public ChunkCoordinates getCoords()
	{
		return new ChunkCoordinates(this.xCoord, this.yCoord, this.zCoord);
	}

	@Override
	public void readFromNBT(NBTTagCompound nbt)
	{
		super.readFromNBT(nbt);
		this.containingItems = this.readStandardItemsFromNBT(nbt);

		this.ownerName = nbt.getString("OwnerName");
		this.launchDropdownSelection = nbt.getInteger("LaunchSelection");
		this.frequency = nbt.getInteger("ControllerFrequency");
		this.destFrequency = nbt.getInteger("TargetFrequency");
		this.frequencyCheckNeeded = true;
		this.launchPadRemovalDisabled = nbt.getBoolean("LaunchPadRemovalDisabled");
		this.launchSchedulingEnabled = nbt.getBoolean("LaunchPadSchedulingEnabled");
		this.requiresClientUpdate = true;
	}

	@Override
	public void writeToNBT(NBTTagCompound nbt)
	{
		super.writeToNBT(nbt);
		this.writeStandardItemsToNBT(nbt);
		nbt.setString("OwnerName", this.ownerName);
		nbt.setInteger("LaunchSelection", this.launchDropdownSelection);
		nbt.setInteger("ControllerFrequency", this.frequency);
		nbt.setInteger("TargetFrequency", this.destFrequency);
		nbt.setBoolean("LaunchPadRemovalDisabled", this.launchPadRemovalDisabled);
		nbt.setBoolean("LaunchPadSchedulingEnabled", this.launchSchedulingEnabled);
	}

	@Override
	public ItemStack[] getContainingItems()
	{
		return this.containingItems;
	}

	@Override
	public String getInventoryName()
	{
		return GCCoreUtil.translate("container.launchcontroller.name");
	}

	@Override
	public boolean hasCustomInventoryName()
	{
		return true;
	}

	@Override
	public boolean isItemValidForSlot(int slotID, ItemStack itemStack)
	{
		return slotID == 0 && ItemElectric.isElectricItem(itemStack.getItem());
	}

	@Override
	public int[] getAccessibleSlotsFromSide(int side)
	{
		return new int[] { 0 };
	}

	@Override
	public boolean canInsertItem(int slotID, ItemStack par2ItemStack, int par3)
	{
		return this.isItemValidForSlot(slotID, par2ItemStack);
	}

	@Override
	public boolean canExtractItem(int slotID, ItemStack par2ItemStack, int par3)
	{
		return slotID == 0;
	}

	@Override
	public boolean shouldUseEnergy()
	{
		return !this.getDisabled(0);
	}

	@Override
	public void setDisabled(int index, boolean disabled)
	{
		if (this.disableCooldown == 0)
		{
			switch (index)
			{
			case 0:
				this.disabled = disabled;
				this.disableCooldown = 10;
				break;
			case 1:
				this.launchSchedulingEnabled = disabled;
				break;
			}		
		}
	}

	@Override
	public boolean getDisabled(int index)
	{
		switch (index)
		{
		case 0:
			return this.disabled;
		case 1:
			return this.launchSchedulingEnabled;
		}

		return true;
	}

	@Override
	public boolean canAttachToLandingPad(IBlockAccess world, int x, int y, int z)
	{
		TileEntity tile = world.getTileEntity(x, y, z);

		return tile instanceof TileEntityLandingPad;
	}

	public void setFrequency(int frequency)
	{
		this.frequency = frequency;

		if (this.frequency >= 0)
		{
			this.frequencyValid = true;

			worldLoop:
			for (int i = 0; i < FMLCommonHandler.instance().getMinecraftServerInstance().worldServers.length; i++)
			{
				WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().worldServers[i];

				for (int j = 0; j < world.loadedTileEntityList.size(); j++)
				{
					TileEntity tile2 = (TileEntity) world.loadedTileEntityList.get(j);

					if (this != tile2)
					{
						tile2 = world.getTileEntity(tile2.xCoord, tile2.yCoord, tile2.zCoord);

						if (tile2 instanceof TileEntityLaunchController)
						{
							TileEntityLaunchController launchController2 = (TileEntityLaunchController) tile2;

							if (launchController2.frequency == this.frequency)
							{
								this.frequencyValid = false;
								break worldLoop;
							}
						}
					}
				}
			}
		}
		else
		{
			this.frequencyValid = false;
		}
	}

	public void setDestinationFrequency(int frequency)
	{
		if (frequency != this.destFrequency)
		{
			this.destFrequency = frequency;
			this.checkDestFrequencyValid();
			this.updateRocketOnDockSettings();
		}
	}
	
	private void checkDestFrequencyValid()
	{
		this.destFrequencyValid = false;
		if (this.destFrequency >= 0)
		{
			for (int i = 0; i < FMLCommonHandler.instance().getMinecraftServerInstance().worldServers.length; i++)
			{
				WorldServer world = FMLCommonHandler.instance().getMinecraftServerInstance().worldServers[i];

				for (int j = 0; j < world.loadedTileEntityList.size(); j++)
				{
					TileEntity tile2 = (TileEntity) world.loadedTileEntityList.get(j);

					if (this != tile2)
					{
						tile2 = world.getTileEntity(tile2.xCoord, tile2.yCoord, tile2.zCoord);

						if (tile2 instanceof TileEntityLaunchController)
						{
							TileEntityLaunchController launchController2 = (TileEntityLaunchController) tile2;

							if (launchController2.frequency == this.destFrequency)
							{
								this.destFrequencyValid = true;
								return;
							}
						}
					}
				}
			}
		}
	}

	public boolean validFrequency()
	{
		return !this.getDisabled(0) && this.hasEnoughEnergyToRun && this.frequencyValid && this.destFrequencyValid;
	}

	public void setLaunchDropdownSelection(int newvalue)
	{
		if (newvalue != this.launchDropdownSelection)
		{
			this.launchDropdownSelection = newvalue;
			this.checkDestFrequencyValid();
			this.updateRocketOnDockSettings();
		}
	}
	
	public void setLaunchSchedulingEnabled(boolean newvalue)
	{
		if (newvalue != this.launchSchedulingEnabled)
		{
			this.launchSchedulingEnabled = newvalue;
			this.checkDestFrequencyValid();
			this.updateRocketOnDockSettings();
		}
	}

	public void updateRocketOnDockSettings()
	{
		if (this.attachedDock instanceof TileEntityLandingPad)
		{
			TileEntityLandingPad pad = ((TileEntityLandingPad) this.attachedDock); 
			IDockable rocket = pad.getDockedEntity();
			if (rocket instanceof EntityAutoRocket)
			{
				((EntityAutoRocket) rocket).updateControllerSettings(pad);
			}
		}
	}
}
