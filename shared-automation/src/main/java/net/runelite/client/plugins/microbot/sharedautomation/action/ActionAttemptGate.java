package net.runelite.client.plugins.microbot.sharedautomation.action;

/**
 * Tracks one pending action, its confirmation deadline, and a bounded retry budget.
 * The caller decides what counts as evidence and performs all game interactions.
 */
public final class ActionAttemptGate
{
	public enum State
	{
		IDLE,
		WAITING,
		RETRY_DUE,
		CONFIRMED,
		EXHAUSTED
	}

	private State state = State.IDLE;
	private Ticket currentTicket;
	private int attemptCount;
	private int maximumAttempts;
	private long timeoutMillis;
	private long deadlineMillis;
	private long lastObservedMillis;

	/** Starts tracking after the caller has issued the first attempt. */
	public Ticket begin(String key, long nowMillis, long timeoutMillis, int maximumAttempts)
	{
		if (key == null || key.trim().isEmpty() || timeoutMillis <= 0 || maximumAttempts < 1)
		{
			throw new IllegalArgumentException("key, positive timeout, and at least one attempt are required");
		}
		if (state == State.WAITING || state == State.RETRY_DUE)
		{
			return null;
		}

		this.currentTicket = new Ticket(key);
		this.attemptCount = 1;
		this.maximumAttempts = maximumAttempts;
		this.timeoutMillis = timeoutMillis;
		this.deadlineMillis = addSaturated(nowMillis, timeoutMillis);
		this.lastObservedMillis = nowMillis;
		this.state = State.WAITING;
		return currentTicket;
	}

	public State poll(Ticket ticket, long nowMillis)
	{
		if (!isCurrent(ticket))
		{
			return State.IDLE;
		}
		ensureMonotonic(nowMillis);
		lastObservedMillis = nowMillis;
		if (state == State.WAITING && nowMillis >= deadlineMillis)
		{
			state = attemptCount < maximumAttempts ? State.RETRY_DUE : State.EXHAUSTED;
		}
		return state;
	}

	/** Records that the caller issued a retry after poll returned RETRY_DUE. */
	public boolean recordRetry(Ticket ticket, long nowMillis)
	{
		if (poll(ticket, nowMillis) != State.RETRY_DUE)
		{
			return false;
		}
		attemptCount++;
		deadlineMillis = addSaturated(nowMillis, timeoutMillis);
		state = State.WAITING;
		return true;
	}

	/** Confirms the pending action only when the supplied evidence belongs to its key. */
	public boolean confirm(Ticket ticket)
	{
		if (!isCurrent(ticket) || state == State.IDLE || state == State.CONFIRMED)
		{
			return false;
		}
		state = State.CONFIRMED;
		return true;
	}

	public void clear()
	{
		state = State.IDLE;
		currentTicket = null;
		attemptCount = 0;
		maximumAttempts = 0;
		timeoutMillis = 0;
		deadlineMillis = 0;
		lastObservedMillis = 0;
	}

	public State getState()
	{
		return state;
	}

	public int getAttemptCount()
	{
		return attemptCount;
	}

	public int getMaximumAttempts()
	{
		return maximumAttempts;
	}

	public long getDeadlineMillis()
	{
		return deadlineMillis;
	}

	private void ensureMonotonic(long nowMillis)
	{
		if (nowMillis < lastObservedMillis)
		{
			throw new IllegalArgumentException("nowMillis must be monotonic");
		}
	}

	private boolean isCurrent(Ticket ticket)
	{
		return ticket != null && ticket == currentTicket;
	}

	private static long addSaturated(long value, long amount)
	{
		if (amount > 0 && value > Long.MAX_VALUE - amount)
		{
			return Long.MAX_VALUE;
		}
		return value + amount;
	}

	/** Identity token that prevents late evidence from confirming a newer action. */
	public static final class Ticket
	{
		private final String actionKey;

		private Ticket(String actionKey)
		{
			this.actionKey = actionKey;
		}

		public String getActionKey()
		{
			return actionKey;
		}
	}
}
