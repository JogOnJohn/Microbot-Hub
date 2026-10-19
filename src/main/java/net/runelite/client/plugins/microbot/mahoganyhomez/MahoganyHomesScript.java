package net.runelite.client.plugins.microbot.mahoganyhomez;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.geom.Area;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.questhelper.collections.ItemCollections;
import net.runelite.client.plugins.microbot.statemachine.StateMachineScript;
import net.runelite.client.plugins.microbot.statemachine.Transition;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.input.InputArbiter;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

@Slf4j
public final class MahoganyHomesScript extends StateMachineScript<MahoganyHomesScript.State>
{
	private static final int[] HOTSPOT_VARBITS =
		{10554, 10555, 10556, 10557, 10558, 10559, 10560, 10561};
	private static final int NO_PROGRESS_TICKS = 6;
	private static final int MAX_ATTEMPTS = 3;
	private static final Pattern COMPLETION_MESSAGE = Pattern.compile(
		"You have completed [\\d,]+ contracts with a total of [\\d,]+ points?\\.");
	private static final Pattern CONTRACT_HOME = Pattern.compile(
		"(?i)\\bgo\\s+see\\s+([a-z][a-z'-]*)\\b.*\\byou can get another job once you have furnished\\s+"
			+ "(?:his|her)\\s+home\\.?");
	private static final Pattern CONTRACT_TIER = Pattern.compile(
		"(?i)currently on an? (Beginner|Novice|Adept|Expert) Contract");
	private static final Pattern CONTRACT_ANNOUNCEMENT = Pattern.compile(
		"(?i)^(Beginner|Novice|Adept|Expert) Contract\\s*:\\s*Go see\\s+([a-z][a-z'-]*)\\b");
	private volatile String assignmentMessage;
	private int contactPickerSteps;
	private int contactSelectedTick = -1;
	private static final String ASSIGNED_HOME_KEY = "assignedHomeowner";
	private static final String ASSIGNED_TIER_KEY = "assignedTier";
	@Inject
	private ConfigManager configManager;

	private volatile int readyTick = -1;
	private volatile int completedAtTick = -1;
	private volatile boolean complete;
	private boolean nextContractReady;
	private volatile String error;
	private volatile int completedEffects;
	private volatile int[] hotspotValues = new int[HOTSPOT_VARBITS.length];
	private int processedTick = -1;
    private int interactionProbeTick = -1;
    private boolean skipEarlyHandoff;
    private volatile Work earlyHandoffWork;
    private long travelStartedAt;
    private WorldPoint travelOrigin;
	private MahoganyHomesConfig config;
	private MahoganyHomesData setupTier;
	private int setupPlanks;
	private int setupSteelBars;
	private String knownSupplyHomeowner;
	private MahoganyHomesData knownSupplyTier;
	private int knownSupplyPlanks;
	private int knownSupplyBars;
	private String lastSupplyReport = "";
	private boolean currentContract;
	private boolean currentContractAtHomeowner;
	private boolean setupReady;
	private boolean teleportResourcesPrepared;
	private boolean contractAcquired;
	private boolean contractDialogueClosed;
	private String assignedHomeowner;
	private MahoganyHomesData assignedTier;
	private MahoganyHomesContractorData contractor;
	private int contractRequestTick;
	private int contractAttempts;
	private String lastContractDialogue = "";
	private boolean contractArrived;
	private boolean contractValidated;
	private int contractArrivalWaitTick = -1;
	private final ContractLayoutReadiness layoutReadiness = new ContractLayoutReadiness();
	private final HomeownerValidationRecovery homeownerValidationRecovery = new HomeownerValidationRecovery();
	private int pendingBankItemId = -1;
	private int pendingBankItemQuantity;
	private int pendingBankActionTick;
	private int lastBankActionItemId = -1;
	private int bankActionAttempts;
	private int pendingSackQuantity = -1;
	private int pendingSackActionTick;
	private int lastSackQuantity = -1;
	private int sackActionAttempts;
	private int pendingSawItemId = -1;
	private int pendingSawItemQuantity;
	private int pendingSawActionTick;
	private int lastSawItemId = -1;
	private int sawActionAttempts;
	private boolean needsTravel;
	private boolean travelArrived;
	private boolean travelRequested;
	private State travelReturnState;
	private WorldPoint travelTarget;
	private int travelArrivalDistance;
	private boolean travelToNearestBank;
	private WorldPoint lastTravelPosition;
	private int lastTravelProgressTick;
	private Work pending;
	private Work activeWork;
	private int pendingValue;
	private int lastWorkProgressTick;
	private int workAttempts;
	private int missingObjectSince = -1;
	private boolean turnInRequested;
	private int turnInAttemptTick;
	private int turnInAttempts;
	private WalkerBridge walker;
	private TransitionSnapshotGuard snapshotGuard = new TransitionSnapshotGuard();
	private WorldPoint statePosition;

	enum State
	{
		GET_SETUP,
		GET_CONTRACT,
		READY,
		GO_TO_CONTRACT,
		VALIDATE_CONTRACT,
		DO_CONTRACT,
		TRAVEL,
		TURN_IN_CONTRACT,
		DONE,
		ERROR
	}

	@Override
	protected State initialState()
	{
		return State.GET_CONTRACT;
	}

	@Override
	protected List<Transition<State>> defineTransitions()
	{
		return List.of(
			transition(State.GET_SETUP, State.ERROR, () -> error != null, "Setup failed"),
			transition(State.GET_SETUP, State.TRAVEL, () -> needsTravel, "Travelling to the setup bank"),
			transition(State.GET_SETUP, State.VALIDATE_CONTRACT,
				() -> setupReady && currentContractAtHomeowner, "Current contract setup is ready"),
			transition(State.GET_SETUP, State.READY,
				() -> setupReady && currentContract && !currentContractAtHomeowner,
				"Saved contract setup is ready"),
			transition(State.GET_SETUP, State.GET_CONTRACT,
				() -> setupReady && !currentContract, "Next contract setup is ready"),
			transition(State.GET_CONTRACT, State.ERROR, () -> error != null, "Contract acquisition failed"),
			transition(State.GET_CONTRACT, State.TRAVEL, () -> needsTravel, "Travelling to a contractor"),
			transition(State.GET_CONTRACT, State.GET_SETUP,
				() -> contractAcquired && contractDialogueClosed && !setupReady,
				"Preparing for the assigned contract"),
			transition(State.GET_CONTRACT, State.READY,
				() -> contractAcquired && contractDialogueClosed && setupReady,
				"Contract assignment confirmed"),
			transition(State.READY, State.GET_CONTRACT,
				() -> assignedHomeowner == null, "No contract assignment is known"),
			transition(State.READY, State.GO_TO_CONTRACT,
				() -> assignedHomeowner != null, "Contract assignment is ready for travel"),
			transition(State.GO_TO_CONTRACT, State.ERROR, () -> error != null, "Contract travel failed"),
			transition(State.GO_TO_CONTRACT, State.TRAVEL, () -> needsTravel,
				"Travelling to the assigned homeowner"),
			transition(State.GO_TO_CONTRACT, State.VALIDATE_CONTRACT,
				() -> contractArrived, "Assigned contract reached"),
			transition(State.VALIDATE_CONTRACT, State.ERROR, () -> error != null, "Validation failed"),
			transition(State.VALIDATE_CONTRACT, State.TRAVEL, () -> needsTravel, "Approaching the repair layout"),
			transition(State.VALIDATE_CONTRACT, State.DO_CONTRACT,
				() -> contractValidated, "Contract verified"),
			transition(State.DO_CONTRACT, State.ERROR, () -> error != null, "Work failed"),
			transition(State.DO_CONTRACT, State.TRAVEL, () -> needsTravel,
				"Another floor has active work"),
			transition(State.DO_CONTRACT, State.TURN_IN_CONTRACT, this::allFixed,
				"Every active hotspot has an authoritative effect"),
			transition(State.TRAVEL, State.ERROR, () -> error != null, "Microbot walker failed"),
			transition(State.TRAVEL, State.GET_SETUP,
				() -> travelArrived && travelReturnState == State.GET_SETUP, "Setup bank reached"),
			transition(State.TRAVEL, State.GET_CONTRACT,
				() -> travelArrived && travelReturnState == State.GET_CONTRACT, "Contractor reached"),
			transition(State.TRAVEL, State.GO_TO_CONTRACT,
				() -> travelArrived && travelReturnState == State.GO_TO_CONTRACT,
				"Assigned home reached"),
			transition(State.TRAVEL, State.DO_CONTRACT,
				() -> travelArrived && travelReturnState == State.DO_CONTRACT, "Work floor reached"),
			transition(State.TRAVEL, State.VALIDATE_CONTRACT,
				() -> travelArrived && travelReturnState == State.VALIDATE_CONTRACT, "Repair layout approach reached"),
			transition(State.TRAVEL, State.TURN_IN_CONTRACT,
				() -> travelArrived && travelReturnState == State.TURN_IN_CONTRACT,
				"Homeowner reached"),
			transition(State.TURN_IN_CONTRACT, State.ERROR, () -> error != null, "Turn-in failed"),
			transition(State.TURN_IN_CONTRACT, State.TRAVEL, () -> needsTravel,
				"Returning to the homeowner"),
			transition(State.TURN_IN_CONTRACT, State.DONE,
				() -> complete && !Rs2Dialogue.isInDialogue(),
				"Contract completion message observed and dialogue closed"),
			transition(State.DONE, State.GET_CONTRACT, () -> nextContractReady, "Getting the next contract"),
			transition(State.ERROR, State.VALIDATE_CONTRACT, () -> error == null, "The error was cleared")
		);
	}

	private Transition<State> transition(
		State from, State to, java.util.function.BooleanSupplier condition, String reason)
	{
		return Transition.<State>from(from)
			.when(condition, reason)
			.because(reason)
			.goTo(to);
	}

	@Override
	protected void onState(State state)
	{
		if (state != State.ERROR && !captureUsableSnapshot()) return;
		if (walker == null && state != State.ERROR)
		{
			walker = WalkerBridge.bind();
		}
		if (walker != null && !walker.isAvailable())
		{
			fail("Microbot's walker became unavailable. Restart Mahogany Homes.");
			return;
		}
		switch (state)
		{
			case GET_SETUP:
				getSetup();
				break;
			case GET_CONTRACT:
				getContract();
				break;
			case READY:
				ready();
				break;
			case GO_TO_CONTRACT:
				goToContract();
				break;
			case VALIDATE_CONTRACT:
				validateContract();
				break;
			case DO_CONTRACT:
				doContract();
				break;
			case TRAVEL:
				travel();
				break;
			case TURN_IN_CONTRACT:
				turnIn();
				break;
			case DONE:
				Microbot.status = "Mahogany Homes: contract complete";
				if (readyForNextContract(readyTick, completedAtTick))
				{
					nextContractReady = true;
				}
				break;
			case ERROR:
				Microbot.status = "Mahogany Homes: " + error;
				break;
			default:
				fail("The plugin entered an unexpected state. Restart Mahogany Homes.");
		}
	}

	private boolean captureUsableSnapshot()
	{
		// Do not advance/cancel a route while the scene or local player is unavailable.
		if (walker != null && "BLOCKED".equals(walker.currentStatus))
		{
			fail("Travel to " + travelDescription() + " stopped by Microbot's walker. Check the route and restart.");
			return false;
		}
		var clientThread = Microbot.getClientThread();
		TravelSnapshot snapshot = clientThread == null ? new TravelSnapshot(null, null)
			: clientThread.runOnClientThreadOptional(() -> {
			net.runelite.api.Client client = Microbot.getClient();
			if (client == null) return new TravelSnapshot(null, null);
			GameState gameState = client.getGameState();
			WorldPoint position = gameState == GameState.LOGGED_IN && client.getLocalPlayer() != null
				? Rs2Player.getWorldLocation() : null;
			return new TravelSnapshot(gameState, position);
		}).orElse(new TravelSnapshot(null, null));
		TransitionSnapshotGuard.Decision decision = snapshotGuard.check(
			snapshot.gameState, snapshot.position, System.nanoTime() / 1_000_000);
		if (decision == TransitionSnapshotGuard.Decision.UNAVAILABLE)
		{
			fail("Player/scene unavailable or transition wait expired. Check login and the route before restarting.");
			return false;
		}
		if (decision == TransitionSnapshotGuard.Decision.WAIT)
		{
			Microbot.status = "Mahogany Homes: waiting for player/scene transition";
			return false;
		}
		statePosition = snapshot.position;
		if (snapshotGuard.recovered()) lastTravelProgressTick = readyTick;
		return true;
	}

	private static final class TravelSnapshot
	{
		final GameState gameState;
		final WorldPoint position;

		TravelSnapshot(GameState gameState, WorldPoint position)
		{
			this.gameState = gameState;
			this.position = position;
		}
	}

	@Override
	protected State onError(State state, Exception exception)
	{
		// Read only owned fields: live player/route queries may be the source of the exception.
		WalkerBridge bridge = walker;
		log.error("Mahogany Homes exception: state={} tick={} returnState={} target={} nearestBank={} "
			+ "requested={} arrived={} lastPosition={} lastProgressTick={} homeowner={} "
			+ "bridgeStatus={} bridgeTarget={} ownedTarget={}",
			state, readyTick, travelReturnState, travelTarget, travelToNearestBank,
			travelRequested, travelArrived, lastTravelPosition, lastTravelProgressTick, assignedHomeowner,
			bridge == null ? null : bridge.currentStatus, bridge == null ? null : bridge.requestedTarget,
			bridge == null ? null : bridge.ownedActiveTarget.get(), exception);
		fail("An unexpected error stopped the plugin. Check your current contract before restarting.");
		return State.ERROR;
	}

	public boolean run(MahoganyHomesConfig config)
	{
		this.config = config;
		loadAssignment();
		mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
		{
			int tick = readyTick;
			if (tick == processedTick)
			{
				return;
			}
			processedTick = tick;
			step();
		}, 0, 50, TimeUnit.MILLISECONDS);
		return true;
	}

	@Override
	public void shutdown()
	{
		if (walker != null)
		{
			walker.cancel();
		}
		super.shutdown();
	}

	void onGameTick(int tick)
	{
		readyTick = tick;
	}

	void onGameMessage(String message)
	{
		String text = Rs2UiHelper.stripTagsToSpace(message).trim();
		onAssignmentMessage(text);
		if (isCompletionMessage(text))
		{
			complete = true;
			completedAtTick = readyTick;
			clearAssignment();
		}
	}

	void onAssignmentMessage(String message)
	{
		if (config != null && parseAssignment(message, config.contractTier()) != null)
			assignmentMessage = message;
	}

	static boolean readyForNextContract(int currentTick, int completionTick)
	{
		return completionTick >= 0 && currentTick - completionTick >= 2;
	}

	private void resetCompletedContract()
	{
		assignmentMessage = null;
		contactPickerSteps = 0;
		contactSelectedTick = -1;
		layoutReadiness.reset();
		homeownerValidationRecovery.reset();
		complete = false;
		nextContractReady = false;
		completedAtTick = -1;
		completedEffects = 0;
		error = null;
		setupTier = null;
		setupPlanks = 0;
		setupSteelBars = 0;
		knownSupplyHomeowner = null;
		currentContract = false;
		currentContractAtHomeowner = false;
		setupReady = false;
		teleportResourcesPrepared = false;
		contractor = null;
		contractRequestTick = -1;
		contractAttempts = 0;
		lastContractDialogue = "";
		needsTravel = false;
		travelArrived = false;
		travelRequested = false;
		travelReturnState = null;
		travelTarget = null;
		travelToNearestBank = false;
		lastTravelPosition = null;
		pending = null;
		activeWork = null;
		earlyHandoffWork = null;
		workAttempts = 0;
		missingObjectSince = -1;
		turnInRequested = false;
		turnInAttemptTick = 0;
		turnInAttempts = 0;
		clearBankAction();
		clearSackAction();
		clearSawAction();
		if (walker != null)
		{
			walker.cancel();
		}
	}

	private void getSetup()
	{
		if (completedAtTick >= 0)
		{
			resetCompletedContract();
		}
		refreshHotspots();
		WorldPoint position = statePosition;
		MahoganyHomesData preferredTier = assignedTier == null ? config.contractTier() : assignedTier;
		MahoganyHomesContractData assignedContract =
			MahoganyHomesContractData.forHomeowner(assignedHomeowner);
		if (assignedContract == null && assignedHomeowner == null
			&& isActiveContractLayout(hotspotValues))
		{
			assignedContract = MahoganyHomesContractData.forLocation(position);
			if (assignedContract != null)
			{
				rememberAssignment(assignedContract.homeowner, preferredTier);
			}
		}
		boolean assignedAtHome = assignedContract != null && assignedContract.contains(position)
			&& isLoadedContractLayout(hotspotValues);
		if (assignedAtHome)
		{
			knownSupplyHomeowner = assignedHomeowner;
			knownSupplyTier = preferredTier;
			knownSupplyPlanks = assignedContract.requiredPlanks(hotspotValues);
			knownSupplyBars = assignedContract.requiredSteelBars(hotspotValues);
		}
		Setup setup = assignedAtHome
			? new Setup(preferredTier, assignedContract.requiredPlanks(hotspotValues),
				assignedContract.requiredSteelBars(hotspotValues), true)
			: assignedHomeowner != null && assignedHomeowner.equals(knownSupplyHomeowner) && preferredTier == knownSupplyTier
				? new Setup(preferredTier, knownSupplyPlanks, knownSupplyBars, true)
				: selectSetup(preferredTier, false, hotspotValues);
		setupTier = setup.tier;
		setupPlanks = setup.planks;
		setupSteelBars = setup.steelBars;
		currentContract = assignedHomeowner != null || setup.currentContract;
		Rs2NpcModel setupHomeowner = assignedContract == null ? null
			: Microbot.getRs2NpcCache().query().withId(assignedContract.npcId).nearest();
		currentContractAtHomeowner = isCurrentContractAtHomeowner(assignedContract, false,
			setupHomeowner == null ? null : setupHomeowner.getWorldLocation(), position,
			Rs2Tile.getReachableTilesFromTile(position).keySet());

		if (Rs2Player.getRealSkillLevel(Skill.CONSTRUCTION) < setupTier.getConstructionLevel())
		{
			fail(setupTier.getName() + " contracts require "
				+ setupTier.getConstructionLevel() + " Construction. Select a lower contract tier.");
			return;
		}
		if (Rs2Bank.isOpen() && shouldPrepareSackAtBank(setup))
		{
			prepareAtBank(setup);
			return;
		}
		if (hasSetup(setup))
		{
			if (shouldPrepareTeleportResources(Rs2Bank.isOpen(), teleportResourcesPrepared))
			{
				setupReady = false;
				if (!walker.prepareTeleportResourcesAtBank())
				{
					return;
				}
				teleportResourcesPrepared = true;
			}
			if (Rs2Bank.isOpen())
			{
				Rs2Bank.closeBank();
				return;
			}
			if (!prepareAmysSaw())
			{
				return;
			}
			setupReady = true;
			reportSupplies(setup, "ready", null);
			Microbot.status = "Mahogany Homes: " + setupTier.getName() + " setup ready";
			return;
		}
		setupReady = false;
		if (!Rs2Bank.isOpen())
		{
			if (!travelArrived || !travelToNearestBank)
			{
				reportSupplies(setup, "bank: below contract minimum or missing tool", null);
				requestNearestBank(State.GET_SETUP);
				return;
			}
			if (!Rs2Bank.openBank())
			{
				fail("Could not open the bank. Move within reach of a banker or bank booth, then restart.");
			}
			return;
		}
		prepareAtBank(setup);
	}

	static boolean shouldPrepareTeleportResources(boolean bankOpen, boolean resourcesPrepared)
	{
		return bankOpen && !resourcesPrepared;
	}

	static boolean isCurrentContractAtHomeowner(MahoganyHomesContractData contract,
		boolean leelaContract, WorldPoint homeowner, WorldPoint position, Set<WorldPoint> reachable)
	{
		return leelaContract || contract != null
			&& isHomeownerReachable(position,
				homeowner == null ? contract.destination : homeowner, reachable);
	}

	private void prepareAtBank(Setup setup)
	{
		if (awaitBankAction() || awaitSackAction())
		{
			return;
		}
		if (Rs2Inventory.contains(setup.tier.getNotedPlankId()))
		{
			trackBankAction(setup.tier.getNotedPlankId());
			if (!Rs2Bank.depositAll(setup.tier.getNotedPlankId()))
			{
				clearBankAction();
				fail("Could not deposit noted " + setup.tier.getPlankName() + ". Deposit them manually, then restart.");
			}
			return;
		}
		if (Rs2Inventory.contains(ItemID.Cert.STEEL_BAR))
		{
			trackBankAction(ItemID.Cert.STEEL_BAR);
			if (!Rs2Bank.depositAll(ItemID.Cert.STEEL_BAR))
			{
				clearBankAction();
				fail("Could not deposit noted steel bars. Deposit them manually, then restart.");
			}
			return;
		}
		if (!PlankSack.isCarried() && Rs2Bank.hasBankItem(ItemID.PLANK_SACK, 1))
		{
			if (Rs2Inventory.emptySlotCount() == 0)
			{
				if (!depositFirstSupply(setup.tier))
					fail("Free one inventory slot for the plank sack; travel items were retained.");
				return;
			}
			withdraw(ItemID.PLANK_SACK, 1, "plank sack");
			return;
		}
		int wrongPlank = PlankSack.firstWrongInventoryPlank(setup.tier);
		if (wrongPlank >= 0)
		{
			trackBankAction(wrongPlank);
			if (!Rs2Bank.depositAll(wrongPlank))
			{
				clearBankAction();
				fail("Could not deposit planks that do not match your contract tier. Deposit them manually, then restart.");
			}
			return;
		}
		if (PlankSack.isCarried() && PlankSack.hasWrongContents(setup.tier))
		{
			if (Rs2Inventory.emptySlotCount() == 0)
			{
				if (!depositFirstSupply(setup.tier))
				{
					fail("Could not make room to empty the plank sack. Free inventory space at the bank, then restart.");
				}
				return;
			}
			trackSackAction();
			if (!PlankSack.empty())
			{
				clearSackAction();
				fail("Could not empty the plank sack. Check its contents and your free inventory space, then restart.");
			}
			return;
		}
		if (PlankSack.isCarried() && PlankSack.freeCapacity() > 0)
		{
			int loosePlanks = Rs2Inventory.count(setup.tier.getPlankId());
			if (loosePlanks > 0)
			{
				trackSackAction();
				if (!PlankSack.fill())
				{
					clearSackAction();
					fail("Could not fill the plank sack. Check the planks in your inventory and the sack's contents, then restart.");
				}
				return;
			}
			int fillWithdrawal = Math.min(Rs2Inventory.emptySlotCount(),
				PlankSack.fillWithdrawal(PlankSack.count(setup.tier), 0,
					Rs2Bank.count(setup.tier.getPlankId())));
			if (fillWithdrawal > 0)
			{
				withdraw(setup.tier.getPlankId(), fillWithdrawal, setup.tier.getPlankName());
				return;
			}
		}
		MahoganyHomesSupplyPlan plan = supplyPlan(setup);
		reportSupplies(setup, "bank refill", plan);
		if (!plan.minimumStock)
		{
			fail("Bank supplies do not cover this contract: need " + setup.planks + " usable planks and "
				+ setup.steelBars + " steel bars. Add the missing supplies before restarting.");
			return;
		}
		if (!plan.minimumFits)
		{
			fail("Not enough space for this contract's minimum supplies and tools. Free inventory space at the bank; travel items were retained.");
			return;
		}
		if (!hasTool(ItemCollections.HAMMER))
		{
			withdrawTool(ItemCollections.HAMMER, "hammer");
			return;
		}
		if (!hasTool(ItemCollections.SAW))
		{
			withdrawTool(AmysSaw.preferredSawIds(), "saw");
			return;
		}
		int bars = Rs2Inventory.count(ItemID.STEEL_BAR);
		if (bars > plan.barsTarget)
		{
			deposit(ItemID.STEEL_BAR, "surplus steel bars");
			return;
		}
		if (bars < plan.barsTarget)
		{
			int amount = Math.min(plan.barsTarget - bars, Rs2Inventory.emptySlotCount());
			if (amount > 0) withdraw(ItemID.STEEL_BAR, amount, "steel bars");
			else deposit(setup.tier.getPlankId(), "loose planks to reserve steel bar space");
			return;
		}
		int missingLoosePlanks = plan.looseTarget - Rs2Inventory.count(setup.tier.getPlankId());
		if (missingLoosePlanks > 0)
		{
			withdraw(setup.tier.getPlankId(), missingLoosePlanks, setup.tier.getPlankName());
		}
	}

	private void withdrawTool(ItemCollections collection, String name)
	{
		withdrawTool(collection.getItems().stream().mapToInt(Integer::intValue).toArray(), name);
	}

	private void withdrawTool(int[] ids, String name)
	{
		for (int id : ids)
		{
			if (Rs2Bank.hasBankItem(id, 1))
			{
				trackBankAction(id);
				if (!Rs2Bank.withdrawOne(id))
				{
					clearBankAction();
					fail("Could not withdraw " + name + ". Open the bank and check that the item is available, then restart.");
				}
				return;
			}
		}
		fail("No " + name + " found in your bank. Add it, then restart.");
	}

	private boolean shouldPrepareSackAtBank(Setup setup)
	{
		if (!PlankSack.isCarried())
		{
			return Rs2Bank.hasBankItem(ItemID.PLANK_SACK, 1);
		}
		if (PlankSack.hasWrongContents(setup.tier)
			|| PlankSack.firstWrongInventoryPlank(setup.tier) >= 0
			|| Rs2Inventory.contains(ItemID.Cert.STEEL_BAR))
		{
			return true;
		}
		MahoganyHomesSupplyPlan plan = supplyPlan(setup);
		return pendingBankItemId >= 0 || pendingSackQuantity >= 0
			|| PlankSack.freeCapacity() > 0 && (Rs2Inventory.count(setup.tier.getPlankId()) > 0
				|| Rs2Inventory.emptySlotCount() > 0 && Rs2Bank.count(setup.tier.getPlankId()) > 0)
			|| Rs2Inventory.count(setup.tier.getPlankId()) < plan.looseTarget
			|| Rs2Inventory.count(ItemID.STEEL_BAR) != plan.barsTarget;
	}

	private MahoganyHomesSupplyPlan supplyPlan(Setup setup)
	{
		return MahoganyHomesSupplyPlan.plan(PlankSack.isCarried(), PlankSack.count(setup.tier),
			Rs2Inventory.count(setup.tier.getPlankId()), Rs2Inventory.count(ItemID.STEEL_BAR),
			Rs2Inventory.emptySlotCount(), (hasTool(ItemCollections.HAMMER) ? 0 : 1)
				+ (hasTool(ItemCollections.SAW) ? 0 : 1), Rs2Bank.count(setup.tier.getPlankId()),
			Rs2Bank.count(ItemID.STEEL_BAR), setup.planks, setup.steelBars, setup.tier.getSetupPlanks());
	}

	private boolean depositFirstSupply(MahoganyHomesData tier)
	{
		int id = Rs2Inventory.contains(tier.getPlankId()) ? tier.getPlankId()
			: Rs2Inventory.contains(ItemID.STEEL_BAR) ? ItemID.STEEL_BAR : -1;
		return deposit(id, "temporary supply space");
	}

	private void reportSupplies(Setup setup, String reason, MahoganyHomesSupplyPlan plan)
	{
		String report = reason + " tier=" + setup.tier.getName() + " loose=" + Rs2Inventory.count(setup.tier.getPlankId())
			+ " stored=" + PlankSack.count(setup.tier) + " bars=" + Rs2Inventory.count(ItemID.STEEL_BAR)
			+ " minimum=" + setup.planks + "/" + setup.steelBars
			+ (plan == null ? "" : " refill=" + plan.looseTarget + "/" + plan.barsTarget);
		if (!report.equals(lastSupplyReport)) { log.info("[MH_SUPPLIES] {}", report); lastSupplyReport = report; }
	}

	private boolean deposit(int id, String name)
	{
		if (id < 0)
		{
			return false;
		}
		trackBankAction(id);
		if (Rs2Bank.depositAll(id))
		{
			return true;
		}
		clearBankAction();
		fail("Could not deposit " + name + ". Deposit it manually, then restart.");
		return false;
	}

	private static boolean isSetupItem(int id, MahoganyHomesData tier)
	{
		return id == ItemID.PLANK_SACK || id == tier.getPlankId() || id == ItemID.STEEL_BAR
			|| ItemCollections.HAMMER.getItems().contains(id)
			|| ItemCollections.SAW.getItems().contains(id);
	}

	private boolean prepareAmysSaw()
	{
		if (awaitSawAction())
		{
			return false;
		}
		if (AmysSaw.isEquipped())
		{
			return true;
		}
		int id = AmysSaw.inventoryItemId();
		if (id < 0)
		{
			return true;
		}
		trackSawAction(id);
		if (!AmysSaw.prepare(id))
		{
			clearSawAction();
			fail("Could not prepare Amy's saw. Put it in your inventory or equip it, then restart.");
		}
		return false;
	}

	private boolean awaitSackAction()
	{
		if (pendingSackQuantity < 0)
		{
			return false;
		}
		if (bankActionChanged(pendingSackQuantity, PlankSack.total()))
		{
			clearSackAction();
			return false;
		}
		if (readyTick - pendingSackActionTick >= NO_PROGRESS_TICKS)
		{
			if (bankActionFailed(readyTick - pendingSackActionTick, sackActionAttempts))
			{
				fail("Could not confirm that the plank sack's contents changed. Check its contents before restarting.");
			}
			else
			{
				pendingSackQuantity = -1;
			}
		}
		return pendingSackQuantity >= 0;
	}

	private void trackSackAction()
	{
		int quantity = PlankSack.total();
		sackActionAttempts = lastSackQuantity == quantity ? sackActionAttempts + 1 : 1;
		lastSackQuantity = quantity;
		pendingSackQuantity = quantity;
		pendingSackActionTick = readyTick;
	}

	private void clearSackAction()
	{
		pendingSackQuantity = -1;
		lastSackQuantity = -1;
		sackActionAttempts = 0;
	}

	private boolean awaitSawAction()
	{
		if (pendingSawItemId < 0)
		{
			return false;
		}
		if (bankActionChanged(pendingSawItemQuantity, Rs2Inventory.count(pendingSawItemId)))
		{
			clearSawAction();
			return false;
		}
		if (readyTick - pendingSawActionTick >= NO_PROGRESS_TICKS)
		{
			if (bankActionFailed(readyTick - pendingSawActionTick, sawActionAttempts))
			{
				fail("Could not confirm that Amy's saw was equipped or unequipped. Check your tools before restarting.");
			}
			else
			{
				pendingSawItemId = -1;
			}
		}
		return pendingSawItemId >= 0;
	}

	private void trackSawAction(int itemId)
	{
		sawActionAttempts = lastSawItemId == itemId ? sawActionAttempts + 1 : 1;
		lastSawItemId = itemId;
		pendingSawItemId = itemId;
		pendingSawItemQuantity = Rs2Inventory.count(itemId);
		pendingSawActionTick = readyTick;
	}

	private void clearSawAction()
	{
		pendingSawItemId = -1;
		lastSawItemId = -1;
		sawActionAttempts = 0;
	}

	private void withdraw(int id, int amount, String name)
	{
		if (!Rs2Bank.hasBankItem(id, amount))
		{
			int missing = amount - Rs2Bank.count(id);
			fail(missing > 0 ? "Missing " + missing + " " + name + ". Add them to your bank, then restart."
				: "Could not read your bank contents. Close and reopen the bank, then restart.");
			return;
		}
		trackBankAction(id);
		boolean withdrawn = amount == 1 ? Rs2Bank.withdrawOne(id) : Rs2Bank.withdrawX(id, amount);
		if (!withdrawn)
		{
			clearBankAction();
			fail("Could not withdraw " + amount + " " + name + ". Check the available amount and free inventory space, then restart.");
		}
	}

	private boolean awaitBankAction()
	{
		if (pendingBankItemId < 0)
		{
			return false;
		}
		if (bankActionChanged(pendingBankItemQuantity, Rs2Inventory.count(pendingBankItemId)))
		{
			clearBankAction();
			return false;
		}
		if (readyTick - pendingBankActionTick >= NO_PROGRESS_TICKS)
		{
			if (bankActionFailed(readyTick - pendingBankActionTick, bankActionAttempts))
			{
				fail("Could not confirm the bank transfer. Check your inventory and bank before restarting.");
			}
			else
			{
				releaseBankAction();
			}
		}
		return true;
	}

	private void trackBankAction(int itemId)
	{
		bankActionAttempts = lastBankActionItemId == itemId ? bankActionAttempts + 1 : 1;
		lastBankActionItemId = itemId;
		pendingBankItemId = itemId;
		pendingBankItemQuantity = Rs2Inventory.count(itemId);
		pendingBankActionTick = readyTick;
	}

	static boolean bankActionChanged(int previousSlots, int currentSlots)
	{
		return previousSlots != currentSlots;
	}

	static boolean bankActionFailed(int elapsedTicks, int attempts)
	{
		return elapsedTicks >= NO_PROGRESS_TICKS && attempts >= MAX_ATTEMPTS;
	}

	private void clearBankAction()
	{
		releaseBankAction();
		lastBankActionItemId = -1;
		bankActionAttempts = 0;
	}

	private void releaseBankAction()
	{
		pendingBankItemId = -1;
	}

	private static boolean hasSetup(Setup setup)
	{
		return hasTool(ItemCollections.HAMMER) && hasTool(ItemCollections.SAW)
			&& MahoganyHomesSupplyPlan.ready(setup.planks, setup.steelBars,
				Rs2Inventory.count(setup.tier.getPlankId()), PlankSack.count(setup.tier),
				Rs2Inventory.count(ItemID.STEEL_BAR));
	}

	private void getContract()
	{
		if (completedAtTick >= 0)
		{
			resetCompletedContract();
		}
		setupTier = tierForContractRequest(setupTier, config.contractTier());
		String observed = assignmentMessage;
		assignmentMessage = null;
		Assignment observedAssignment = parseAssignment(observed, setupTier);
		if (observedAssignment != null)
		{
			rememberAssignment(observedAssignment.homeowner, observedAssignment.tier);
			contractAcquired = true;
			log.info("[MH_CONTRACT] acquired source={} tier={} homeowner={}",
				config.useAstralContact() ? "Astral Contact" : "contractor", assignedTier, assignedHomeowner);
		}
		contractDialogueClosed = contractAcquired && !Rs2Dialogue.isInDialogue();
		if (contractDialogueClosed)
		{
			return;
		}

		WorldPoint position = statePosition;
		if (contractor == null)
		{
			contractor = MahoganyHomesContractorData.nearest(position);
		}
		if (!config.useAstralContact() && (position.getPlane() != contractor.location.getPlane()
			|| position.distanceTo(contractor.location) > 8))
		{
			requestTravel(contractor.location, State.GET_CONTRACT);
			return;
		}
		if (config.useAstralContact() && AstralContact.pickerOpen())
		{
			if (contactSelectedTick >= 0 && readyTick - contactSelectedTick < NO_PROGRESS_TICKS) return;
			if (++contactPickerSteps > 24)
			{
				fail("Could not select Amy in Astral Contact. Check the character picker and restart.");
				return;
			}
			AstralContact.Selection selection = AstralContact.selectAmy();
			if (selection == AstralContact.Selection.CONTACTED)
			{
				contactSelectedTick = readyTick;
				contractRequestTick = readyTick;
				log.info("[MH_CONTRACT] Astral Contact: selected Amy; requested tier={}", setupTier);
			}
			Microbot.status = "Mahogany Homes: Astral Contact - selecting Amy";
			return;
		}

		String dialogue = Rs2Dialogue.getDialogueText();
		if (!dialogue.equals(lastContractDialogue))
		{
			lastContractDialogue = dialogue;
			contractRequestTick = readyTick;
		}
		Assignment assignment = parseAssignment(dialogue, setupTier);
		if (assignment != null)
		{
			rememberAssignment(assignment.homeowner, assignment.tier);
			contractAcquired = true;
			if (Rs2Dialogue.hasContinue())
			{
				Rs2Dialogue.clickContinue();
			}
			return;
		}
		// A selected option can become a Continue dialogue between widget reads.
		if (Rs2Dialogue.hasContinue())
		{
			Rs2Dialogue.clickContinue();
			return;
		}
		if (Rs2Dialogue.hasSelectAnOption())
		{
			if (!Rs2Dialogue.hasDialogueOption(setupTier.getContractOption()))
			{
				if (contractOptionFailed(readyTick, contractRequestTick,
					Rs2Dialogue.hasSelectAnOption(), Rs2Dialogue.hasContinue()))
				{
					fail("Could not select " + setupTier.getContractOption() + ". Close the dialogue and restart.");
				}
				return;
			}
			if (Rs2Dialogue.clickOption(setupTier.getContractOption()))
			{
				contractRequestTick = readyTick;
			}
			return;
		}
		if (Rs2Dialogue.hasContinue())
		{
			Rs2Dialogue.clickContinue();
			return;
		}
		// The Lunar cast animation can outlast the normal dialogue retry interval.
		int requestWaitTicks = config.useAstralContact() ? 12 : NO_PROGRESS_TICKS;
		if (contractAttempts > 0 && readyTick - contractRequestTick < requestWaitTicks)
		{
			return;
		}
		if (contractAttempts >= MAX_ATTEMPTS)
		{
			fail(config.useAstralContact()
				? "Could not confirm a new contract from Amy through Astral Contact. Check the dialogue and restart."
				: "Could not confirm a new contract from " + contractor.name + ". Speak to them to check your assignment, then restart.");
			return;
		}
		if (config.useAstralContact())
		{
			String missing = AstralContact.missingRequirement();
			if (missing != null) { fail(missing); return; }
			if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return; }
			contractAttempts++;
			contractRequestTick = readyTick;
			contactPickerSteps = 0;
			contactSelectedTick = -1;
			boolean cast = net.runelite.client.plugins.microbot.util.magic.Rs2Magic.cast(
				net.runelite.client.plugins.skillcalculator.skills.MagicAction.NPC_CONTACT);
			log.info("[MH_CONTRACT] Astral Contact cast={} attempt={} tier={}", cast, contractAttempts, setupTier);
			Microbot.status = "Mahogany Homes: casting Astral Contact for " + setupTier.getName();
			return;
		}

		Rs2NpcModel npc = Microbot.getRs2NpcCache().query().withName(contractor.name).nearest();
		if (npc == null || !npc.click("Contract"))
		{
            contractAttempts++;
            skipEarlyHandoff = true;
            requestTravel(contractor.location, State.GET_CONTRACT);
			return;
		}
		contractRequestTick = readyTick;
		contractAttempts++;
		Microbot.status = "Mahogany Homes: requesting a " + setupTier.getName() + " contract";
	}

	static boolean contractOptionTimedOut(int tick, int requestedAt)
	{
		return requestedAt >= 0 && tick - requestedAt >= NO_PROGRESS_TICKS;
	}

	static boolean contractOptionFailed(int tick, int requestedAt,
		boolean optionsStillVisible, boolean continueVisible)
	{
		return optionsStillVisible && !continueVisible && contractOptionTimedOut(tick, requestedAt);
	}

	static MahoganyHomesData tierForContractRequest(
		MahoganyHomesData currentTier, MahoganyHomesData configuredTier)
	{
		return currentTier == null ? configuredTier : currentTier;
	}

	private void ready()
	{
		Microbot.status = "Mahogany Homes: " + assignedTier.getName()
			+ " contract assigned to " + assignedHomeowner;
	}

	private void goToContract()
	{
		MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
		if (contract == null)
		{
			fail("Travel to " + assignedHomeowner + "'s home is not supported. Report this assignment.");
			return;
		}
		if (contractArrived)
		{
			Microbot.status = "Mahogany Homes: arrived at " + assignedHomeowner + "'s contract";
			return;
		}

		WorldPoint position = statePosition;
		Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(position).keySet();
		Rs2NpcModel homeowner = Microbot.getRs2NpcCache().query().withId(contract.npcId).nearest();
		WorldPoint homeownerLocation = homeowner == null ? null : homeowner.getWorldLocation();
		if (homeownerLocation != null)
		{
			if (isHomeownerReachable(position, homeownerLocation, reachable))
			{
				walker.cancel();
				needsTravel = false;
				contractArrived = true;
				Microbot.status = "Mahogany Homes: arrived at " + assignedHomeowner + "'s contract";
				return;
			}
			contractArrivalWaitTick = -1;
			requestTravel(homeownerApproach(position, homeownerLocation,
				Rs2Tile.getReachableTilesFromTile(homeownerLocation, 1).keySet()), State.GO_TO_CONTRACT);
			return;
		}
		if (position.getPlane() == contract.destination.getPlane()
			&& position.distanceTo(contract.destination) <= 3)
		{
			if (contractArrivalWaitTick < 0)
			{
				contractArrivalWaitTick = readyTick;
			}
			else if (readyTick - contractArrivalWaitTick >= NO_PROGRESS_TICKS)
			{
				fail("Could not find " + assignedHomeowner + " at the assigned home. Check the building and floor, then restart.");
			}
			return;
		}

		contractArrivalWaitTick = -1;
		requestTravel(contract.destination, State.GO_TO_CONTRACT);
	}

	static boolean isHomeownerInRange(WorldPoint player, WorldPoint homeowner)
	{
		return player != null && homeowner != null && player.getPlane() == homeowner.getPlane()
			&& player.distanceTo(homeowner) <= 8;
	}

	static boolean isHomeownerReachable(WorldPoint player, WorldPoint homeowner,
		Set<WorldPoint> reachable)
	{
		return isHomeownerInRange(player, homeowner) && reachable != null && reachable.contains(homeowner);
	}

	static WorldPoint homeownerApproach(WorldPoint player, WorldPoint homeowner,
		Set<WorldPoint> homeownerArea)
	{
		return homeownerArea.stream()
			.filter(tile -> !tile.equals(homeowner))
			.min(Comparator.comparingInt((WorldPoint tile) -> tile.distanceTo2D(player))
				.thenComparingInt(WorldPoint::getX)
				.thenComparingInt(WorldPoint::getY))
			.orElse(homeowner);
	}

	static Assignment parseAssignment(String dialogue, MahoganyHomesData fallbackTier)
	{
		if (dialogue == null)
		{
			return null;
		}
		String text = Rs2UiHelper.stripTagsToSpace(dialogue).trim();
		Matcher announcement = CONTRACT_ANNOUNCEMENT.matcher(text);
		if (announcement.find())
		{
			String homeowner = announcement.group(2);
			return new Assignment(Character.toUpperCase(homeowner.charAt(0)) + homeowner.substring(1),
				MahoganyHomesData.fromName(announcement.group(1)));
		}
		Matcher homeMatcher = CONTRACT_HOME.matcher(text);
		if (!homeMatcher.find())
		{
			return null;
		}
		Matcher tierMatcher = CONTRACT_TIER.matcher(text);
		MahoganyHomesData tier = tierMatcher.find()
			? MahoganyHomesData.fromName(tierMatcher.group(1)) : fallbackTier;
		if (tier == null)
		{
			return null;
		}
		String homeowner = homeMatcher.group(1);
		return new Assignment(Character.toUpperCase(homeowner.charAt(0)) + homeowner.substring(1), tier);
	}

	private void loadAssignment()
	{
		String homeowner = configManager.getRSProfileConfiguration(
			MahoganyHomesConfig.GROUP, ASSIGNED_HOME_KEY);
		String tierName = configManager.getRSProfileConfiguration(
			MahoganyHomesConfig.GROUP, ASSIGNED_TIER_KEY);
		MahoganyHomesData tier = tierName == null ? null : MahoganyHomesData.fromName(tierName);
		if (homeowner == null || homeowner.isBlank() || tier == null)
		{
			clearAssignment();
			return;
		}
		assignedHomeowner = homeowner;
		assignedTier = tier;
	}

	private void rememberAssignment(String homeowner, MahoganyHomesData tier)
	{
		if (!setupMatchesAssignment(setupTier, tier))
		{
			setupReady = false;
		}
		if (homeowner.equals(assignedHomeowner) && tier == assignedTier)
		{
			return;
		}
		assignedHomeowner = homeowner;
		assignedTier = tier;
		contractArrived = false;
		layoutReadiness.reset();
		homeownerValidationRecovery.reset();
		contractValidated = false;
		contractArrivalWaitTick = -1;
		configManager.setRSProfileConfiguration(MahoganyHomesConfig.GROUP, ASSIGNED_HOME_KEY, homeowner);
		configManager.setRSProfileConfiguration(MahoganyHomesConfig.GROUP, ASSIGNED_TIER_KEY, tier.getName());
	}

	static boolean setupMatchesAssignment(MahoganyHomesData setup, MahoganyHomesData assignment)
	{
		return setup != null && setup == assignment;
	}

	private void clearAssignment()
	{
		layoutReadiness.reset();
		homeownerValidationRecovery.reset();
		knownSupplyHomeowner = null;
		knownSupplyTier = null;
		assignedHomeowner = null;
		assignedTier = null;
		contractAcquired = false;
		contractDialogueClosed = false;
		contractArrived = false;
		contractValidated = false;
		contractArrivalWaitTick = -1;
		configManager.unsetRSProfileConfiguration(MahoganyHomesConfig.GROUP, ASSIGNED_HOME_KEY);
		configManager.unsetRSProfileConfiguration(MahoganyHomesConfig.GROUP, ASSIGNED_TIER_KEY);
	}

	private void validateContract()
	{
		refreshHotspots();
		MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
		if (contract == null)
		{
			fail("The contract for " + assignedHomeowner + " is not supported. Report this assignment.");
			return;
		}
		validateAssignedContract(contract);
	}

	private void validateAssignedContract(MahoganyHomesContractData contract)
	{
		WorldPoint position = statePosition;
		boolean pendingLayout = layoutReadiness.pending();
		ContractLayoutReadiness.Decision readiness = layoutReadiness.check(
            layoutContextReady(contract, position), isLoadedContractLayout(hotspotValues), readyTick);
		if (readiness != ContractLayoutReadiness.Decision.READY)
		{
			if (!pendingLayout || readiness == ContractLayoutReadiness.Decision.EXPIRED)
				log.info("mahogany_layout | phase={} at={} target={} values={}", readiness,
					position, contract.destination, Arrays.toString(hotspotValues));
			if (readiness == ContractLayoutReadiness.Decision.EXPIRED)
			{
				fail("Could not load the repair layout for " + contract.homeowner
					+ " after approaching the house. Report this assignment for review.");
				return;
			}
			Microbot.status = "Mahogany Homes: waiting for " + contract.homeowner + "'s repair layout";
			if (position != null && !position.equals(contract.destination))
				requestTravel(contract.destination, State.VALIDATE_CONTRACT);
			return;
		}
		Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(position).keySet();
		Rs2NpcModel homeowner = Microbot.getRs2NpcCache().query().withId(contract.npcId).nearest();
		WorldPoint homeownerLocation = homeowner == null ? null : homeowner.getWorldLocation();
		HomeownerValidationRecovery.Decision access = homeownerValidationRecovery.check(
			isHomeownerReachable(position, homeownerLocation, reachable), readyTick);
		if (access != HomeownerValidationRecovery.Decision.READY)
		{
			log.info("mahogany_validation_access | phase={} homeowner={} at={} liveAt={} npcTile={} reachableTiles={} tick={}",
				access, contract.homeowner, position, Rs2Player.getWorldLocation(), homeownerLocation,
				reachable.size(), readyTick);
			if (access == HomeownerValidationRecovery.Decision.EXPIRED)
			{
				fail("Could not reach " + contract.homeowner + " after retrying contract validation. Check the door or obstacle, then restart.");
				return;
			}
			Microbot.status = "Mahogany Homes: checking access to " + contract.homeowner;
			if (access == HomeownerValidationRecovery.Decision.APPROACH)
			{
				skipEarlyHandoff = true;
				requestTravel(homeownerLocation == null ? contract.destination
					: homeownerApproach(position, homeownerLocation,
						Rs2Tile.getReachableTilesFromTile(homeownerLocation, 1).keySet()), State.VALIDATE_CONTRACT);
			}
			return;
		}
		if (!hasTool(ItemCollections.HAMMER) || !hasTool(ItemCollections.SAW))
		{
			fail("Missing a usable " + (!hasTool(ItemCollections.HAMMER) ? (!hasTool(ItemCollections.SAW) ? "hammer and saw" : "hammer") : "saw")
				+ ". Put the missing tool in your inventory or equip a supported alternative, then restart.");
			return;
		}
		int planks = contract.requiredPlanks(hotspotValues);
		knownSupplyHomeowner = assignedHomeowner;
		knownSupplyTier = assignedTier;
		knownSupplyPlanks = planks;
		knownSupplyBars = contract.requiredSteelBars(hotspotValues);
		if (PlankSack.available(assignedTier) < planks)
		{
			fail("Missing " + (planks - PlankSack.available(assignedTier)) + " " + assignedTier.getPlankName()
				+ " for this contract. Withdraw them, then restart.");
			return;
		}
		int bars = contract.requiredSteelBars(hotspotValues);
		if (Rs2Inventory.count(ItemID.STEEL_BAR) < bars)
		{
			fail("Missing " + (bars - Rs2Inventory.count(ItemID.STEEL_BAR))
				+ " steel bars for this contract. Withdraw them, then restart.");
			return;
		}
		contractValidated = true;
		Microbot.status = "Mahogany Homes: " + contract.homeowner + " contract verified";
	}

	private void doContract()
	{
		refreshHotspots();
		MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
		if (contract == null)
		{
			fail("The contract for " + assignedHomeowner + " is not supported. Report this assignment.");
			return;
		}
		List<Work> work = contract.work;
		if (!isActiveOrFixedLayout(hotspotValues))
		{
			fail("The repair state at " + contract.homeowner + "'s home was not recognised. Check the contract and report the issue if it repeats.");
			return;
		}
		if (allFixed())
		{
			Microbot.status = "Mahogany Homes: " + contract.homeowner
				+ " contract furniture complete";
			return;
		}
		if (pending != null)
		{
			observePendingWork();
			return;
		}

		WorldPoint position = statePosition;
		Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(position).keySet();
		Work next = workAttempts > 0 && activeWork != null
			? activeWork : nextWork(work, hotspotValues, position, reachable);
        Work handedOff = earlyHandoffWork;
        earlyHandoffWork = null;
        if (handedOff != null && !isActionable(hotspotValues[handedOff.hotspot])) handedOff = null;
        if (handedOff != null) {
            if (!isFurnitureInteractionReady(handedOff)) {
                resumeWorkApproach("readiness-lost");
                return;
            }
            next = handedOff;
        }
		if (next == null)
		{
			Work otherFloor = nextWork(work, hotspotValues, 1 - position.getPlane());
			if (otherFloor != null)
			{
				WorldPoint landing = contract.landing(otherFloor.location.getPlane());
				requestTravel(landing, State.DO_CONTRACT);
			}
			return;
		}
		boolean accessReachable = next.access == null || reachable.contains(next.access);
		if (needsWorkAccess(position, next, workAttempts, accessReachable))
		{
			// A radius may include the wrong side of a room door. Safe handoff remains available.
			requestTravel(next.access, State.DO_CONTRACT, 0);
			return;
		}

		String action = actionFor(hotspotValues[next.hotspot]);
		Rs2TileObjectModel object = exactObject(next);
		if (object == null || !hasAction(object.getObjectComposition(), action))
		{
            if (handedOff != null) {
                resumeWorkApproach("object-or-action-changed");
                return;
            }
			if (missingObjectSince < 0)
			{
				missingObjectSince = readyTick;
			}
			else if (readyTick - missingObjectSince >= NO_PROGRESS_TICKS)
			{
				fail("Could not find or select the next furniture repair. Check the assigned building and floor, then restart.");
			}
			return;
		}

		missingObjectSince = -1;
		pending = next;
		activeWork = next;
		pendingValue = hotspotValues[next.hotspot];
		lastWorkProgressTick = readyTick;
		workAttempts++;
        boolean dispatched = object.click(action);
        if (handedOff != null && !dispatched) {
            pending = activeWork = null;
            workAttempts = 0;
            resumeWorkApproach("dispatch-failed");
            return;
        }
		Microbot.status = "Mahogany Homes: " + action + " " + object.getName();
	}

	private void observePendingWork()
	{
		int value = hotspotValues[pending.hotspot];
		if (value != pendingValue)
		{
			completedEffects++;
			pending = null;
			activeWork = null;
			workAttempts = 0;
			lastWorkProgressTick = readyTick;
			return;
		}
		if (Rs2Player.isMoving() || Rs2Player.isAnimating())
		{
			lastWorkProgressTick = readyTick;
			return;
		}
		if (readyTick - lastWorkProgressTick < NO_PROGRESS_TICKS)
		{
			return;
		}
		if (workAttempts >= MAX_ATTEMPTS)
		{
			fail("Could not confirm the furniture repair. Check your supplies and the unfinished furniture before restarting.");
			return;
		}
		pending = null;
	}

	private void requestTravel(WorldPoint target, State returnState)
	{
		requestTravel(target, returnState, arrivalDistanceFor(returnState));
	}

	private void requestTravel(WorldPoint target, State returnState, int arrivalDistance)
	{
		travelTarget = target;
		travelToNearestBank = false;
		prepareTravel(returnState, arrivalDistance);
	}

	private void requestNearestBank(State returnState)
	{
		travelTarget = null;
		travelToNearestBank = true;
		prepareTravel(returnState);
	}

	private void prepareTravel(State returnState)
	{
		prepareTravel(returnState, arrivalDistanceFor(returnState));
	}

	private void prepareTravel(State returnState, int arrivalDistance)
	{
		snapshotGuard = new TransitionSnapshotGuard();
        travelArrivalDistance = arrivalDistance;
        earlyHandoffWork = null;
        interactionProbeTick = -1;
        travelStartedAt = System.currentTimeMillis();
		travelOrigin = statePosition;
		net.runelite.client.plugins.microbot.util.walker.WebWalkLog.spInfo(
			"mahogany_journey | event=start from={} to={} purpose={} homeowner={} arrivalDistance={}",
			travelOrigin, travelToNearestBank ? "nearest-bank" : travelTarget, returnState, assignedHomeowner,
            travelArrivalDistance);
        walker.completionCondition = this::isTravelInteractionReady;
		travelReturnState = returnState;
		travelRequested = false;
		travelArrived = false;
		needsTravel = true;
		lastTravelPosition = statePosition;
		lastTravelProgressTick = readyTick;
	}

	private void travel()
	{
        String routeStatus = walker.status();
		// Walker advancement can block through a teleport; discard the pre-walk position.
		if (!captureUsableSnapshot()) return;
		WorldPoint position = statePosition;
		if (hasTravelArrived(travelRequested, routeStatus))
		{
			walker.cancel();
            skipEarlyHandoff = false;
            net.runelite.client.plugins.microbot.util.walker.WebWalkLog.spInfo(
                "mahogany_travel_return | state={} elapsedMs={} at={}",
                travelReturnState, System.currentTimeMillis() - travelStartedAt, position);
			net.runelite.client.plugins.microbot.util.walker.WebWalkLog.spInfo(
				"mahogany_journey | event=arrived from={} to={} at={} purpose={} elapsedMs={}",
				travelOrigin, travelToNearestBank ? "nearest-bank" : travelTarget, position,
				travelReturnState, System.currentTimeMillis() - travelStartedAt);
			needsTravel = false;
			travelArrived = true;
			return;
		}
		if (!position.equals(lastTravelPosition))
		{
			lastTravelPosition = position;
			lastTravelProgressTick = readyTick;
		}
		if (!travelRequested)
		{
			boolean accepted = travelToNearestBank
				? walker.walkToNearestBank() : walker.walkTo(travelTarget, travelArrivalDistance);
			if (!accepted)
			{
				fail("Could not start travel to " + travelDescription() + " with Microbot's walker, then restart.");
				return;
			}
			travelRequested = true;
			return;
		}
		if ("BLOCKED".equals(routeStatus))
		{
			fail("Travel to " + travelDescription() + " stopped by Microbot's walker. Check the route and restart.");
		}
		else if (readyTick - lastTravelProgressTick >= 30)
		{
			fail("Could not make progress towards " + travelDescription() + ". Check for an obstacle or open dialogue, then restart.");
		}
	}

    private boolean isTravelInteractionReady()
    {
        if (skipEarlyHandoff || travelToNearestBank || interactionProbeTick == readyTick) return false;
        interactionProbeTick = readyTick;
        if (travelReturnState != State.DO_CONTRACT && travelReturnState != State.VALIDATE_CONTRACT
            && travelReturnState != State.GET_CONTRACT && travelReturnState != State.GO_TO_CONTRACT
            && travelReturnState != State.TURN_IN_CONTRACT) return false;
        var clientThread = Microbot.getClientThread();
        if (clientThread == null) return false;
        return clientThread.runOnClientThreadOptional(() -> {
            net.runelite.api.Client client = Microbot.getClient();
            if (client == null || client.getGameState() != GameState.LOGGED_IN
                || client.getLocalPlayer() == null) return false;
            if (travelReturnState == State.VALIDATE_CONTRACT) {
                refreshHotspots();
                return layoutContextReady(MahoganyHomesContractData.forHomeowner(assignedHomeowner),
                        Rs2Player.getWorldLocation()) && isLoadedContractLayout(hotspotValues);
            }
            if (travelReturnState == State.DO_CONTRACT) {
                Work candidate = findReadyFurniture(null);
                if (candidate != null) {
                    earlyHandoffWork = candidate;
                    log.info("mahogany_work_handoff | phase=ready object={} access={} routeTarget={}",
                        candidate.objectId, candidate.access, travelTarget);
                    return true;
                }
                return false;
            }
            Rs2NpcModel npc;
            String action;
            if (travelReturnState == State.GET_CONTRACT) {
                if (contractor == null) return false;
                npc = Microbot.getRs2NpcCache().query().withName(contractor.name).nearest();
                action = "Contract";
            } else {
                MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
                if (contract == null) return false;
                npc = Microbot.getRs2NpcCache().query().withId(contract.npcId).nearest();
                action = "Talk-to";
            }
            net.runelite.api.NPC liveNpc = npc == null ? null : npc.getNpc();
            if (liveNpc == null) return false;
            WorldPoint player = Rs2Player.getWorldLocation();
            WorldPoint target = npc.getWorldLocation();
            if (!isHomeownerInRange(player, target)) return false;
            net.runelite.api.NPCComposition composition = liveNpc.getTransformedComposition();
            boolean hasAction = composition != null && composition.getActions() != null
                && Arrays.asList(composition.getActions()).contains(action);
            Shape hull = npc.getConvexHull();
            Rectangle viewport = new Rectangle(client.getViewportXOffset(),
                client.getViewportYOffset(), client.getViewportWidth(), client.getViewportHeight());
            boolean ready = interactionHandoffReady(player, target, hasAction, clickboxVisible(hull, viewport),
                Rs2Tile.getReachableTilesFromTile(player, 8).keySet());
            if (ready) {
                log.info("mahogany_npc_handoff | phase=ready action={} npc={} at={} npcTile={} routeTarget={}",
                    action, npc.getId(), player, target, travelTarget);
            }
            return ready;
        }).orElse(false);
    }

    static boolean interactionHandoffReady(WorldPoint player, WorldPoint target, boolean hasAction,
                                           boolean clickable, Set<WorldPoint> reachable)
    {
        return isHomeownerInRange(player, target) && hasAction && clickable
            && reachable != null && reachable.contains(target);
    }

    static boolean clickboxVisible(Shape clickbox, Rectangle viewport)
    {
        if (clickbox == null || viewport == null || viewport.isEmpty()
            || clickbox.getBounds().isEmpty() || !clickbox.getBounds().intersects(viewport)) return false;
        // RuneLite's SimplePolygon intersection misses shapes wholly inside the rectangle.
        // Use its path with AWT geometry, retaining a real shape test for clipped/offscreen models.
        return new Area(clickbox).intersects(viewport);
    }

    private boolean isFurnitureInteractionReady(Work work) {
        return work != null && findReadyFurniture(work) == work;
    }

    private Work findReadyFurniture(Work preferred) {
        var clientThread = Microbot.getClientThread();
        if (clientThread == null || pending != null || workAttempts > 0) return null;
        return clientThread.runOnClientThreadOptional(() -> {
            var client = Microbot.getClient();
            if (client == null || client.getGameState() != GameState.LOGGED_IN || client.getLocalPlayer() == null
                || client.getTopLevelWorldView() == null || client.getTopLevelWorldView().isInstance()
                || Microbot.pauseAllScripts.get() || InputArbiter.isHuman()
                || Rs2Player.isAnimating() || Rs2Player.isInteracting()) return null;
            WorldPoint player = Rs2Player.getWorldLocation();
            MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
            if (player == null || contract == null || !contract.contains(player) || travelTarget == null
                || player.getPlane() != travelTarget.getPlane()) return null;
            refreshHotspots();
            Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(player, 8).keySet();
            Rectangle viewport = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
                client.getViewportWidth(), client.getViewportHeight());
            Work best = null;
            for (Work work : contract.work) {
                if ((preferred != null && work != preferred) || work.access == null
                    || !isActionable(hotspotValues[work.hotspot]) || !reachable.contains(work.access)) continue;
                Rs2TileObjectModel object = exactObject(work);
                if (object == null) continue;
                Shape clickbox = object.getClickbox();
                if (furnitureHandoffReady(player, travelTarget, work, object.getWorldLocation(), object.getId(),
                    hasAction(object.getObjectComposition(), actionFor(hotspotValues[work.hotspot])),
                    clickboxVisible(clickbox, viewport), reachable)
                    && (best == null || player.distanceTo2D(work.location) < player.distanceTo2D(best.location))) best = work;
            }
            return best;
        }).orElse(null);
    }

    static boolean furnitureHandoffReady(WorldPoint player, WorldPoint routeTarget, Work work,
                                         WorldPoint objectLocation, int objectId, boolean action,
                                         boolean clickable, Set<WorldPoint> reachable) {
        return player != null && routeTarget != null && work != null && work.access != null
            && work.objectId == objectId && work.location.equals(objectLocation)
            && player.getPlane() == routeTarget.getPlane() && player.getPlane() == work.location.getPlane()
            && work.access.getPlane() == player.getPlane() && player.distanceTo2D(work.location) <= 8
            && action && clickable && reachable != null && reachable.contains(work.access);
    }

    private void resumeWorkApproach(String reason) {
        log.info("mahogany_work_handoff | phase=resume reason={} target={}", reason, travelTarget);
        skipEarlyHandoff = true;
        requestTravel(travelTarget, State.DO_CONTRACT);
    }

	private String travelDescription()
	{
		return travelToNearestBank ? "a bank" : travelReturnState != State.GET_CONTRACT && assignedHomeowner != null
			? assignedHomeowner + "'s home" : "the contractor";
	}

	static boolean hasTravelArrived(boolean requested, String walkerStatus)
	{
		return requested && "ARRIVED".equals(walkerStatus);
	}

    static int arrivalDistanceFor(State returnState)
    {
        // Even one tile of tolerance can finish outside a closed door. The reachable-NPC
        // completion condition still hands off early once the intended interaction is safe.
        return returnState == State.GO_TO_CONTRACT || returnState == State.TURN_IN_CONTRACT
                || returnState == State.VALIDATE_CONTRACT ? 0 : 4;
    }

    static boolean layoutContextReady(MahoganyHomesContractData contract, WorldPoint position)
    {
        return contract != null && position != null && contract.contains(position)
                && position.getPlane() == contract.destination.getPlane();
    }

	private void turnIn()
	{
		refreshHotspots();
		if (!allFixed())
		{
			fail("The contract still has unfinished furniture. Check the remaining repairs, then restart.");
			return;
		}
		if (Rs2Dialogue.hasContinue())
		{
			Rs2Dialogue.clickContinue();
			return;
		}
		if (Rs2Dialogue.hasSelectAnOption())
		{
			if (!Rs2Dialogue.hasDialogueOption("Yes, I'd love a cuppa."))
			{
				fail("An unexpected dialogue appeared while finishing the contract. Close it and speak to " + assignedHomeowner + " to check the contract.");
				return;
			}
			Rs2Dialogue.clickOption("Yes, I'd love a cuppa.");
			return;
		}
		if (complete || Rs2Dialogue.isInDialogue())
		{
			return;
		}

		MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
		if (contract == null)
		{
			fail("The contract for " + assignedHomeowner + " is not supported. Report this assignment.");
			return;
		}
		String homeownerName = contract.homeowner;
		int homeownerId = contract.npcId;
		WorldPoint target = contract.destination;
		WorldPoint position = statePosition;
		if (position.getPlane() != target.getPlane() || position.distanceTo(target) > 8)
		{
			requestTravel(target, State.TURN_IN_CONTRACT);
			return;
		}
		Rs2NpcModel homeowner = Microbot.getRs2NpcCache().query().withId(homeownerId).nearest();
		if (homeowner == null)
		{
			fail("Could not find " + homeownerName + " to finish the contract. Move near them, then restart.");
			return;
		}
		Set<WorldPoint> reachable = Rs2Tile.getReachableTilesFromTile(position).keySet();
		WorldPoint homeownerLocation = homeowner.getWorldLocation();
		if (homeownerLocation == null)
		{
			requestTravel(target, State.TURN_IN_CONTRACT);
			return;
		}
		if (!isHomeownerReachable(position, homeownerLocation, reachable))
		{
			requestTravel(homeownerApproach(position, homeownerLocation,
				Rs2Tile.getReachableTilesFromTile(homeownerLocation, 1).keySet()),
				State.TURN_IN_CONTRACT);
			return;
		}
		if (turnInRequested && readyTick - turnInAttemptTick < NO_PROGRESS_TICKS)
		{
			return;
		}
		if (turnInAttempts >= MAX_ATTEMPTS)
		{
			fail("Could not confirm completion of " + homeownerName + "'s contract. Speak to them to check its status before restarting.");
			return;
		}
		if (!homeowner.click("Talk-to"))
		{
            turnInAttempts++;
            skipEarlyHandoff = true;
            requestTravel(target, State.TURN_IN_CONTRACT);
			return;
		}
		turnInRequested = true;
		turnInAttemptTick = readyTick;
		turnInAttempts++;
		Microbot.status = "Mahogany Homes: turning in " + homeownerName + "'s contract";
	}

	static boolean isCompletionMessage(String message)
	{
		return message != null && COMPLETION_MESSAGE.matcher(message).matches();
	}

	private void refreshHotspots()
	{
		hotspotValues = Arrays.stream(HOTSPOT_VARBITS).map(Microbot::getVarbitValue).toArray();
	}

	private Rs2TileObjectModel exactObject(Work work)
	{
		return Microbot.getRs2TileObjectCache().query().withId(work.objectId).toList().stream()
			.filter(object -> work.location.equals(object.getWorldLocation()))
			.findFirst()
			.orElse(null);
	}

	private static boolean hasAction(ObjectComposition composition, String action)
	{
		return composition != null && action != null && composition.getActions() != null
			&& Arrays.stream(composition.getActions()).anyMatch(action::equalsIgnoreCase);
	}

	private static boolean hasTool(ItemCollections collection)
	{
		int[] ids = collection.getItems().stream().mapToInt(Integer::intValue).toArray();
		return Rs2Inventory.contains(ids) || Rs2Equipment.isWearing(ids);
	}

	static boolean isSupportedCurrentContract(int[] values)
	{
		return isSupportedLayout(values)
			&& Arrays.stream(values).anyMatch(MahoganyHomesScript::isActionable);
	}

	static boolean isActiveContractLayout(int[] values)
	{
		return values != null && values.length == HOTSPOT_VARBITS.length
			&& Arrays.stream(values).allMatch(MahoganyHomesScript::isKnownHotspotValue)
			&& Arrays.stream(values).anyMatch(MahoganyHomesScript::isActionable);
	}

	static boolean isActiveOrFixedLayout(int[] values)
	{
		return values != null && values.length == HOTSPOT_VARBITS.length
			&& Arrays.stream(values).allMatch(MahoganyHomesScript::isKnownHotspotValue);
	}

	static boolean isLoadedContractLayout(int[] values)
	{
		return isActiveOrFixedLayout(values) && Arrays.stream(values).anyMatch(value -> value != 0);
	}

	static boolean isSupportedLayout(int[] values)
	{
		if (values == null || values.length != HOTSPOT_VARBITS.length || values[7] != 0)
		{
			return false;
		}
		return Arrays.stream(values).allMatch(MahoganyHomesScript::isKnownHotspotValue);
	}

	static int requiredPlanks(int[] values)
	{
		return MahoganyHomesContractData.LEELA.requiredPlanks(values);
	}

	static int requiredSteelBars(int[] values)
	{
		return MahoganyHomesContractData.LEELA.requiredSteelBars(values);
	}

	static Setup selectSetup(MahoganyHomesData configuredTier, boolean atCurrentHome, int[] values)
	{
		if (atCurrentHome && isSupportedCurrentContract(values))
		{
			return new Setup(configuredTier, requiredPlanks(values), requiredSteelBars(values), true);
		}
		return new Setup(configuredTier, configuredTier.getSetupPlanks(),
			configuredTier.getSetupSteelBars(), false);
	}

	int getLoosePlanks()
	{
		return Rs2Inventory.count(supplyTier().getPlankId());
	}

	int getStoredPlanks()
	{
		return PlankSack.count(supplyTier());
	}

	int getSteelBars()
	{
		return Rs2Inventory.count(ItemID.STEEL_BAR);
	}

	private MahoganyHomesData supplyTier()
	{
		return assignedTier != null ? assignedTier : setupTier != null ? setupTier : config.contractTier();
	}

	static boolean allFixed(int[] values)
	{
		return values != null && Arrays.stream(values).noneMatch(MahoganyHomesScript::isActionable);
	}

	private boolean allFixed()
	{
		return allFixed(hotspotValues);
	}

	static Work nextWork(int[] values, int plane)
	{
		return nextWork(MahoganyHomesContractData.LEELA.work, values, plane);
	}

	static Work nextWork(List<Work> workItems, int[] values, int plane)
	{
		return workItems.stream()
			.filter(work -> work.location.getPlane() == plane && isActionable(values[work.hotspot]))
			.findFirst()
			.orElse(null);
	}

	static Work nextWork(List<Work> workItems, int[] values, WorldPoint position)
	{
		return workItems.stream()
			.filter(work -> work.location.getPlane() == position.getPlane()
				&& isActionable(values[work.hotspot]))
			.min(Comparator.comparingInt(work -> position.distanceTo2D(work.location)))
			.orElse(null);
	}

	static Work nextWork(List<Work> workItems, int[] values, WorldPoint position,
		Set<WorldPoint> reachable)
	{
		Work nearestReachable = workItems.stream()
			.filter(work -> work.location.getPlane() == position.getPlane()
				&& isActionable(values[work.hotspot])
				&& (work.access == null || reachable.contains(work.access)))
			.min(Comparator.comparingInt(work -> position.distanceTo2D(work.location)))
			.orElse(null);
		return nearestReachable == null ? nextWork(workItems, values, position) : nearestReachable;
	}

	static boolean needsWorkAccess(WorldPoint position, Work work, int attempts,
		boolean accessReachable)
	{
		return work.access != null && !position.equals(work.access)
			&& (attempts > 0 || !accessReachable);
	}

	static String actionFor(int value)
	{
		switch (value)
		{
			case 1:
				return "Repair";
			case 3:
				return "Remove";
			case 4:
				return "Build";
			default:
				return null;
		}
	}

	private static boolean isActionable(int value)
	{
		return value == 1 || value == 3 || value == 4;
	}

	private static boolean isKnownHotspotValue(int value)
	{
		return value >= 0 && value <= 8;
	}

	private synchronized void fail(String message)
	{
		if (error != null) return;
		error = message == null || message.isBlank() ? "The plugin stopped without a detailed reason." : message;
		log.warn("mahogany_error | reason={} at={} homeowner={} values={}", error,
			statePosition, assignedHomeowner, Arrays.toString(hotspotValues));
		String notice = "Error: Mahogany Homes: " + net.runelite.client.util.Text.removeTags(error);
		Microbot.status = notice;
		var clientThread = Microbot.getClientThread();
		if (clientThread != null && Microbot.getClient() != null)
		{
			clientThread.invoke(() -> {
				net.runelite.api.Client client = Microbot.getClient();
				if (client != null) client.addChatMessage(net.runelite.api.ChatMessageType.GAMEMESSAGE, "",
					net.runelite.client.util.ColorUtil.wrapWithColorTag(notice, java.awt.Color.RED), null);
			});
		}
		if (walker != null)
		{
			walker.cancel();
		}
	}

	String getStateName()
	{
		return getSnapshot() == null ? "STARTING" : getSnapshot().currentState().name();
	}

	String getError()
	{
		return error;
	}

	int getCompletedEffects()
	{
		return completedEffects;
	}

	int[] getHotspotValues()
	{
		return hotspotValues.clone();
	}

	int getCurrentRequiredPlanks()
	{
		MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
		return contract == null ? requiredPlanks(hotspotValues) : contract.requiredPlanks(hotspotValues);
	}

	int getCurrentRequiredSteelBars()
	{
		MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
		return contract == null ? requiredSteelBars(hotspotValues) : contract.requiredSteelBars(hotspotValues);
	}

	boolean isComplete()
	{
		return complete;
	}

	String getAssignedHomeowner()
	{
		return assignedHomeowner;
	}

	MahoganyHomesData getAssignedTier()
	{
		return assignedTier;
	}

	boolean isContractArrived()
	{
		return contractArrived;
	}

	boolean isContractValidated()
	{
		return contractValidated;
	}

	WorldPoint getContractDestination()
	{
		MahoganyHomesContractData contract = MahoganyHomesContractData.forHomeowner(assignedHomeowner);
		return contract == null ? null : contract.destination;
	}

	static final class Assignment
	{
		final String homeowner;
		final MahoganyHomesData tier;

		private Assignment(String homeowner, MahoganyHomesData tier)
		{
			this.homeowner = homeowner;
			this.tier = tier;
		}
	}

	static final class Work
	{
		final int hotspot;
		final int objectId;
		final WorldPoint location;
		final WorldPoint access;

		Work(int hotspot, int objectId, WorldPoint location)
		{
			this(hotspot, objectId, location, null);
		}

		Work(int hotspot, int objectId, WorldPoint location, WorldPoint access)
		{
			this.hotspot = hotspot;
			this.objectId = objectId;
			this.location = location;
			this.access = access;
		}
	}

	static final class Setup
	{
		final MahoganyHomesData tier;
		final int planks;
		final int steelBars;
		final boolean currentContract;

		private Setup(MahoganyHomesData tier, int planks, int steelBars, boolean currentContract)
		{
			this.tier = tier;
			this.planks = planks;
			this.steelBars = steelBars;
			this.currentContract = currentContract;
		}
	}

	private static final class WalkerBridge
	{
		private volatile WorldPoint requestedTarget;
        private int requestedArrivalDistance;
        private final java.util.concurrent.atomic.AtomicReference<WorldPoint> ownedActiveTarget =
            new java.util.concurrent.atomic.AtomicReference<>();
		private volatile String currentStatus = "IDLE";
        private BooleanSupplier completionCondition = () -> false;

		static WalkerBridge bind()
		{
			return new WalkerBridge();
		}

		boolean isAvailable()
		{
			return true;
		}

		boolean walkTo(WorldPoint point, int arrivalDistance)
		{
			return startRoute(point, arrivalDistance);
		}

		boolean walkToNearestBank()
		{
			BankLocation bank = Rs2Bank.getNearestBank();
			return bank != null && startRoute(bank.getWorldPoint(), 4);
		}

		boolean prepareTeleportResourcesAtBank()
		{
			// Rs2Walker's banked-transport route planner selects and obtains required
			// transport items as part of the route instead of preloading a separate catalog.
			return isAvailable();
		}

		private boolean startRoute(WorldPoint target, int arrivalDistance)
		{
			if (!isAvailable() || target == null)
			{
				return false;
			}
			WorldPoint activeTarget = Rs2Walker.getCurrentTarget();
			if (activeTarget != null && !activeTarget.equals(requestedTarget))
			{
				return false;
			}
			requestedTarget = target;
            requestedArrivalDistance = arrivalDistance;
            ownedActiveTarget.set(activeTarget);
			currentStatus = "MOVING";
			return advanceRoute();
		}

		private boolean advanceRoute()
		{
			WorldPoint target = requestedTarget;
			if (!isAvailable() || target == null) return false;
            WalkerState state = Rs2Walker.walkWithBankedTransportsUntil(target, requestedArrivalDistance,
                ownedActiveTarget, completionCondition);
			currentStatus = state == WalkerState.ARRIVED ? "ARRIVED"
				: state == WalkerState.MOVING ? "MOVING" : "BLOCKED";
			return state != WalkerState.EXIT && state != WalkerState.UNREACHABLE;
		}

		void cancel()
		{
			WorldPoint activeTarget = Rs2Walker.getCurrentTarget();
			if (ownedActiveTarget.get() != null && ownedActiveTarget.get().equals(activeTarget))
				Rs2Walker.clearWalkingRoute("mahogany-homes:cancel-owned-route");
			requestedTarget = null;
            ownedActiveTarget.set(null);
			currentStatus = "CANCELLED";
		}

		String status()
		{
			if (currentStatus.equals("MOVING")) advanceRoute();
			return currentStatus;
		}

	}
}
