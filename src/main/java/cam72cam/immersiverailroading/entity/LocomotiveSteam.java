package cam72cam.immersiverailroading.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cam72cam.immersiverailroading.Config;
import cam72cam.immersiverailroading.Config.ConfigBalance;
import cam72cam.immersiverailroading.ConfigGraphics;
import cam72cam.immersiverailroading.ConfigSound;
import cam72cam.immersiverailroading.ImmersiveRailroading;
import cam72cam.immersiverailroading.library.GuiTypes;
import cam72cam.immersiverailroading.library.RenderComponentType;
import cam72cam.immersiverailroading.model.RenderComponent;
import cam72cam.immersiverailroading.registry.LocomotiveSteamDefinition;
import cam72cam.immersiverailroading.sound.ISound;
import cam72cam.immersiverailroading.util.BurnUtil;
import cam72cam.immersiverailroading.util.FluidQuantity;
import cam72cam.immersiverailroading.util.VecUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.tileentity.TileEntityFurnace;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fluids.*;

public class LocomotiveSteam extends Locomotive {
	// PSI
	private static DataParameter<Float> BOILER_PRESSURE = EntityDataManager.createKey(LocomotiveSteam.class, DataSerializers.FLOAT);
	// Celsius
	private static DataParameter<Float> BOILER_TEMPERATURE = EntityDataManager.createKey(LocomotiveSteam.class, DataSerializers.FLOAT);
	// Map<Slot, TicksToBurn>
	private static DataParameter<NBTTagCompound> BURN_TIME = EntityDataManager.createKey(LocomotiveSteam.class, DataSerializers.COMPOUND_TAG);
	private static DataParameter<NBTTagCompound> BURN_MAX = EntityDataManager.createKey(LocomotiveSteam.class, DataSerializers.COMPOUND_TAG);
	private double driverDiameter;
	
	public LocomotiveSteam(World world) {
		this(world, null);
	}

	public LocomotiveSteam(World world, String defID) {
		super(world, defID);
		
		this.getDataManager().register(BOILER_PRESSURE, 0f);
		this.getDataManager().register(BOILER_TEMPERATURE, 0f);
		this.getDataManager().register(BURN_TIME, new NBTTagCompound());
		this.getDataManager().register(BURN_MAX, new NBTTagCompound());
	}

	@Override
	public LocomotiveSteamDefinition getDefinition() {
		return super.getDefinition(LocomotiveSteamDefinition.class);
	}

	@Override
	public GuiTypes guiType() {
		return GuiTypes.STEAM_LOCOMOTIVE;
	}
	
	@Override
	protected void writeEntityToNBT(NBTTagCompound nbttagcompound) {
		super.writeEntityToNBT(nbttagcompound);
		nbttagcompound.setFloat("boiler_temperature", getBoilerTemperature());
		nbttagcompound.setFloat("boiler_psi", getBoilerPressure());
		nbttagcompound.setTag("burn_time", dataManager.get(BURN_TIME));
		nbttagcompound.setTag("burn_max", dataManager.get(BURN_MAX));
	}

	@Override
	protected void readEntityFromNBT(NBTTagCompound nbttagcompound) {
		super.readEntityFromNBT(nbttagcompound);
		setBoilerTemperature(nbttagcompound.getFloat("boiler_temperature"));
		setBoilerPressure(nbttagcompound.getFloat("boiler_psi"));
		dataManager.set(BURN_TIME, (NBTTagCompound) nbttagcompound.getTag("burn_time"));
		dataManager.set(BURN_MAX, (NBTTagCompound) nbttagcompound.getTag("burn_max"));
		

	}
	
	@Override
	public void readSpawnData(ByteBuf additionalData) {
		super.readSpawnData(additionalData);

		List<RenderComponent> driving = this.getDefinition().getComponents(RenderComponentType.WHEEL_DRIVER_X, gauge);
		if (driving != null) {
			for (RenderComponent driver : driving) {
				driverDiameter = Math.max(driverDiameter, driver.height());
			}
		}
		driving = this.getDefinition().getComponents(RenderComponentType.WHEEL_DRIVER_REAR_X, gauge);
		if (driving != null) {
			for (RenderComponent driver : driving) {
				driverDiameter = Math.max(driverDiameter, driver.height());
			}
		}
	}
	
	public float getBoilerTemperature() {
		return this.dataManager.get(BOILER_TEMPERATURE);
	}
	private void setBoilerTemperature(float temp) {
		this.dataManager.set(BOILER_TEMPERATURE, temp);
	}
	
	public float getBoilerPressure() {
		return this.dataManager.get(BOILER_PRESSURE);
	}
	private void setBoilerPressure(float temp) {
		this.dataManager.set(BOILER_PRESSURE, temp);
	}
	
	private NBTTagCompound mapToNBT(Map<Integer, Integer> map) {
		NBTTagCompound data = new NBTTagCompound();
		for (Integer slot : map.keySet()) {
			data.setInteger("" + slot, map.get(slot));
		}
		return data;
	}
	private Map<Integer, Integer> NBTtoMap(NBTTagCompound nbt) {
		Map<Integer, Integer> map = new HashMap<Integer, Integer>();
		for (String key : nbt.getKeySet()) {
			map.put(Integer.parseInt(key), nbt.getInteger(key));
		}
		return map;
	}
	
	public Map<Integer, Integer> getBurnTime() {
		return NBTtoMap(this.dataManager.get(BURN_TIME));
	}
	private void setBurnTime(Map<Integer, Integer> burnTime) {
		this.dataManager.set(BURN_TIME, mapToNBT(burnTime));
	}
	public Map<Integer, Integer> getBurnMax() {
		return NBTtoMap(this.dataManager.get(BURN_MAX));
	}
	private void setBurnMax(Map<Integer, Integer> burnMax) {
		this.dataManager.set(BURN_MAX, mapToNBT(burnMax));
	}
	
	
	@Override
	protected int getAvailableHP() {
		if (!Config.isFuelRequired(gauge)) {
			return this.getDefinition().getHorsePower(gauge);
		}
		return (int) (this.getDefinition().getHorsePower(gauge) * Math.pow(this.getBoilerPressure() / this.getDefinition().getMaxPSI(gauge), 3));
	}
	
	
	@Override
	public void onDissassemble() {
		super.onDissassemble();
		this.setBoilerTemperature(0);
		this.setBoilerPressure(0);
		
		Map<Integer, Integer> burnTime = getBurnTime();
		for (Integer slot : burnTime.keySet()) {
			burnTime.put(slot, 0);
		}
		setBurnTime(burnTime);
	}

	private double getPhase(int spikes, float offsetDegrees, double perc) {
		if (driverDiameter == 0) {
			return 0;
		}
		double circumference = (driverDiameter * Math.PI);
		double skewDistance = this.distanceTraveled - this.getCurrentSpeed().minecraft() * perc;
		double phase = (skewDistance % circumference)/circumference;
		phase = Math.abs(Math.cos(phase*Math.PI*spikes + Math.toRadians(offsetDegrees)));
		return phase;
	}
	
	private double getPhase(int spikes, float offsetDegrees) {
		if (driverDiameter == 0) {
			return 0;
		}
		double circumference = (driverDiameter * Math.PI);
		double phase = (this.distanceTraveled % circumference)/circumference;
		phase = Math.abs(Math.cos(phase*Math.PI*spikes + Math.toRadians(offsetDegrees)));
		return phase;
	}
	
	private Map<String, Boolean> phaseOn = new HashMap<String, Boolean>();
	private List<ISound> sndCache = new ArrayList<ISound>();
	private int sndCacheId = 0;
	private ISound whistle;
	private ISound idle;
	private ISound pressure;
	@Override
	public void onUpdate() {
		super.onUpdate();

		if (world.isRemote) {
			// Particles and Sound
			
			if (ConfigSound.soundEnabled) {
				if (this.sndCache.size() == 0) {
					this.whistle = ImmersiveRailroading.proxy.newSound(this.getDefinition().whistle, false, 150, gauge);
	
					for (int i = 0; i < 32; i ++) {
						sndCache.add(ImmersiveRailroading.proxy.newSound(this.getDefinition().chuff, false, 80, gauge));
					}
					
					this.idle = ImmersiveRailroading.proxy.newSound(this.getDefinition().idle, true, 40, gauge);
					idle.setVolume(0.1f);
					this.pressure = ImmersiveRailroading.proxy.newSound(this.getDefinition().pressure, true, 40, gauge);
					pressure.setVolume(0.5f);
				}
				
				if (this.getDataManager().get(HORN) != 0 && !whistle.isPlaying() && (this.getBoilerPressure() > 0 || !Config.isFuelRequired(gauge))) {
					whistle.play(getPositionVector());
				}
				
				if (this.getBoilerTemperature() > 0) {
					if (!idle.isPlaying()) {
						idle.play(getPositionVector());
					}
				} else {
					if (idle.isPlaying()) {
						idle.stop();
					}
				}
			}
			
			double phase;
			
			Vec3d fakeMotion = new Vec3d(this.motionX, this.motionY, this.motionZ);//VecUtil.fromYaw(this.getCurrentSpeed().minecraft(), this.rotationYaw);
			
			List<RenderComponent> smokes = this.getDefinition().getComponents(RenderComponentType.PARTICLE_CHIMNEY_X, gauge);
			if (smokes != null && ConfigGraphics.particlesEnabled) {
				phase = getPhase(4, 0);
				//System.out.println(phase);
				for (RenderComponent smoke : smokes) {
					Vec3d particlePos = this.getPositionVector().add(VecUtil.rotateYaw(smoke.center(), this.rotationYaw + 180)).addVector(0, 0.35 * gauge.scale(), 0);
					particlePos = particlePos.subtract(fakeMotion);
					if (this.ticksExisted % 1 == 0 ) {
						float darken = 0;
						float thickness = Math.abs(this.getThrottle())/2;
						for (int i : this.getBurnTime().values()) {
							darken += i >= 1 ? 1 : 0;
						}
						if (darken == 0 && Config.isFuelRequired(gauge)) {
							break;
						}
						darken /= this.getInventorySize() - 2.0;
						darken *= 0.5;
						
						double smokeMod = Math.min(1, Math.max(0.2, Math.abs(this.getCurrentSpeed().minecraft())*2));
						
						int lifespan = (int) (200 * (1 + Math.abs(this.getThrottle())) * smokeMod * gauge.scale());
						//lifespan *= size;
						
						float verticalSpeed = (0.5f + Math.abs(this.getThrottle())) * (float)gauge.scale();
						
						double size = smoke.width() * (0.8 + smokeMod);
						if (phase != 0 && Math.abs(this.getThrottle()) > 0.01 && Math.abs(this.getCurrentSpeed().metric()) / gauge.scale() < 30) {
							double phaseSpike = Math.pow(phase, 8);
							size *= 1 + phaseSpike*1.5;
							verticalSpeed *= 1 + phaseSpike/2;
						}
						
						particlePos = particlePos.subtract(fakeMotion);
						
						EntitySmokeParticle sp = new EntitySmokeParticle(world, lifespan , darken, thickness, size);
						sp.setPosition(particlePos.x, particlePos.y, particlePos.z);
						sp.setVelocity(fakeMotion.x, fakeMotion.y + verticalSpeed, fakeMotion.z);
						world.spawnEntity(sp);
					}
				}
			}
			
			List<RenderComponent> pistons = this.getDefinition().getComponents(RenderComponentType.PISTON_ROD_SIDE, gauge);
			double csm = Math.abs(this.getCurrentSpeed().metric()) / gauge.scale();
			if (pistons != null && (this.getBoilerPressure() > 0 || !Config.isFuelRequired(gauge))) {
				for (RenderComponent piston : pistons) {
					float phaseOffset = 0;
					switch (piston.side) {
					case "LEFT":
						phaseOffset = 45+90;
						break;
					case "RIGHT":
						phaseOffset = -45+90;
						break;
					case "LEFT_FRONT":
						phaseOffset = 45+90;
						break;
					case "RIGHT_FRONT":
						phaseOffset = -45+90;
						break;
					case "LEFT_REAR":
						phaseOffset = 90;
						break;
					case "RIGHT_REAR":
						phaseOffset = 0;
						break;
					default:
						continue;
					}
					
					phase = this.getPhase(2, phaseOffset);
					double phaseSpike = Math.pow(phase, 4);
					
					if (phaseSpike >= 0.6 && csm > 0.1 && csm  < 20 && ConfigGraphics.particlesEnabled) {
						Vec3d particlePos = this.getPositionVector().add(VecUtil.rotateYaw(piston.min(), this.rotationYaw + 180)).addVector(0, 0.35 * gauge.scale(), 0);
						EntitySmokeParticle sp = new EntitySmokeParticle(world, 80, 0, 0.6f, 0.2);
						sp.setPosition(particlePos.x, particlePos.y, particlePos.z);
						double accell = (piston.side.contains("RIGHT") ? 1 : -1) * 0.3 * gauge.scale();
						Vec3d sideMotion = fakeMotion.add(VecUtil.fromYaw(accell, this.rotationYaw+90));
						sp.setVelocity(sideMotion.x, sideMotion.y+0.01, sideMotion.z);
						world.spawnEntity(sp);
					}
					
					if (!ConfigSound.soundEnabled) {
						continue;
					}
					
					String key = piston.side;
					if (!phaseOn.containsKey(key)) {
						phaseOn.put(key, false);
					}
					
					for (int i = 0; i < 10; i++) {
						phase = this.getPhase(2, phaseOffset + 45, 1-i/10.0);
						
						if (!phaseOn.get(key)) {
							if (phase > 0.5) {
						    	double speed = Math.abs(getCurrentSpeed().minecraft());
						    	double maxSpeed = Math.abs(getDefinition().getMaxSpeed(gauge).minecraft());
						    	float volume = (float) Math.max(1-speed/maxSpeed, 0.3) * Math.max(0.3f, Math.abs(this.getThrottle()));
						    	volume = (float) Math.sqrt(volume);
						    	double fraction = 3;
						    	float pitch = 0.8f + (float) (speed/maxSpeed/fraction);
						    	pitch += (this.ticksExisted % 10) / 300.0;
						    	ISound snd = sndCache.get(sndCacheId);
						    	snd.setPitch(pitch);
						    	snd.setVolume(volume);
						    	snd.play(getPositionVector());
						    	sndCacheId++;
						    	sndCacheId = sndCacheId % sndCache.size();
								phaseOn.put(key, true);
							}
						} else {
							if (phase < 0.5) {
								phaseOn.put(key, false);
							}
						}
					}
				}
			}
			
			List<RenderComponent> steams = this.getDefinition().getComponents(RenderComponentType.PRESSURE_VALVE_X, gauge);
			if (steams != null && (this.getBoilerPressure() >= this.getDefinition().getMaxPSI(gauge) || !Config.isFuelRequired(gauge))) {
				if (ConfigSound.soundEnabled && ConfigSound.soundPressureValve) {
					if (!pressure.isPlaying()) {
						pressure.play(getPositionVector());
					}
				}
				if (ConfigGraphics.particlesEnabled) {
					for (RenderComponent steam : steams) {
						Vec3d particlePos = this.getPositionVector().add(VecUtil.rotateYaw(steam.center(), this.rotationYaw + 180)).addVector(0, 0.35 * gauge.scale(), 0);
						particlePos = particlePos.subtract(fakeMotion);
						EntitySmokeParticle sp = new EntitySmokeParticle(world, 40, 0, 0.2f, steam.width());
						sp.setPosition(particlePos.x, particlePos.y, particlePos.z);
						sp.setVelocity(fakeMotion.x, fakeMotion.y + 0.2 * gauge.scale(), fakeMotion.z);
						world.spawnEntity(sp);
					}
				}
			} else {
				if (ConfigSound.soundEnabled && pressure.isPlaying()) {
					pressure.stop();
				}
			}
			
			if (ConfigSound.soundEnabled) {
				// Update sound positions
				if (whistle.isPlaying()) {
					whistle.setPosition(getPositionVector());
					whistle.setVelocity(getVelocity());
					whistle.update();
				}
				if (idle.isPlaying()) {
					idle.setPosition(getPositionVector());
					idle.setVelocity(getVelocity());
					idle.update();
				}
				if (pressure.isPlaying()) {
					pressure.setPosition(getPositionVector());
					pressure.setVelocity(getVelocity());
					pressure.update();
				}
				for (int i = 0; i < sndCache.size(); i ++) {
					ISound snd = sndCache.get(i);
					if (snd.isPlaying()) {
						snd.setPosition(getPositionVector());
						snd.setVelocity(getVelocity());
						snd.update();
					}
				}
			}
			
			return;
		}
		
		if (!this.isBuilt()) {
			return;
		}
		
		if (this.getCoupled(CouplerType.BACK) instanceof Tender) {
			Tender tender = (Tender) getCoupled(CouplerType.BACK);

			// Only drain 10mb at a time from the tender
			int desiredDrain = 10;
			if (getTankCapacity().MilliBuckets() - getServerLiquidAmount() >= 10) {
				FluidUtil.tryFluidTransfer(this.theTank, tender.theTank, desiredDrain, true);
			}
			
			if (this.ticksExisted % 20 == 0) {
				// Top off stacks
				for (int slot = 0; slot < this.cargoItems.getSlots()-2; slot ++) {
					if (BurnUtil.getBurnTime(this.cargoItems.getStackInSlot(slot)) != 0) {
						for (int tenderSlot = 0; tenderSlot < tender.cargoItems.getSlots(); tenderSlot ++) {
							if (this.cargoItems.getStackInSlot(slot).isItemEqual(tender.cargoItems.getStackInSlot(tenderSlot))) {
								if (this.cargoItems.getStackInSlot(slot).getMaxStackSize() > this.cargoItems.getStackInSlot(slot).getCount()) {
									ItemStack extracted = tender.cargoItems.extractItem(tenderSlot, 1, false);
									this.cargoItems.insertItem(slot, extracted, false);
								}
							}
						}
					}
				}
			}
		}
		
		float boilerTemperature = getBoilerTemperature();
		float boilerPressure = getBoilerPressure();
		float waterLevelMB = this.getLiquidAmount();
		Map<Integer, Integer> burnTime = getBurnTime();
		Map<Integer, Integer> burnMax = getBurnMax();
		Boolean changedBurnTime = false;
		Boolean changedBurnMax = false;
		int burningSlots = 0;
		float waterUsed = 0;
		
		if (this.getLiquidAmount() > 0) {
			for (int slot = 0; slot < this.cargoItems.getSlots()-2; slot ++) {
				int remainingTime = burnTime.containsKey(slot) ? burnTime.get(slot) : 0;
				if (remainingTime <= 0) {
					ItemStack stack = this.cargoItems.getStackInSlot(slot);
					if (stack.getCount() <= 0 || !TileEntityFurnace.isItemFuel(stack)) {
						continue;
					}
					remainingTime = (int) (BurnUtil.getBurnTime(stack) * 1/gauge.scale() * (Config.ConfigBalance.locoSteamFuelEfficiency / 100.0));
					burnTime.put(slot, remainingTime);
					burnMax.put(slot, remainingTime);
					stack.setCount(stack.getCount()-1);
					this.cargoItems.setStackInSlot(slot, stack);
					changedBurnMax = true;
				} else {
					burnTime.put(slot, remainingTime - 1);
				}
				changedBurnTime = true;
				burningSlots += 1;
			}
		}
		
		double energyKCalDeltaTick = 0;
		
		if (burningSlots != 0 && this.getLiquidAmount() > 0) {
			energyKCalDeltaTick += burningSlots * coalEnergyKCalTick();
		}

		// Assume the boiler is a cube...
		double boilerVolume = this.getTankCapacity().Buckets();
		double boilerEdgeM = Math.pow(boilerVolume, 1.0/3.0);
		double boilerAreaM = 6 * Math.pow(boilerEdgeM, 2);

		if (boilerTemperature > 0) {
			// Decrease temperature due to heat loss
			// Estimate Kw emitter per m^2: (TdegC/10)^2 / 100
			double radiatedKwHr = Math.pow(boilerTemperature/10, 2) / 100 * boilerAreaM * 2;
			double radiatedKCalHr = radiatedKwHr * 859.85;
			double radiatedKCalTick = radiatedKCalHr / 60 / 60 / 20 * ConfigBalance.locoHeatTimeScale;
			energyKCalDeltaTick -= radiatedKCalTick / 1000;
		}
		
		if (energyKCalDeltaTick != 0) {
			// Change temperature
			// 1 KCal raises 1KG water at STP 1 degree
			// 1 KG of water == 1 m^3 of water 
			// TODO what happens when we change liters per mb FluidQuantity.FromMillibuckets((int) waterLevelMB).Liters()
			boilerTemperature += energyKCalDeltaTick / (waterLevelMB / 1000);
		}
		
		if (boilerTemperature > 100) {
			// Assume linear relationship between temperature and pressure
			// Max 2 degree per second
			float heatTransfer = Math.min(2.0f/20, boilerTemperature - 100);
			boilerPressure += heatTransfer;

			if (this.getPercentLiquidFull() > 25) {
				boilerTemperature -= heatTransfer;
			}
			
			// Pressure relief valve
			int maxPSI = this.getDefinition().getMaxPSI(gauge);
			if (boilerPressure > maxPSI) {
				waterUsed += boilerPressure - maxPSI; 
				boilerPressure = maxPSI;
			}
		} else {
			if (boilerPressure > 0) {
				// Reduce pressure by needed temperature
				boilerPressure = Math.max(0, boilerPressure - (100 - boilerTemperature));
				boilerTemperature = 100;
			}
		}
		
		float throttle = Math.abs(getThrottle());
		if (throttle != 0 && boilerPressure > 0) {
			double burnableSlots = this.cargoItems.getSlots()-2;
			double maxKCalTick = burnableSlots * coalEnergyKCalTick();
			double maxPressureTick = maxKCalTick / (this.getTankCapacity().MilliBuckets() / 1000);
			
			float delta = (float) (throttle * maxPressureTick);
			
			boilerPressure = Math.max(0, boilerPressure - delta);
			waterUsed += delta * Config.ConfigBalance.locoWaterUsage;
		}
		
		if (waterUsed != 0) {
			if (waterUsed > 0) {
				theTank.drain((int) Math.floor(waterUsed), true);
				waterUsed = waterUsed % 1;
			}
			// handle remainder
			if (Math.random() <= waterUsed) {
				theTank.drain(1, true);
			}
		}
		
		setBoilerPressure(boilerPressure);
		setBoilerTemperature(boilerTemperature);
		if (changedBurnTime) {
			setBurnTime(burnTime);
		}
		if (changedBurnMax) {
			setBurnMax(burnMax);
		}
		
		if (boilerPressure > this.getDefinition().getMaxPSI(gauge) * 1.1 || (boilerPressure > this.getDefinition().getMaxPSI(gauge) * 0.5 && boilerTemperature > 150)) {
			// 10% over max pressure OR
			// Half max pressure and high boiler temperature
			//EXPLODE
			if (Config.ConfigDamage.explosionsEnabled) {
				for (int i = 0; i < 5; i++) {
					world.createExplosion(this, this.posX, this.posY, this.posZ, boilerPressure/8, true);
				}
			}
			world.removeEntity(this);
		}
	}

	@Override
	public void setDead() {
		super.setDead();
		
		if (idle != null) {
			idle.stop();
		}
		if (pressure != null) {
			pressure.stop();
		}
	}

	@Override
	public int getInventorySize() {
		return this.getDefinition().getInventorySize(gauge) + 2;
	}
	
	public int getInventoryWidth() {
		return this.getDefinition().getInventoryWidth(gauge);
	}
	
	@Override
	protected int[] getContainerInputSlots() {
		return new int[] { getInventorySize()-2 };
	}
	@Override
	protected int[] getContainertOutputSlots() {
		return new int[] { getInventorySize()-1 };
	}

	@Override
	public FluidQuantity getTankCapacity() {
		return this.getDefinition().getTankCapacity(gauge);
	}

	@Override
	public List<Fluid> getFluidFilter() {
		List<Fluid> filter = new ArrayList<Fluid>();
		filter.add(FluidRegistry.WATER);
		return filter;
	}

	private double coalEnergyKCalTick() {
		// Coal density = 800 KG/m3 (engineering toolbox)
		double coalEnergyDensity = 30000; // KJ/KG (engineering toolbox)
		double coalEnergyKJ = coalEnergyDensity / 9; // Assume each slot is burning 1/9th of a coal block
		double coalEnergyBTU = coalEnergyKJ * 0.958; // 1 KJ = 0.958 BTU
		double coalEnergyKCal = coalEnergyBTU / (3.968 * 1000); // 3.968 BTU = 1 KCal
		double coalBurnTicks = 1600; // This is a bit of fudge
		double coalEnergyKCalTick = coalEnergyKCal / coalBurnTicks * ConfigBalance.locoHeatTimeScale;
		return coalEnergyKCalTick;
	}
}