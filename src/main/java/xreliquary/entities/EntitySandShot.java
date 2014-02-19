package xreliquary.entities;

import java.util.Iterator;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.monster.EntityCreeper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

public class EntitySandShot extends Entity implements IProjectile {
	private int xTile = -1;
	private int yTile = -1;
	private int zTile = -1;
	private int inTile = 0;
	private int inData = 0;
	private boolean inGround = false;

	/** The owner of this arrow. */
	public EntityPlayer shootingEntity;
	private int ticksInAir = 0;

	private int ricochetCounter = 0;
	private boolean scheduledForDeath = false;

	public EntitySandShot(World par1World) {
		super(par1World);
		this.setSize(0.5F, 0.5F);
	}

	public EntitySandShot(World par1World, double par2, double par4, double par6) {
		super(par1World);
		this.setSize(0.5F, 0.5F);
		this.setPosition(par2, par4, par6);
		yOffset = 0.0F;
	}

	public EntitySandShot(World par1World, EntityPlayer par2EntityPlayer) {
		super(par1World);
		shootingEntity = par2EntityPlayer;
		float par3 = 0.8F;
		this.setSize(0.1F, 0.1F);
		this.setLocationAndAngles(par2EntityPlayer.posX, par2EntityPlayer.posY + par2EntityPlayer.getEyeHeight(), par2EntityPlayer.posZ, par2EntityPlayer.rotationYaw, par2EntityPlayer.rotationPitch);
		posX -= MathHelper.cos(rotationYaw / 180.0F * (float) Math.PI) * 0.16F;
		posY -= 0.2D;
		posZ -= MathHelper.sin(rotationYaw / 180.0F * (float) Math.PI) * 0.16F;
		this.setPosition(posX, posY, posZ);
		yOffset = 0.0F;
		motionX = -MathHelper.sin(rotationYaw / 180.0F * (float) Math.PI) * MathHelper.cos(rotationPitch / 180.0F * (float) Math.PI);
		motionZ = MathHelper.cos(rotationYaw / 180.0F * (float) Math.PI) * MathHelper.cos(rotationPitch / 180.0F * (float) Math.PI);
		motionY = -MathHelper.sin(rotationPitch / 180.0F * (float) Math.PI);
		this.setThrowableHeading(motionX, motionY, motionZ, par3 * 1.5F, 1.0F);
	}

	@Override
	protected void entityInit() {
		dataWatcher.addObject(16, Byte.valueOf((byte) 0));
	}

	/**
	 * Similar to setArrowHeading, it's point the throwable entity to a x, y, z
	 * direction.
	 */
	@Override
	public void setThrowableHeading(double var1, double var3, double var5, float var7, float var8) {
		float var9 = MathHelper.sqrt_double(var1 * var1 + var3 * var3 + var5 * var5);
		var1 /= var9;
		var3 /= var9;
		var5 /= var9;
		var1 += rand.nextGaussian() * 0.007499999832361937D * var8;
		var3 += rand.nextGaussian() * 0.007499999832361937D * var8;
		var5 += rand.nextGaussian() * 0.007499999832361937D * var8;
		var1 *= var7;
		var3 *= var7;
		var5 *= var7;
		motionX = var1;
		motionY = var3;
		motionZ = var5;
		float var10 = MathHelper.sqrt_double(var1 * var1 + var5 * var5);
		prevRotationYaw = rotationYaw = (float) (Math.atan2(var1, var5) * 180.0D / Math.PI);
		prevRotationPitch = rotationPitch = (float) (Math.atan2(var3, var10) * 180.0D / Math.PI);
	}

	@Override
	@SideOnly(Side.CLIENT)
	/**
	 * Sets the position and rotation. Only difference from the other one is no bounding on the rotation. Args: posX,
	 * posY, posZ, yaw, pitch
	 */
	public void setPositionAndRotation2(double par1, double par3, double par5, float par7, float par8, int par9) {
		this.setPosition(par1, par3, par5);
		this.setRotation(par7, par8);
	}

	@Override
	@SideOnly(Side.CLIENT)
	/**
	 * Sets the velocity to the args. Args: x, y, z
	 */
	public void setVelocity(double par1, double par3, double par5) {
		motionX = par1;
		motionY = par3;
		motionZ = par5;

		if (prevRotationPitch == 0.0F && prevRotationYaw == 0.0F) {
			float var7 = MathHelper.sqrt_double(par1 * par1 + par5 * par5);
			prevRotationYaw = rotationYaw = (float) (Math.atan2(par1, par5) * 180.0D / Math.PI);
			prevRotationPitch = rotationPitch = (float) (Math.atan2(par3, var7) * 180.0D / Math.PI);
			prevRotationPitch = rotationPitch;
			prevRotationYaw = rotationYaw;
			this.setLocationAndAngles(posX, posY, posZ, rotationYaw, rotationPitch);
		}
	}

	/**
	 * Called to update the entity's position/logic.
	 */
	@Override
	public void onUpdate() {
		super.onUpdate();
		if (posY > worldObj.getHeight()) {
			changeWeather();
			this.setDead();
		}
		if (ticksInAir > 600) {
			this.setDead();
		}
		if (shootingEntity == null) {
			List players = worldObj.getEntitiesWithinAABB(EntityPlayer.class, AxisAlignedBB.getBoundingBox(posX - 1, posY - 1, posZ - 1, posX + 1, posY + 1, posZ + 1));
			Iterator i = players.iterator();
			double closestDistance = Double.MAX_VALUE;
			EntityPlayer closestPlayer = null;
			while (i.hasNext()) {
				EntityPlayer e = (EntityPlayer) i.next();
				double distance = e.getDistanceToEntity(this);
				if (distance < closestDistance) {
					closestPlayer = e;
				}
			}
			if (closestPlayer != null) {
				shootingEntity = closestPlayer;
			}
		}
		if (prevRotationPitch == 0.0F && prevRotationYaw == 0.0F) {
			float var1 = MathHelper.sqrt_double(motionX * motionX + motionZ * motionZ);
			prevRotationYaw = rotationYaw = (float) (Math.atan2(motionX, motionZ) * 180.0D / Math.PI);
			prevRotationPitch = rotationPitch = (float) (Math.atan2(motionY, var1) * 180.0D / Math.PI);
		}



        Block block = this.worldObj.getBlock(this.xTile, this.yTile, this.zTile);

        if (block.getMaterial() != Material.air)
        {
            block.setBlockBoundsBasedOnState(this.worldObj, this.xTile, this.yTile, this.zTile);
            AxisAlignedBB axisalignedbb = block.getCollisionBoundingBoxFromPool(this.worldObj, this.xTile, this.yTile, this.zTile);

            if (axisalignedbb != null && axisalignedbb.isVecInside(this.worldObj.getWorldVec3Pool().getVecFromPool(this.posX, this.posY, this.posZ)))
            {
                this.inGround = true;
            }
        }


		if (!inGround) {
			++ticksInAir;
			if (ticksInAir > 1 && ticksInAir < 3) {
				worldObj.spawnParticle("flame", posX + smallGauss(0.1D), posY + smallGauss(0.1D), posZ + smallGauss(0.1D), 0D, 0D, 0D);
				for (int particles = 0; particles < 3; particles++) {
					this.doFiringParticles();
				}
			}
			Vec3 var17 = worldObj.getWorldVec3Pool().getVecFromPool(posX, posY, posZ);
			Vec3 var3 = worldObj.getWorldVec3Pool().getVecFromPool(posX + motionX, posY + motionY, posZ + motionZ);
			MovingObjectPosition var4 = worldObj.func_147447_a(var17, var3, false, true, false);
			var17 = worldObj.getWorldVec3Pool().getVecFromPool(posX, posY, posZ);
			var3 = worldObj.getWorldVec3Pool().getVecFromPool(posX + motionX, posY + motionY, posZ + motionZ);

			if (var4 != null) {
				var3 = worldObj.getWorldVec3Pool().getVecFromPool(var4.hitVec.xCoord, var4.hitVec.yCoord, var4.hitVec.zCoord);
			}

			Entity var5 = null;
			List var6 = worldObj.getEntitiesWithinAABBExcludingEntity(this, boundingBox.addCoord(motionX, motionY, motionZ).expand(1.0D, 1.0D, 1.0D));
			double var7 = 0.0D;
			Iterator var9 = var6.iterator();
			float var11;

			while (var9.hasNext()) {
				Entity var10 = (Entity) var9.next();
				if (var10.canBeCollidedWith() && (var10 != shootingEntity || ticksInAir >= 5)) {
					var11 = 0.3F;
					AxisAlignedBB var12 = var10.boundingBox.expand(var11, var11, var11);
					MovingObjectPosition var13 = var12.calculateIntercept(var17, var3);

					if (var13 != null) {
						double var14 = var17.distanceTo(var13.hitVec);

						if (var14 < var7 || var7 == 0.0D) {
							var5 = var10;
							var7 = var14;
						}
					}
				}
			}

			if (var5 != null) {
				var4 = new MovingObjectPosition(var5);
			}

			if (var4 != null) {
				this.onImpact(var4);
				if (scheduledForDeath) {
					this.setDead();
				}
			}

			posX += motionX;
			posY += motionY;
			posZ += motionZ;
			MathHelper.sqrt_double(motionX * motionX + motionZ * motionZ);

			this.setPosition(posX, posY, posZ);
			this.func_145775_I();
		}
	}

	private void changeWeather() {
		if (worldObj.getWorldInfo().isRaining()) {
			worldObj.getWorldInfo().setRaining(false);
		} else if (worldObj.getWorldInfo().isThundering()) {
			worldObj.getWorldInfo().setThundering(false);
		}
	}

	private void doFiringParticles() {
		worldObj.spawnParticle("mobSpellAmbient", posX + smallGauss(0.1D), posY + smallGauss(0.1D), posZ + smallGauss(0.1D), 0.5D, 0.5D, 0.5D);
		worldObj.spawnParticle("flame", posX, posY, posZ, gaussian(motionX), gaussian(motionY), gaussian(motionZ));
	}

	/**
	 * (abstract) Protected helper method to write subclass entity data to NBT.
	 */
	@Override
	public void writeEntityToNBT(NBTTagCompound par1NBTTagCompound) {
		par1NBTTagCompound.setShort("xTile", (short) xTile);
		par1NBTTagCompound.setShort("yTile", (short) yTile);
		par1NBTTagCompound.setShort("zTile", (short) zTile);
		par1NBTTagCompound.setByte("inTile", (byte) inTile);
		par1NBTTagCompound.setByte("inData", (byte) inData);
		par1NBTTagCompound.setByte("inGround", (byte) (inGround ? 1 : 0));
	}

	/**
	 * (abstract) Protected helper method to read subclass entity data from NBT.
	 */
	@Override
	public void readEntityFromNBT(NBTTagCompound par1NBTTagCompound) {
		xTile = par1NBTTagCompound.getShort("xTile");
		yTile = par1NBTTagCompound.getShort("yTile");
		zTile = par1NBTTagCompound.getShort("zTile");
		inTile = par1NBTTagCompound.getByte("inTile") & 255;
		inData = par1NBTTagCompound.getByte("inData") & 255;
		inGround = par1NBTTagCompound.getByte("inGround") == 1;
	}

	/**
	 * returns if this entity triggers Block.onEntityWalking on the blocks they
	 * walk on. used for spiders and wolves to prevent them from trampling crops
	 */
	@Override
	protected boolean canTriggerWalking() {
		return false;
	}

	@Override
	@SideOnly(Side.CLIENT)
	public float getShadowSize() {
		return 0.0F;
	}

	/**
	 * Sets the amount of knockback the arrow applies when it hits a mob.
	 */
	public void setKnockbackStrength(int par1) {
	}

	/**
	 * If returns false, the item will not inflict any damage against entities.
	 */
	@Override
	public boolean canAttackWithItem() {
		return false;
	}

	/**
	 * Whether the arrow has a stream of critical hit particles flying behind
	 * it.
	 */
	public void setIsCritical(boolean par1) {
		byte var2 = dataWatcher.getWatchableObjectByte(16);

		if (par1) {
			dataWatcher.updateObject(16, Byte.valueOf((byte) (var2 | 1)));
		} else {
			dataWatcher.updateObject(16, Byte.valueOf((byte) (var2 & -2)));
		}
	}

	/**
	 * Whether the arrow has a stream of critical hit particles flying behind
	 * it.
	 */
	public boolean getIsCritical() {
		byte var1 = dataWatcher.getWatchableObjectByte(16);
		return (var1 & 1) != 0;
	}

	private void onImpact(MovingObjectPosition mop) {
		if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.ENTITY && mop.entityHit != null) {
			if (mop.entityHit == shootingEntity)
				return;
			this.onImpact(mop.entityHit);
		} else if (mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
			this.groundImpact(mop.sideHit);
		}
	}

	private void onImpact(Entity mop) {
		if (mop == shootingEntity && ticksInAir > 3) {
			shootingEntity.attackEntityFrom(DamageSource.causePlayerDamage(shootingEntity), 1);
			this.setDead();
		} else {
			doDamage(8 + d6(), mop);
		}
		spawnHitParticles("reddust", 8);
		this.setDead();
	}

	private int d6() {
		return rand.nextInt(6) + 1;
	}

	private void spawnHitParticles(String string, int i) {
		for (int particles = 0; particles < i; particles++) {
			worldObj.spawnParticle(string, posX, posY - (string == "portal" ? 1 : 0), posZ, 0.7F, 0.7F, 0.3F);
		}
	}

	private void doDamage(int i, Entity mop) {
		if (mop instanceof EntityCreeper) {
			i *= 4;
		}
		if (mop instanceof EntityLiving) {
			((EntityLiving) mop).addPotionEffect(new PotionEffect(Potion.blindness.id, 200, 1));
		}
		if (worldObj.getWorldInfo().isRaining()) {
			i /= 2;
		}
		mop.attackEntityFrom(this.getDamageSource(), i);
	}

	private DamageSource getDamageSource() {
		return DamageSource.causePlayerDamage(shootingEntity);
	}

	private void groundImpact(int sideHit) {
		this.ricochet(sideHit);
	}

	private double smallGauss(double d) {
		return (worldObj.rand.nextFloat() - 0.5D) * d;
	}

	private double gaussian(double d) {
		return d + d * ((rand.nextFloat() - 0.5D) / 4);
	}

	private void ricochet(int sideHit) {
		switch (sideHit) {

		case 0:
		case 1:
			// topHit, bottomHit, reflect Y
			motionY = motionY * -1;
			break;
		case 2:
		case 3:
			// westHit, eastHit, reflect Z
			motionZ = motionZ * -1;
			break;
		case 4:
		case 5:
			// southHit, northHit, reflect X
			motionX = motionX * -1;
			break;
		}
		ricochetCounter++;
		if (ricochetCounter > this.getRicochetMax()) {
			scheduledForDeath = true;
			for (int particles = 0; particles < 4; particles++) {
				switch (sideHit) {
				case 0:
					worldObj.spawnParticle("smoke", posX, posY, posZ, gaussian(0.1D), -gaussian(0.1D), gaussian(0.1D));
					break;
				case 1:
					worldObj.spawnParticle("smoke", posX, posY, posZ, gaussian(0.1D), gaussian(0.1D), gaussian(0.1D));
					break;
				case 2:
					worldObj.spawnParticle("smoke", posX, posY, posZ, gaussian(0.1D), gaussian(0.1D), -gaussian(0.1D));
					break;
				case 3:
					worldObj.spawnParticle("smoke", posX, posY, posZ, gaussian(0.1D), gaussian(0.1D), gaussian(0.1D));
					break;
				case 4:
					worldObj.spawnParticle("smoke", posX, posY, posZ, -gaussian(0.1D), gaussian(0.1D), gaussian(0.1D));
					break;
				case 5:
					worldObj.spawnParticle("smoke", posX, posY, posZ, gaussian(0.1D), gaussian(0.1D), gaussian(0.1D));
					break;
				}
			}
		}
	}

	private int getRicochetMax() {
		return 0;
	}
}
